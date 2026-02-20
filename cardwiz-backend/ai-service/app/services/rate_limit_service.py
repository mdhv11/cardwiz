import logging
from dataclasses import dataclass

from app.clients.cache_client import get_cache_client
from app.config import settings

logger = logging.getLogger("uvicorn")


@dataclass
class RateLimitDecision:
    allowed: bool
    limit: int
    remaining: int
    retry_after_seconds: int


def check_rate_limit(namespace: str, actor_key: str, limit: int, window_seconds: int) -> RateLimitDecision:
    if not settings.AI_RATE_LIMIT_ENABLED:
        return RateLimitDecision(True, limit, limit, 0)
    if limit <= 0:
        return RateLimitDecision(True, limit, limit, 0)

    client = get_cache_client()
    if client is None:
        # Fail open if Redis is unavailable so internal flows keep working.
        return RateLimitDecision(True, limit, limit, 0)

    key = f"rate:ai-service:{namespace}:{actor_key}"
    try:
        count = client.incr(key)
        if count == 1:
            client.expire(key, window_seconds)
        ttl = client.ttl(key)
        retry_after = ttl if isinstance(ttl, int) and ttl > 0 else window_seconds
        remaining = max(0, limit - int(count))
        allowed = int(count) <= limit
        return RateLimitDecision(allowed, limit, remaining, retry_after)
    except Exception as exc:
        logger.warning("Rate limit check failed for key=%s: %s", key, exc)
        return RateLimitDecision(True, limit, limit, 0)
