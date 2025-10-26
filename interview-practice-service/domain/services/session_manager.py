"""Session metadata management using Redis."""
import json
import uuid
from datetime import datetime, timedelta
from typing import Optional, Any
from infrastructure.redis_client import get_redis_client


def convert_uuids_to_strings(obj: Any) -> Any:
    """
    Recursively convert UUID objects to strings for JSON serialization.

    Args:
        obj: Object to convert (dict, list, or primitive)

    Returns:
        Object with UUIDs converted to strings
    """
    if hasattr(obj, 'hex'):  # UUID object
        return str(obj)
    elif isinstance(obj, dict):
        return {k: convert_uuids_to_strings(v) for k, v in obj.items()}
    elif isinstance(obj, (list, tuple)):
        return [convert_uuids_to_strings(item) for item in obj]
    else:
        return obj


class SessionManager:
    """Manages interview session metadata in Redis."""

    def __init__(self):
        self.redis_client = get_redis_client()

    def create_session(
        self,
        user_id: int,
        resume_id: int,
        job_listing_id: Optional[int],
        interview_type: str,
        difficulty_level: str
    ) -> str:
        """
        Create new interview session.

        Args:
            user_id: User ID
            resume_id: Resume ID
            job_listing_id: Optional job listing ID
            interview_type: TECHNICAL | BEHAVIORAL | LEADERSHIP
            difficulty_level: JUNIOR | MID | SENIOR | STAFF

        Returns:
            Session ID (UUID)
        """
        session_id = str(uuid.uuid4())

        # Convert UUIDs to strings for JSON serialization
        metadata = {
            "user_id": str(user_id) if hasattr(user_id, 'hex') else user_id,
            "resume_id": str(resume_id) if hasattr(resume_id, 'hex') else resume_id,
            "job_listing_id": str(job_listing_id) if job_listing_id and hasattr(job_listing_id, 'hex') else job_listing_id,
            "interview_type": interview_type,
            "difficulty_level": difficulty_level,
            "status": "REGISTERED",
            "created_at": datetime.utcnow().isoformat(),
            "expires_at": (datetime.utcnow() + timedelta(hours=24)).isoformat()
        }

        # Store with 24h TTL
        self.redis_client.setex(
            f"session:{session_id}:meta",
            timedelta(hours=24),
            json.dumps(metadata)
        )

        return session_id

    def get_session_metadata(self, session_id: str) -> Optional[dict]:
        """
        Retrieve session metadata.

        Args:
            session_id: Session ID

        Returns:
            Session metadata dict or None if not found
        """
        data = self.redis_client.get(f"session:{session_id}:meta")
        return json.loads(data) if data else None

    def update_session_status(self, session_id: str, status: str):
        """
        Update session status.

        Args:
            session_id: Session ID
            status: REGISTERED | ACTIVE | COMPLETED | ABANDONED
        """
        metadata = self.get_session_metadata(session_id)
        if metadata:
            metadata["status"] = status
            if status == "ACTIVE":
                metadata["started_at"] = datetime.utcnow().isoformat()
            elif status == "COMPLETED":
                metadata["completed_at"] = datetime.utcnow().isoformat()

            self.redis_client.setex(
                f"session:{session_id}:meta",
                timedelta(hours=24),
                json.dumps(metadata)
            )

    def store_context(self, session_id: str, resume_data: dict, job_data: Optional[dict]):
        """
        Store resume and job context for quick access.

        Args:
            session_id: Session ID
            resume_data: Resume data from Resume API
            job_data: Job data from Job Search API (optional)
        """
        context = {
            "resume": resume_data,
            "job": job_data
        }

        # Convert any UUIDs to strings for JSON serialization
        context = convert_uuids_to_strings(context)

        self.redis_client.setex(
            f"session:{session_id}:context",
            timedelta(hours=24),
            json.dumps(context)
        )

    def get_context(self, session_id: str) -> Optional[dict]:
        """
        Retrieve session context.

        Args:
            session_id: Session ID

        Returns:
            Context dict with resume and job data
        """
        data = self.redis_client.get(f"session:{session_id}:context")
        return json.loads(data) if data else None

    def store_audio_url(self, session_id: str, audio_type: str, url: str):
        """
        Store audio URL.

        Args:
            session_id: Session ID
            audio_type: greeting | question_{round} | feedback
            url: Audio file URL or path
        """
        self.redis_client.setex(
            f"session:{session_id}:audio:{audio_type}",
            timedelta(hours=24),
            url
        )

    def get_audio_url(self, session_id: str, audio_type: str) -> Optional[str]:
        """
        Retrieve audio URL.

        Args:
            session_id: Session ID
            audio_type: greeting | question_{round} | feedback

        Returns:
            Audio URL or None
        """
        return self.redis_client.get(f"session:{session_id}:audio:{audio_type}")
