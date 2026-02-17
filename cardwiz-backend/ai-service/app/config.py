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
    BEDROCK_CONNECT_TIMEOUT_SECONDS: int = 10
    BEDROCK_READ_TIMEOUT_SECONDS: int = 3600

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
    EMBEDDING_INDEX_PURPOSE: str = "GENERIC_INDEX"
    EMBEDDING_RETRIEVAL_PURPOSE: str = "GENERIC_RETRIEVAL"
    EMBEDDING_TEXT_TRUNCATION: str = "NONE"

    # Recommendation reasoning model config
    RECOMMENDATION_PRIMARY_MODEL_ID: str = "amazon.nova-lite-v1:0"
    RECOMMENDATION_FALLBACK_MODEL_ID: Optional[str] = "amazon.nova-micro-v1:0"

    # Kafka async ingestion config
    KAFKA_ENABLED: bool = False
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    KAFKA_DOCUMENT_INGEST_TOPIC: str = "cardwiz.document.ingest"
    KAFKA_CONSUMER_GROUP: str = "ai-service-doc-ingest"
    KAFKA_AUTO_OFFSET_RESET: str = "latest"
    KAFKA_MAX_POLL_INTERVAL_MS: int = 900000
    KAFKA_SESSION_TIMEOUT_MS: int = 45000
    KAFKA_HEARTBEAT_INTERVAL_MS: int = 15000

    # Callback endpoint for ingestion completion
    USER_SERVICE_CALLBACK_URL: str = "http://localhost:8080/api/v1/cards/internal/ingestion-callback"
    USER_SERVICE_CALLBACK_SECRET: str = "cardwiz-internal-secret"

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
