"""
Redis client for session state storage (24h TTL)
Adapted from code-raider for chaos-monkey
Uses Redis DB 1 to avoid conflict with code-raider (DB 0)
"""
import os
import json
import redis.asyncio as redis
from typing import Optional, Dict, Any
from dotenv import load_dotenv

load_dotenv()

REDIS_URI = os.getenv("REDIS_URI", "redis://localhost:6379/1")  # DB 1


class RedisClient:
    def __init__(self):
        self.client = redis.from_url(REDIS_URI, decode_responses=True)

    async def set_session(self, session_id: str, data: Dict[str, Any], ttl: int = 86400):
        """
        Store session data with 24h TTL
        """
        await self.client.setex(
            f"chaos_session:{session_id}",  # Prefix for clarity
            ttl,
            json.dumps(data, default=str)  # Handle UUID/datetime
        )

    async def get_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        """
        Retrieve session data
        """
        data = await self.client.get(f"chaos_session:{session_id}")
        return json.loads(data) if data else None

    async def update_session(self, session_id: str, updates: Dict[str, Any]):
        """
        Update specific fields in session
        """
        session = await self.get_session(session_id)
        if session:
            session.update(updates)
            await self.set_session(session_id, session)

    async def delete_session(self, session_id: str):
        """
        Delete session (when persisted to PostgreSQL)
        """
        await self.client.delete(f"chaos_session:{session_id}")

    async def add_round(self, session_id: str, round_data: Dict[str, Any]):
        """
        Add round data to session's rounds array
        """
        session = await self.get_session(session_id)
        if session:
            if "rounds" not in session:
                session["rounds"] = []
            session["rounds"].append(round_data)
            await self.set_session(session_id, session)

    async def get_all_sessions(self) -> list:
        """
        Get all active session IDs (for cleanup/admin)
        """
        keys = await self.client.keys("chaos_session:*")
        return [key.replace("chaos_session:", "") for key in keys]

    async def close(self):
        """
        Close Redis connection
        """
        await self.client.close()


# Singleton instance
redis_client = RedisClient()
