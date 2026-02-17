import logging

from redis import Redis

from app.config import settings

logger = logging.getLogger("uvicorn")

_cache_client = None
_cache_init_attempted = False


def get_cache_client():
    global _cache_client, _cache_init_attempted
    if not settings.REDIS_ENABLED:
        return None
    if _cache_init_attempted:
        return _cache_client

    _cache_init_attempted = True
    try:
        client = Redis.from_url(settings.REDIS_URL, decode_responses=True)
        client.ping()
        _cache_client = client
        logger.info("Redis cache connected for ai-service.")
    except Exception as exc:
        logger.warning("Redis cache unavailable. Continuing without cache: %s", exc)
        _cache_client = None
    return _cache_client
