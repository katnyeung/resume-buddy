"""Redis client for session metadata (LangGraph removed)."""
import os
import redis


REDIS_URI = os.getenv("REDIS_URI", "redis://localhost:6379")


def get_redis_client() -> redis.Redis:
    """Get raw Redis client for session metadata."""
    return redis.from_url(REDIS_URI, decode_responses=True)
