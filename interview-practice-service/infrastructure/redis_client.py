"""Redis client for LangGraph checkpointing and session metadata."""
import os
import redis
from langgraph.checkpoint.redis import RedisSaver
from langgraph.checkpoint.redis.aio import AsyncRedisSaver


REDIS_URI = os.getenv("REDIS_URI", "redis://localhost:6379")


def get_redis_checkpointer() -> RedisSaver:
    """Get synchronous Redis checkpointer for LangGraph."""
    return RedisSaver.from_conn_string(REDIS_URI)


def get_async_redis_checkpointer() -> AsyncRedisSaver:
    """Get async Redis checkpointer for LangGraph (recommended for FastAPI)."""
    return AsyncRedisSaver.from_conn_string(REDIS_URI)


def get_redis_client() -> redis.Redis:
    """Get raw Redis client for session metadata."""
    return redis.from_url(REDIS_URI, decode_responses=True)
