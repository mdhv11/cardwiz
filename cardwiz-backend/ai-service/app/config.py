from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    # Legacy/shared AWS defaults
    AWS_ACCESS_KEY_ID: Optional[str] = None
    AWS_SECRET_ACCESS_KEY: Optional[str] = None
    AWS_REGION: str = "us-east-1"

    # S3 Config (supports both AI-service and shared repo env naming)
    S3_BUCKET_NAME: str = "cardwiz-docs"
    AWS_S3_BUCKET_NAME: Optional[str] = None
    AWS_S3_REGION: Optional[str] = None
    AWS_S3_ACCESS_KEY: Optional[str] = None
    AWS_S3_SECRET_KEY: Optional[str] = None
    AWS_S3_ENDPOINT: Optional[str] = None

    # Bedrock Config (optional overrides for region/credentials)
    AWS_BEDROCK_REGION: Optional[str] = None
    AWS_BEDROCK_ACCESS_KEY_ID: Optional[str] = None
    AWS_BEDROCK_SECRET_ACCESS_KEY: Optional[str] = None

    # Eureka Config
    EUREKA_ENABLED: bool = False
    EUREKA_SERVER: str = "http://localhost:8761/eureka"
    EUREKA_INSTANCE_HOST: Optional[str] = None
    APP_NAME: str = "ai-service"
    INSTANCE_PORT: int = 8000

    # Nova Config
    NOVA_ENABLE_REASONING: bool = False
    NOVA_REASONING_BUDGET_TOKENS: int = 1024

    # Database Config
    # Default to localhost, but allow override via env var
    DATABASE_URL: str = "postgresql+psycopg://postgres:postgres@localhost:5432/cardwiz"
    VECTOR_TOP_K: int = 5

    # Redis Cache Config
    REDIS_ENABLED: bool = True
    REDIS_URL: str = "redis://localhost:6379/0"
    CACHE_TTL_SECONDS: int = 600

    # Embedding model config
    EMBEDDING_PRIMARY_MODEL_ID: str = "amazon.nova-2-multimodal-embeddings-v1:0"
    EMBEDDING_FALLBACK_MODEL_ID: str = "amazon.titan-embed-text-v2:0"
    EMBEDDING_OUTPUT_LENGTH: int = 1024

    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        env_ignore_empty=True,
        extra="ignore"
    )

    @property
    def effective_s3_bucket(self) -> str:
        return self.AWS_S3_BUCKET_NAME or self.S3_BUCKET_NAME

    @property
    def effective_s3_region(self) -> str:
        return self.AWS_S3_REGION or self.AWS_REGION

    @property
    def effective_s3_access_key(self) -> Optional[str]:
        return self.AWS_S3_ACCESS_KEY or self.AWS_ACCESS_KEY_ID

    @property
    def effective_s3_secret_key(self) -> Optional[str]:
        return self.AWS_S3_SECRET_KEY or self.AWS_SECRET_ACCESS_KEY

    @property
    def effective_bedrock_region(self) -> str:
        return self.AWS_BEDROCK_REGION or self.AWS_REGION

    @property
    def effective_bedrock_access_key(self) -> Optional[str]:
        return self.AWS_BEDROCK_ACCESS_KEY_ID or self.AWS_ACCESS_KEY_ID

    @property
    def effective_bedrock_secret_key(self) -> Optional[str]:
        return self.AWS_BEDROCK_SECRET_ACCESS_KEY or self.AWS_SECRET_ACCESS_KEY

settings = Settings()
