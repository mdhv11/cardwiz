import boto3

from app.config import settings


def get_bedrock_runtime_client():
    return boto3.client("bedrock-runtime", region_name=settings.AWS_REGION)
