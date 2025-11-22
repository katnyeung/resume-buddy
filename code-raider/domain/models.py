"""
SQLAlchemy models for Code Raider
"""
from sqlalchemy import Column, String, Integer, Boolean, Text, DateTime, ForeignKey
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.sql import func
import uuid

from infrastructure.database import Base


class Problem(Base):
    __tablename__ = "problems"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    title = Column(Text, nullable=False)
    description = Column(Text, nullable=False)
    algorithm_type = Column(Text, nullable=False)
    difficulty = Column(Text, nullable=False, default="MEDIUM")
    test_cases = Column(JSONB, nullable=False)
    story_seed = Column(Text, nullable=False)
    function_signature = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    created_by = Column(UUID(as_uuid=True))


class GameSession(Base):
    __tablename__ = "game_sessions"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(UUID(as_uuid=True), nullable=False)
    problem_id = Column(UUID(as_uuid=True), ForeignKey("problems.id", ondelete="CASCADE"))
    session_type = Column(Text, default="DUEL")
    current_round = Column(Integer, default=1)
    max_rounds = Column(Integer, default=5)
    is_completed = Column(Boolean, default=False)
    final_score = Column(Integer)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    completed_at = Column(DateTime(timezone=True))


class SessionRound(Base):
    __tablename__ = "session_rounds"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    session_id = Column(UUID(as_uuid=True), ForeignKey("game_sessions.id", ondelete="CASCADE"))
    round_number = Column(Integer, nullable=False)
    user_input_text = Column(Text)
    user_code = Column(Text)
    ai_code = Column(Text)
    combined_code = Column(Text)
    execution_result = Column(JSONB)
    story_text = Column(Text)
    ai_message = Column(Text)
    ascii_animation = Column(Text)
    emotion = Column(Text)
    tts_audio_url = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
