"""
Session API routes for chaos game sessions
Create and manage game sessions
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional
import uuid
import json

from infrastructure.database import db
from infrastructure.clients.redis_client import redis_client

router = APIRouter(prefix="/api/sessions")


# Pydantic models
class StartSessionRequest(BaseModel):
    scenario_id: str
    user_id: Optional[str] = None  # For future auth integration


class SessionResponse(BaseModel):
    session_id: str
    scenario_id: str
    current_round: int
    max_rounds: int
    is_completed: bool


@router.post("/start", response_model=SessionResponse)
async def start_session(request: StartSessionRequest):
    """
    Create new game session

    Flow:
    1. Load scenario from PostgreSQL
    2. Create session in Redis (active gameplay)
    3. Return session_id for WebSocket connection

    Example:
    POST /api/sessions/start
    {
      "scenario_id": "uuid-here",
      "user_id": "optional-user-id"
    }

    Returns:
    {
      "session_id": "new-uuid",
      "scenario_id": "uuid-here",
      "current_round": 1,
      "max_rounds": 5,
      "is_completed": false
    }
    """
    try:
        # Load scenario from database
        scenario = await db.fetch_one(
            """
            SELECT id, story_title, story_theme, story_intro, story_stakes,
                   target_lesson, anti_pattern, difficulty, initial_architecture
            FROM chaos_scenarios
            WHERE id = $1
            """,
            uuid.UUID(request.scenario_id)
        )

        if not scenario:
            raise HTTPException(status_code=404, detail="Scenario not found")

        # Parse JSONB fields if they're strings (PostgreSQL returns JSONB as string)
        initial_architecture = scenario["initial_architecture"]
        if isinstance(initial_architecture, str):
            initial_architecture = json.loads(initial_architecture)

        # Create session ID
        session_id = str(uuid.uuid4())

        # Prepare session data for Redis
        session_data = {
            "id": session_id,
            "user_id": request.user_id or "anonymous",
            "scenario_id": request.scenario_id,
            "current_round": 1,
            "max_rounds": 5,
            "is_completed": False,

            # Include full scenario data for gameplay
            "scenario": {
                "id": str(scenario["id"]),
                "story_title": scenario["story_title"],
                "story_theme": scenario["story_theme"],
                "story_intro": scenario["story_intro"],
                "story_stakes": scenario["story_stakes"],
                "target_lesson": scenario["target_lesson"],
                "anti_pattern": scenario["anti_pattern"],
                "difficulty": scenario["difficulty"],
                "initial_architecture": initial_architecture  # Already parsed
                # attack_options removed - dynamically generated each round by LLM
            },

            # Game state
            "system_state": initial_architecture,  # Current architecture (will evolve, already parsed)
            "rounds": []  # Will store round history
        }

        # Store in Redis with 24h TTL
        await redis_client.set_session(session_id, session_data)

        return {
            "session_id": session_id,
            "scenario_id": request.scenario_id,
            "current_round": 1,
            "max_rounds": 5,
            "is_completed": False
        }

    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid scenario ID format")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error creating session: {str(e)}")


@router.get("/{session_id}/status", response_model=SessionResponse)
async def get_session_status(session_id: str):
    """
    Get session status

    Used for:
    - Checking if session exists
    - Getting current round number
    - Checking if game is complete

    Example:
    GET /api/sessions/{session_id}/status

    Returns:
    {
      "session_id": "uuid",
      "scenario_id": "uuid",
      "current_round": 3,
      "max_rounds": 5,
      "is_completed": false
    }
    """
    try:
        session = await redis_client.get_session(session_id)

        if not session:
            raise HTTPException(status_code=404, detail="Session not found or expired")

        return {
            "session_id": session_id,
            "scenario_id": session["scenario_id"],
            "current_round": session["current_round"],
            "max_rounds": session["max_rounds"],
            "is_completed": session["is_completed"]
        }

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error getting session: {str(e)}")


@router.delete("/{session_id}")
async def delete_session(session_id: str):
    """
    Delete session from Redis

    Called when:
    - User abandons game
    - Session completes (after saving to PostgreSQL)
    """
    try:
        await redis_client.delete_session(session_id)
        return {"status": "deleted", "session_id": session_id}

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error deleting session: {str(e)}")
