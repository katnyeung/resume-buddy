"""SQLAlchemy ORM models for interview practice service."""
from sqlalchemy import Column, Integer, String, Date, Time, TIMESTAMP, DECIMAL, ARRAY, Text, ForeignKey
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
import uuid

from infrastructure.database import Base


class InterviewSession(Base):
    """Interview practice session model."""

    __tablename__ = "interview_session"

    # Primary Key
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # Foreign Keys (UUIDs to match Resume Buddy system)
    user_id = Column(UUID(as_uuid=True), nullable=False, index=True)
    resume_id = Column(UUID(as_uuid=True), nullable=False)
    job_listing_id = Column(UUID(as_uuid=True), nullable=True)

    # Session Configuration
    interview_type = Column(String(20), nullable=False, default="TECHNICAL")  # TECHNICAL, BEHAVIORAL, LEADERSHIP
    difficulty_level = Column(String(20), nullable=False, default="MID")  # JUNIOR, MID, SENIOR, STAFF
    interrupt_level = Column(String(20), nullable=False)
    scheduled_date = Column(Date, nullable=False)
    scheduled_time = Column(Time, nullable=False)
    timezone = Column(String(50), default="UTC")
    total_rounds = Column(Integer, default=3)
    round_interval = Column(String(20), default="DAILY")  # DAILY, EVERY_2_DAYS, WEEKLY

    # Session State
    status = Column(String(20), nullable=False, default="SCHEDULED", index=True)
    completed_rounds = Column(Integer, default=0)  # Count of completed rounds

    # Timestamps
    started_at = Column(TIMESTAMP, nullable=True)
    completed_at = Column(TIMESTAMP, nullable=True)
    created_at = Column(TIMESTAMP, server_default=func.now())
    updated_at = Column(TIMESTAMP, server_default=func.now(), onupdate=func.now())

    # Results (calculated from detail records)
    overall_score = Column(DECIMAL(5, 2), nullable=True)

    # Billing
    credits_deducted = Column(Integer, default=0)

    # Relationships
    rounds = relationship("SessionRound", back_populates="session", cascade="all, delete-orphan")

    def __repr__(self):
        return f"<InterviewSession(id={self.id}, user_id={self.user_id}, status={self.status})>"

    def to_dict(self):
        """Convert to dictionary for JSON serialization."""
        return {
            "id": str(self.id),
            "user_id": self.user_id,
            "resume_id": self.resume_id,
            "job_listing_id": self.job_listing_id,
            "interview_type": self.interview_type,
            "difficulty_level": self.difficulty_level,
            "interrupt_level": self.interrupt_level,
            "scheduled_date": self.scheduled_date.isoformat() if self.scheduled_date else None,
            "scheduled_time": self.scheduled_time.isoformat() if self.scheduled_time else None,
            "timezone": self.timezone,
            "total_rounds": self.total_rounds,
            "round_interval": self.round_interval,
            "status": self.status,
            "completed_rounds": self.completed_rounds,
            "started_at": self.started_at.isoformat() if self.started_at else None,
            "completed_at": self.completed_at.isoformat() if self.completed_at else None,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
            "overall_score": float(self.overall_score) if self.overall_score else None,
            "credits_deducted": self.credits_deducted
        }


class SessionRound(Base):
    """Interview practice session round model (detail table)."""

    __tablename__ = "interview_session_round"

    # Primary Key
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    # Foreign Key to Master
    session_id = Column(UUID(as_uuid=True), ForeignKey('interview_session.id', ondelete='CASCADE'), nullable=False)
    round_number = Column(Integer, nullable=False)

    # Scheduling (when this round should happen)
    scheduled_date = Column(Date, nullable=False)
    scheduled_time = Column(Time, nullable=True)
    email_sent_at = Column(TIMESTAMP, nullable=True)

    # Execution Status
    status = Column(String(20), nullable=False, default='PENDING')  # PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    started_at = Column(TIMESTAMP, nullable=True)
    completed_at = Column(TIMESTAMP, nullable=True)

    # Round Data (filled from Redis after completion)
    question_text = Column(Text, nullable=True)
    question_audio_url = Column(Text, nullable=True)
    answer_text = Column(Text, nullable=True)
    answer_audio_url = Column(Text, nullable=True)

    # Evaluation Results (filled from Redis after completion)
    score = Column(DECIMAL(5, 2), nullable=True)
    feedback_text = Column(Text, nullable=True)
    feedback_audio_url = Column(Text, nullable=True)
    evaluation_json = Column(JSONB, nullable=True)  # Full LLM evaluation details

    # Streaming Support (for real-time interviews)
    live_transcript = Column(Text, nullable=True)  # Full transcript from STT
    interruptions = Column(JSONB, nullable=True, default=[])  # Array of interrupt events

    # Metadata
    created_at = Column(TIMESTAMP, server_default=func.now())

    # Relationships
    session = relationship("InterviewSession", back_populates="rounds")

    def __repr__(self):
        return f"<SessionRound(id={self.id}, session_id={self.session_id}, round={self.round_number}, status={self.status})>"

    def to_dict(self):
        """Convert to dictionary for JSON serialization."""
        return {
            "id": str(self.id),
            "session_id": str(self.session_id),
            "round_number": self.round_number,
            "scheduled_date": self.scheduled_date.isoformat() if self.scheduled_date else None,
            "scheduled_time": self.scheduled_time.isoformat() if self.scheduled_time else None,
            "email_sent_at": self.email_sent_at.isoformat() if self.email_sent_at else None,
            "status": self.status,
            "started_at": self.started_at.isoformat() if self.started_at else None,
            "completed_at": self.completed_at.isoformat() if self.completed_at else None,
            "question_text": self.question_text,
            "question_audio_url": self.question_audio_url,
            "answer_text": self.answer_text,
            "answer_audio_url": self.answer_audio_url,
            "score": float(self.score) if self.score else None,
            "feedback_text": self.feedback_text,
            "feedback_audio_url": self.feedback_audio_url,
            "evaluation_json": self.evaluation_json,
            "created_at": self.created_at.isoformat() if self.created_at else None
        }
