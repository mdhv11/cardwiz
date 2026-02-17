import boto3
from functools import lru_cache
from app.config import settings

@lru_cache()
def get_bedrock_runtime_client():
    """
    Returns a cached Bedrock Runtime client.
    Using lru_cache ensures we reuse the same TCP connection
    instead of creating a new one for every request.
    """
    return boto3.client(
        service_name="bedrock-runtime",
        region_name=settings.effective_bedrock_region,
        aws_access_key_id=settings.effective_bedrock_access_key,
        aws_secret_access_key=settings.effective_bedrock_secret_key,
    )
