from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        extra="ignore",
    )

    # Eureka Config
    EUREKA_ENABLED: bool = False
    EUREKA_SERVER: str = "http://localhost:8761/eureka"
    APP_NAME: str = "ai-service"
    INSTANCE_PORT: int = 8000

    # AWS Config
    AWS_REGION: str = "ap-south-1"

    # Nova Config
    NOVA_ENABLE_REASONING: bool = False
    NOVA_REASONING_BUDGET_TOKENS: int = 1024

    # Database Config
    DATABASE_URL: str = "postgresql+psycopg://postgres:postgres@localhost:5432/cardwiz"
    VECTOR_TOP_K: int = 5

    # Redis Cache Config
    REDIS_ENABLED: bool = True
    REDIS_URL: str = "redis://localhost:6379/0"
    CACHE_TTL_SECONDS: int = 600

settings = Settings()
