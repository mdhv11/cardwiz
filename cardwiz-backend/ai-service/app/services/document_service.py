import boto3
import json
import logging
import re
from io import BytesIO
from json import JSONDecodeError

from pdf2image import convert_from_bytes

from app.clients.bedrock_client import get_bedrock_runtime_client
from app.schemas.document_schema import NovaAnalysisResponse
from app.config import settings

logger = logging.getLogger("uvicorn")


class DocumentService:
    def __init__(self):
        self.bedrock = get_bedrock_runtime_client()
        self.s3 = boto3.client("s3")
        self.model_id = "us.amazon.nova-2-pro-v1:0"

    def _extract_json_payload(self, output_text: str) -> str:
        text = output_text.strip()

        fenced_match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", text, re.IGNORECASE)
        if fenced_match:
            return fenced_match.group(1).strip()

        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end != -1 and end > start:
            return text[start:end + 1]

        return text

    def _parse_model_json(self, output_text: str) -> dict:
        return json.loads(self._extract_json_payload(output_text))

    def _build_converse_kwargs(self, message: dict) -> dict:
        kwargs = {
            "modelId": self.model_id,
            "messages": [message],
            "inferenceConfig": {"temperature": 0},
        }

        if settings.NOVA_ENABLE_REASONING:
            kwargs["additionalModelRequestFields"] = {
                "reasoningConfig": {"budgetTokens": settings.NOVA_REASONING_BUDGET_TOKENS}
            }

        return kwargs

    def _convert_pdf_first_page_to_png(self, document_bytes: bytes) -> bytes:
        pages = convert_from_bytes(document_bytes, first_page=1, last_page=1, fmt="png")
        if not pages:
            raise ValueError("PDF conversion produced no pages.")

        buffer = BytesIO()
        pages[0].save(buffer, format="PNG")
        return buffer.getvalue()

    def _get_png_bytes_for_nova(self, document_bytes: bytes, s3_key: str, content_type: str | None) -> bytes:
        key_is_pdf = s3_key.lower().endswith(".pdf")
        mime_is_pdf = content_type == "application/pdf"

        if key_is_pdf or mime_is_pdf:
            logger.info("Detected PDF input for %s. Converting first page to PNG.", s3_key)
            return self._convert_pdf_first_page_to_png(document_bytes)

        return document_bytes

    def _repair_json_via_nova(self, malformed_text: str) -> str:
        repair_prompt = f"""
You returned malformed JSON in the previous response.
Fix the JSON so it is strictly valid and return ONLY the JSON object.
Do not add markdown, explanations, or extra keys.

Malformed output:
{malformed_text}
"""
        repair_message = {"role": "user", "content": [{"text": repair_prompt}]}
        repair_response = self.bedrock.converse(**self._build_converse_kwargs(repair_message))
        return repair_response["output"]["message"]["content"][0]["text"]

    async def analyze_document(self, s3_key: str, bucket: str, doc_id: int) -> NovaAnalysisResponse:
        # 1. Fetch source bytes from S3
        s3_response = self.s3.get_object(Bucket=bucket, Key=s3_key)
        source_bytes = s3_response["Body"].read()
        content_type = s3_response.get("ContentType")

        # 2. Convert PDF first page to PNG for Nova image input
        image_bytes = self._get_png_bytes_for_nova(source_bytes, s3_key, content_type)

        # 3. Prepare the multimodal request for Nova 2 Pro
        prompt = """
        Analyze this credit card document carefully. 
        Extract all reward rules, specifically cashback percentages, point multipliers, and merchant-specific benefits.
        Return ONLY a JSON object that matches this structure:
        {
          "extractedRules": [
            {"cardName": "string", "category": "string", "rewardRate": float, "rewardType": "CASHBACK|POINTS|MILES", "conditions": "string"}
          ],
          "aiSummary": "A brief summary of the card's best value proposition."
        }
        """

        message = {
            "role": "user",
            "content": [
                {"image": {"format": "png", "source": {"bytes": image_bytes}}},
                {"text": prompt}
            ]
        }

        # 4. Call Converse API and parse response JSON with retry-on-malformed-json
        try:
            response = self.bedrock.converse(**self._build_converse_kwargs(message))
            output_text = response["output"]["message"]["content"][0]["text"]
            try:
                data = self._parse_model_json(output_text)
            except JSONDecodeError:
                logger.warning("Malformed JSON received from Nova. Requesting JSON repair retry.")
                repaired_text = self._repair_json_via_nova(output_text)
                data = self._parse_model_json(repaired_text)

            return NovaAnalysisResponse(
                documentMetadata={"docId": doc_id, "sourceS3": f"s3://{bucket}/{s3_key}"},
                **data
            )

        except Exception as e:
            logger.error(f"Nova Analysis Failed: {str(e)}")
            raise e
