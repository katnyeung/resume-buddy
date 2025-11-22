"""
Session cleanup and recovery service

Handles:
1. Abandoned sessions cleanup (Redis TTL expired or user didn't finish)
2. Session restore (if user reconnects to incomplete session)
3. Manual cleanup endpoint for testing
"""
from datetime import datetime, timedelta
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from infrastructure.clients.redis_client import redis_client
from domain.models import GameSession, SessionRound
import json


class SessionCleanupService:
    """
    Manages session lifecycle: cleanup abandoned sessions, restore incomplete sessions
    """

    async def cleanup_abandoned_sessions(self, db: AsyncSession, max_age_hours: int = 24):
        """
        Cleanup abandoned sessions (Redis expired or orphaned)

        Strategy:
        1. Check all session keys in Redis
        2. If session is older than max_age_hours, persist to DB as incomplete
        3. Delete from Redis

        This runs as a scheduled task (cron job or background worker)
        """
        print(f"🧹 Starting cleanup of abandoned sessions (max age: {max_age_hours}h)")

        # Get all session keys from Redis
        pattern = "session:*"
        cursor = 0
        cleaned_count = 0

        # Scan Redis for session keys (handles large keysets efficiently)
        while True:
            cursor, keys = await redis_client.client.scan(cursor, match=pattern, count=100)

            for key in keys:
                try:
                    session_id = key.replace("session:", "")
                    session_data = await redis_client.get_session(session_id)

                    if not session_data:
                        continue

                    # Check if session is old (created_at field)
                    created_at = datetime.fromisoformat(session_data.get("created_at", ""))
                    age_hours = (datetime.utcnow() - created_at).total_seconds() / 3600

                    if age_hours > max_age_hours:
                        # Persist to DB as incomplete
                        await self._persist_incomplete_session(db, session_id, session_data)
                        # Delete from Redis
                        await redis_client.delete_session(session_id)
                        cleaned_count += 1
                        print(f"   🗑️ Cleaned abandoned session: {session_id} (age: {age_hours:.1f}h)")

                except Exception as e:
                    print(f"   ⚠️ Error cleaning session {key}: {e}")

            # Break when cursor returns to 0 (full scan complete)
            if cursor == 0:
                break

        print(f"✅ Cleanup complete: {cleaned_count} abandoned sessions cleaned")
        return cleaned_count

    async def restore_session(self, session_id: str, db: AsyncSession):
        """
        Restore session from PostgreSQL to Redis (if user reconnects)

        Use case: User's session expired from Redis (TTL), but they want to continue

        Flow:
        1. Check if session exists in PostgreSQL
        2. Load session + rounds
        3. Reconstruct Redis session data
        4. Store back in Redis with fresh TTL
        """
        print(f"🔄 Attempting to restore session: {session_id}")

        # Check Redis first
        redis_session = await redis_client.get_session(session_id)
        if redis_session:
            print(f"   ✅ Session already in Redis, no restore needed")
            return redis_session

        # Load from PostgreSQL
        result = await db.execute(
            select(GameSession).where(GameSession.id == session_id)
        )
        game_session = result.scalar_one_or_none()

        if not game_session:
            print(f"   ❌ Session not found in PostgreSQL")
            return None

        # Don't restore completed sessions
        if game_session.is_completed:
            print(f"   ⚠️ Session already completed, cannot restore")
            return None

        # Load rounds
        result = await db.execute(
            select(SessionRound)
            .where(SessionRound.session_id == session_id)
            .order_by(SessionRound.round_number)
        )
        rounds = result.scalars().all()

        # Reconstruct Redis session data
        session_data = {
            "id": str(game_session.id),
            "user_id": str(game_session.user_id),
            "problem_id": str(game_session.problem_id),
            "current_round": game_session.current_round,
            "max_rounds": game_session.max_rounds,
            "is_completed": False,
            "created_at": game_session.created_at.isoformat(),
            "rounds": [
                {
                    "round_number": r.round_number,
                    "user_input_text": r.user_input_text,
                    "user_code": r.user_code,
                    "ai_code": r.ai_code,
                    "combined_code": r.combined_code,
                    "execution_result": r.execution_result,
                    "story_text": r.story_text,
                    "ai_message": r.ai_message,
                    "ascii_animation": r.ascii_animation,
                    "emotion": r.emotion,
                    "tts_audio_url": r.tts_audio_url
                }
                for r in rounds
            ]
        }

        # Store in Redis with fresh TTL
        await redis_client.set_session(session_id, session_data, ttl=86400)  # 24h

        print(f"   ✅ Session restored to Redis ({len(rounds)} rounds)")
        return session_data

    async def _persist_incomplete_session(self, db: AsyncSession, session_id: str, session_data: dict):
        """
        Persist incomplete session to PostgreSQL (for abandoned sessions)
        """
        try:
            # Check if already exists
            result = await db.execute(
                select(GameSession).where(GameSession.id == session_id)
            )
            existing = result.scalar_one_or_none()

            if existing:
                print(f"   ⚠️ Session {session_id} already exists in DB, skipping")
                return

            # Create GameSession record (incomplete)
            game_session = GameSession(
                id=session_id,
                user_id=session_data["user_id"],
                problem_id=session_data["problem_id"],
                current_round=session_data.get("current_round", 1),
                max_rounds=session_data.get("max_rounds", 5),
                is_completed=False,  # Mark as incomplete
                final_score=None,
                created_at=datetime.fromisoformat(session_data.get("created_at", datetime.utcnow().isoformat())),
                completed_at=None
            )
            db.add(game_session)

            # Create SessionRound records
            for round_data in session_data.get("rounds", []):
                session_round = SessionRound(
                    session_id=session_id,
                    round_number=round_data.get("round_number"),
                    user_input_text=round_data.get("user_input_text"),
                    user_code=round_data.get("user_code"),
                    ai_code=round_data.get("ai_code"),
                    combined_code=round_data.get("combined_code"),
                    execution_result=round_data.get("execution_result"),
                    story_text=round_data.get("story_text"),
                    ai_message=round_data.get("ai_message"),
                    ascii_animation=round_data.get("ascii_animation"),
                    emotion=round_data.get("emotion"),
                    tts_audio_url=round_data.get("tts_audio_url")
                )
                db.add(session_round)

            await db.commit()
            print(f"   ✅ Persisted incomplete session {session_id} to PostgreSQL")

        except Exception as e:
            await db.rollback()
            print(f"   ❌ Error persisting incomplete session {session_id}: {e}")
            raise

    async def get_session_stats(self):
        """
        Get Redis session statistics (for monitoring/debugging)
        """
        pattern = "session:*"
        cursor = 0
        session_count = 0
        total_size = 0

        while True:
            cursor, keys = await redis_client.client.scan(cursor, match=pattern, count=100)
            session_count += len(keys)

            for key in keys:
                # Get memory usage for each key (estimate)
                data = await redis_client.client.get(key)
                if data:
                    total_size += len(data)

            if cursor == 0:
                break

        return {
            "active_sessions": session_count,
            "total_size_bytes": total_size,
            "total_size_mb": round(total_size / 1024 / 1024, 2)
        }


# Singleton instance
session_cleanup_service = SessionCleanupService()
