"""Pydantic models for API requests and responses."""
from typing import Optional, List
from pydantic import BaseModel, Field
from enum import Enum
from uuid import UUID


class InterviewType(str, Enum):
    TECHNICAL = "TECHNICAL"
    BEHAVIORAL = "BEHAVIORAL"
    LEADERSHIP = "LEADERSHIP"


class DifficultyLevel(str, Enum):
    JUNIOR = "JUNIOR"
    MID = "MID"
    SENIOR = "SENIOR"
    STAFF = "STAFF"


# Enums for scheduled interview practice
class InterruptLevel(str, Enum):
    CONSERVATIVE = "CONSERVATIVE"
    MODERATE = "MODERATE"
    AGGRESSIVE = "AGGRESSIVE"


class RoundIntervalEnum(str, Enum):
    DAILY = "DAILY"
    EVERY_2_DAYS = "EVERY_2_DAYS"
    WEEKLY = "WEEKLY"


class CreateSessionRequest(BaseModel):
    user_id: UUID = Field(..., description="User ID (UUID)")
    resume_id: UUID = Field(..., description="Resume ID to analyze (UUID)")
    experience_id: Optional[UUID] = Field(None, description="Optional experience ID to focus interview on (UUID)")
    job_listing_id: Optional[UUID] = Field(None, description="Optional job listing ID for context (UUID)")
    interview_type: InterviewType = Field(InterviewType.TECHNICAL, description="Interview type")
    difficulty_level: DifficultyLevel = Field(DifficultyLevel.MID, description="Difficulty level based on seniority")
    interrupt_level: InterruptLevel = Field(InterruptLevel.MODERATE, description="How aggressively AI interrupts")
    scheduled_date: str = Field(..., description="Date in YYYY-MM-DD format")
    scheduled_time: str = Field(..., description="Time in HH:MM:SS format")
    timezone: str = Field("UTC", description="Timezone for scheduled time")
    total_rounds: int = Field(3, ge=1, le=10, description="Number of interview rounds (1-10)")
    round_interval: RoundIntervalEnum = Field(RoundIntervalEnum.DAILY, description="Interval between rounds")


class CreateSessionResponse(BaseModel):
    session_id: str
    status: str
    message: str
    scheduled_datetime: str


class SessionQuestionResponse(BaseModel):
    session_id: str
    current_round: int
    total_rounds: int
    question_text: str
    question_audio_url: str
    focus_skills: Optional[List[str]] = None


class SessionAnswerResponse(BaseModel):
    answer_transcript: str
    feedback_text: str
    feedback_audio_url: str
    score: float
    next_action: str  # NEXT_QUESTION | COMPLETE
    current_round: int
    total_rounds: int


class SessionCompleteResponse(BaseModel):
    overall_score: float
    strengths: List[str]
    improvements: List[str]
    report_url: str
