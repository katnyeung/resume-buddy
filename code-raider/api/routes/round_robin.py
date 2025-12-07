"""
Round-robin game session management
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from pydantic import BaseModel
from typing import Optional
import uuid

from infrastructure.database import get_db
from infrastructure.clients.redis_client import redis_client
from domain.models import Problem
from api.routes.auth import get_current_user

router = APIRouter(prefix="/round-robin", tags=["round-robin"])


class StartSessionRequest(BaseModel):
    problem_id: str


class SessionStatusResponse(BaseModel):
    session_id: str
    problem_id: str
    problem_title: str
    current_round: int
    max_rounds: int
    is_completed: bool
    final_score: Optional[int] = None


@router.post("/session/start")
async def start_session(
    request: StartSessionRequest,
    user_id: str = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Start new game session

    1. Load problem from DB
    2. Create session in Redis
    3. Return session_id for WebSocket connection
    """
    # Load problem
    result = await db.execute(select(Problem).where(Problem.id == request.problem_id))
    problem = result.scalar_one_or_none()

    if not problem:
        raise HTTPException(status_code=404, detail="Problem not found")

    # Create session in Redis
    session_id = str(uuid.uuid4())

    session_data = {
        "session_id": session_id,
        "user_id": user_id,
        "problem_id": str(problem.id),
        "problem": {
            "id": str(problem.id),
            "title": problem.title,
            "description": problem.description,
            "algorithm_type": problem.algorithm_type,
            "test_cases": problem.test_cases,
            "story_seed": problem.story_seed,
            "function_signature": problem.function_signature
        },
        "current_round": 1,
        "max_rounds": 5,
        "is_completed": False,
        "rounds": []
    }

    await redis_client.set_session(session_id, session_data)

    return {
        "session_id": session_id,
        "problem_title": problem.title,
        "message": "Session created. Connect via WebSocket at /collab/{session_id}"
    }


@router.get("/session/{session_id}/status", response_model=SessionStatusResponse)
async def get_session_status(
    session_id: str,
    user_id: str = Depends(get_current_user)
):
    """
    Get current session status
    """
    session = await redis_client.get_session(session_id)

    if not session:
        raise HTTPException(status_code=404, detail="Session not found")

    if session.get("user_id") != user_id:
        raise HTTPException(status_code=403, detail="Not authorized to access this session")

    return SessionStatusResponse(
        session_id=session["session_id"],
        problem_id=session["problem_id"],
        problem_title=session["problem"]["title"],
        current_round=session.get("current_round", 1),
        max_rounds=session.get("max_rounds", 5),
        is_completed=session.get("is_completed", False),
        final_score=session.get("final_score")
    )
