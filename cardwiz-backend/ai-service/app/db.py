import logging

from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker

from app.config import settings
from app.models.vector_models import Base

engine = create_engine(settings.DATABASE_URL, future=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)
logger = logging.getLogger("uvicorn")


def init_db():
    try:
        with engine.begin() as connection:
            connection.execute(text("CREATE EXTENSION IF NOT EXISTS vector"))
    except Exception as exc:
        logger.warning("Vector extension ensure failed: %s", exc)

    try:
        Base.metadata.create_all(bind=engine)
    except Exception as exc:
        logger.warning("Metadata create_all failed: %s", exc)
        return

    try:
        with engine.begin() as connection:
            connection.execute(
                text(
                    "CREATE INDEX IF NOT EXISTS reward_rule_vector_fts_idx "
                    "ON reward_rule_vectors USING gin (to_tsvector('english', coalesce(content_text, '')))"
                )
            )
    except Exception as exc:
        logger.warning("FTS index creation skipped: %s", exc)
