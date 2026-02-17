import boto3
from botocore.config import Config
from functools import lru_cache
from app.config import settings

@lru_cache()
def get_s3_client():
    """
    Creates a cached S3 client using project settings.
    """
    client_kwargs = {
        "service_name": "s3",
        "aws_access_key_id": settings.effective_s3_access_key,
        "aws_secret_access_key": settings.effective_s3_secret_key,
        "region_name": settings.effective_s3_region,
        # Only needed if using LocalStack or MinIO
        "endpoint_url": settings.AWS_S3_ENDPOINT,
    }
    if settings.AWS_S3_ENDPOINT:
        # MinIO/local S3-compatible services work more reliably with path-style URLs.
        client_kwargs["config"] = Config(s3={"addressing_style": "path"})

    return boto3.client(**client_kwargs)
