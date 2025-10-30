"""Redis client for storing conversation state during active rounds."""
import json
from typing import Optional, Dict, Any, List
from datetime import datetime
import redis.asyncio as redis


class RedisConversationStore:
    """
    Stores conversation state in Redis for active interview rounds.

    Key format: interview:{session_id}:{round_num}:state
    TTL: 24 hours (auto-cleanup if user abandons)

    Benefits:
    - Fast reads for frontend (load conversation on refresh)
    - No database writes mid-conversation (avoids locks)
    - Easy to reset on "regenerate question"
    """

    def __init__(self, redis_url: str):
        self.redis = redis.from_url(redis_url, decode_responses=True)

    def _get_key(self, session_id: str, round_num: int) -> str:
        return f"interview:{session_id}:{round_num}:state"

    async def init_round(self, session_id: str, round_num: int) -> Dict[str, Any]:
        """Initialize conversation state for a new round."""
        key = self._get_key(session_id, round_num)
        state = {
            "session_id": session_id,
            "round_number": round_num,
            "status": "in_progress",
            "current_interaction": 0,
            "interactions": [],
            "interrupts": [],
            "final_score": None,
            "created_at": datetime.utcnow().isoformat()
        }
        await self.redis.setex(key, 86400, json.dumps(state))  # 24h TTL
        print(f"[Redis] Initialized round state: {key}")
        return state

    async def get_state(self, session_id: str, round_num: int) -> Optional[Dict[str, Any]]:
        """Get conversation state from Redis."""
        key = self._get_key(session_id, round_num)
        data = await self.redis.get(key)
        if data:
            return json.loads(data)
        return None

    async def save_state(self, session_id: str, round_num: int, state: Dict[str, Any]):
        """Save conversation state to Redis."""
        key = self._get_key(session_id, round_num)
        await self.redis.setex(key, 86400, json.dumps(state))  # Reset 24h TTL

    async def add_interaction(
        self,
        session_id: str,
        round_num: int,
        interaction: Dict[str, Any]
    ):
        """Add a new interaction (question) to the conversation."""
        state = await self.get_state(session_id, round_num)
        if not state:
            state = await self.init_round(session_id, round_num)

        # Add timestamp if not present
        if "timestamp" not in interaction:
            interaction["timestamp"] = datetime.utcnow().isoformat()

        state["interactions"].append(interaction)
        state["current_interaction"] = len(state["interactions"])
        await self.save_state(session_id, round_num, state)
        print(f"[Redis] Added interaction {interaction['interaction_num']}")

    async def update_last_interaction(
        self,
        session_id: str,
        round_num: int,
        updates: Dict[str, Any]
    ):
        """Update the last interaction with answer/evaluation data."""
        state = await self.get_state(session_id, round_num)
        if not state or not state["interactions"]:
            print(f"[Redis] WARNING: No interactions to update")
            return

        state["interactions"][-1].update(updates)

        # Update timestamp
        state["interactions"][-1]["updated_at"] = datetime.utcnow().isoformat()

        await self.save_state(session_id, round_num, state)
        print(f"[Redis] Updated interaction {state['interactions'][-1]['interaction_num']}")

    async def remove_last_interaction(self, session_id: str, round_num: int):
        """Remove the last interaction (used for question regeneration)."""
        state = await self.get_state(session_id, round_num)
        if not state or not state["interactions"]:
            return

        removed = state["interactions"].pop()
        state["current_interaction"] = len(state["interactions"])
        await self.save_state(session_id, round_num, state)
        print(f"[Redis] Removed interaction {removed['interaction_num']} for regeneration")

    async def add_interrupt(
        self,
        session_id: str,
        round_num: int,
        interrupt: Dict[str, Any]
    ):
        """Add an AI interrupt event."""
        state = await self.get_state(session_id, round_num)
        if not state:
            return

        if "timestamp" not in interrupt:
            interrupt["timestamp"] = datetime.utcnow().isoformat()

        state["interrupts"].append(interrupt)

        # Increment interrupt count for current interaction
        if state["interactions"]:
            current_interaction = state["interactions"][-1]
            current_interaction["interrupt_count"] = current_interaction.get("interrupt_count", 0) + 1

        await self.save_state(session_id, round_num, state)
        print(f"[Redis] Added interrupt: {interrupt['type']}")

    async def set_final_score(self, session_id: str, round_num: int, score: float):
        """Set the final score for the round."""
        state = await self.get_state(session_id, round_num)
        if not state:
            return

        state["final_score"] = score
        state["status"] = "completed"
        state["completed_at"] = datetime.utcnow().isoformat()
        await self.save_state(session_id, round_num, state)
        print(f"[Redis] Set final score: {score}")

    async def delete_state(self, session_id: str, round_num: int):
        """Delete conversation state (optional cleanup after persisting to DB)."""
        key = self._get_key(session_id, round_num)
        await self.redis.delete(key)
        print(f"[Redis] Deleted round state: {key}")

    async def get_interaction_count(self, session_id: str, round_num: int) -> int:
        """Get current number of interactions in the round."""
        state = await self.get_state(session_id, round_num)
        if not state:
            return 0
        return len(state.get("interactions", []))


# Dependency injection for FastAPI
_redis_store: Optional[RedisConversationStore] = None

def init_redis_store(redis_url: str):
    """Initialize the Redis store (call at app startup)."""
    global _redis_store
    _redis_store = RedisConversationStore(redis_url)
    print(f"[Redis] Initialized conversation store")

def get_redis_store() -> RedisConversationStore:
    """Get the Redis store instance (for dependency injection)."""
    if _redis_store is None:
        raise RuntimeError("Redis store not initialized. Call init_redis_store() first.")
    return _redis_store
