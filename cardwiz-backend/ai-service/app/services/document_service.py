import json
import logging
import re
from io import BytesIO
from json import JSONDecodeError

from pdf2image import convert_from_bytes

from app.clients.bedrock_client import get_bedrock_runtime_client
from app.clients.s3_client import get_s3_client
from app.schemas.document_schema import NovaAnalysisResponse, StatementTransactionsResponse
from app.config import settings

logger = logging.getLogger("uvicorn")


class DocumentService:
    def __init__(self):
        self.bedrock = get_bedrock_runtime_client()
        self.s3 = get_s3_client()
        self.model_id = "amazon.nova-pro-v1:0"
        self.max_pdf_pages = 20

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

    def _convert_pdf_to_png_images(self, document_bytes: bytes) -> list[bytes]:
        pages = convert_from_bytes(document_bytes, first_page=1, last_page=self.max_pdf_pages, fmt="png")
        if not pages:
            raise ValueError("PDF conversion produced no pages.")

        images = []
        for page in pages:
            buffer = BytesIO()
            page.save(buffer, format="PNG")
            images.append(buffer.getvalue())
        return images

    def _build_image_content_blocks(
        self, document_bytes: bytes, s3_key: str, content_type: str | None
    ) -> list[dict]:
        key_is_pdf = s3_key.lower().endswith(".pdf")
        mime_is_pdf = content_type == "application/pdf"

        if key_is_pdf or mime_is_pdf:
            logger.info(
                "Detected PDF input for %s. Converting up to %s pages to PNG.",
                s3_key,
                self.max_pdf_pages,
            )
            images = self._convert_pdf_to_png_images(document_bytes)
            return [{"image": {"format": "png", "source": {"bytes": image}}} for image in images]

        return [{"image": {"format": "png", "source": {"bytes": document_bytes}}}]

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

    async def analyze_document(self, s3_key: str, bucket: str | None, doc_id: int) -> NovaAnalysisResponse:
        target_bucket = bucket or settings.effective_s3_bucket

        # 1. Fetch source bytes from S3
        s3_response = self.s3.get_object(Bucket=target_bucket, Key=s3_key)
        source_bytes = s3_response["Body"].read()
        content_type = s3_response.get("ContentType")

        # 2. Convert source bytes into one or more image content blocks for Nova input
        image_content_blocks = self._build_image_content_blocks(source_bytes, s3_key, content_type)

        # 3. Prepare the multimodal request for Nova 2 Pro
        prompt = """
        Analyze this credit card document carefully.
        Extract all reward rules, specifically cashback percentages, point multipliers, and merchant-specific benefits.
        Return ONLY a JSON object that matches this structure:
        {
          "extractedRules": [
            {
              "cardName": "string",
              "category": "string",
              "rewardRate": float,
              "rewardType": "CASHBACK|POINTS|MILES",
              "pointsPerUnit": float|null,
              "spendUnit": float|null,
              "pointValueRupees": float|null,
              "effectiveRewardPercentage": float,
              "conditions": "string"
            }
          ],
          "aiSummary": "A brief summary of the card's best value proposition."
        }

        Normalization rules:
        - For cashback percentages, effectiveRewardPercentage == cashback percentage.
        - For points rules, compute effectiveRewardPercentage using:
          pointsPerUnit * pointValueRupees / spendUnit * 100
        - Example: 20 points per 150 spend, pointValueRupees=0.25 => 3.33
        """

        message = {
            "role": "user",
            "content": image_content_blocks + [{"text": prompt}]
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
                documentMetadata={"docId": doc_id, "sourceS3": f"s3://{target_bucket}/{s3_key}"},
                **data
            )

        except Exception as e:
            logger.error(f"Nova Analysis Failed: {str(e)}")
            raise e

    async def extract_statement_transactions(
        self,
        s3_key: str,
        bucket: str | None,
        limit: int = 30,
    ) -> StatementTransactionsResponse:
        target_bucket = bucket or settings.effective_s3_bucket
        requested_limit = max(1, min(limit, 30))

        s3_response = self.s3.get_object(Bucket=target_bucket, Key=s3_key)
        source_bytes = s3_response["Body"].read()
        content_type = s3_response.get("ContentType")
        image_content_blocks = self._build_image_content_blocks(source_bytes, s3_key, content_type)

        prompt = f"""
        This is a credit-card statement.
        Extract transaction rows into JSON.

        Return ONLY this JSON object shape:
        {{
          "transactions": [
            {{
              "date": "string",
              "merchant": "string",
              "amount": float
            }}
          ]
        }}

        Extraction rules:
        - Include only purchase/spend transactions (exclude payments, fees, reversals, and refunds).
        - Amount must be positive numeric values with no currency symbol.
        - Merchant should be concise and readable.
        - Keep the order exactly as shown in the statement (most recent first if statement is in that order).
        - Return at most {requested_limit} transactions.
        """

        message = {
            "role": "user",
            "content": image_content_blocks + [{"text": prompt}],
        }

        try:
            response = self.bedrock.converse(**self._build_converse_kwargs(message))
            output_text = response["output"]["message"]["content"][0]["text"]
            try:
                data = self._parse_model_json(output_text)
            except JSONDecodeError:
                logger.warning("Malformed JSON for statement extraction. Requesting JSON repair retry.")
                repaired_text = self._repair_json_via_nova(output_text)
                data = self._parse_model_json(repaired_text)

            parsed = StatementTransactionsResponse(**data)
            filtered = [
                tx for tx in parsed.transactions
                if tx.merchant.strip() and float(tx.amount) > 0
            ]
            return StatementTransactionsResponse(transactions=filtered[:requested_limit])
        except Exception as exc:
            logger.error("Statement transaction extraction failed for %s: %s", s3_key, exc)
            raise
