"""
Admin routes for problem management
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, delete
from pydantic import BaseModel
from typing import List, Optional
import json

from infrastructure.database import get_db
from infrastructure.clients.grok_client import grok_client
from domain.models import Problem
from domain.services.session_cleanup import session_cleanup_service
from api.routes.auth import get_current_user

router = APIRouter(prefix="/admin", tags=["admin"])


class CreateProblemRequest(BaseModel):
    title: str
    description: str  # Copy/pasted from LeetCode
    algorithm_type: str  # e.g., "dynamic_programming"
    difficulty: str = "MEDIUM"
    test_cases: str  # JSON string: [{"input": [...], "expected": ...}]
    story_seed: str  # e.g., "dungeon heist"
    function_signature: str = "def solve():"


class ProblemResponse(BaseModel):
    id: str
    title: str
    description: str
    algorithm_type: str
    difficulty: str
    story_seed: str

    class Config:
        from_attributes = True


@router.post("/problems", response_model=ProblemResponse)
async def create_problem(
    request: CreateProblemRequest,
    user_id: str = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Create new problem (admin only - copy/paste from LeetCode)
    """
    try:
        # Parse test cases JSON
        test_cases = json.loads(request.test_cases)

        problem = Problem(
            title=request.title,
            description=request.description,
            algorithm_type=request.algorithm_type,
            difficulty=request.difficulty,
            test_cases=test_cases,
            story_seed=request.story_seed,
            function_signature=request.function_signature,
            created_by=user_id
        )

        db.add(problem)
        await db.commit()
        await db.refresh(problem)

        return ProblemResponse(
            id=str(problem.id),
            title=problem.title,
            description=problem.description,
            algorithm_type=problem.algorithm_type,
            difficulty=problem.difficulty,
            story_seed=problem.story_seed
        )

    except json.JSONDecodeError:
        raise HTTPException(status_code=400, detail="Invalid test_cases JSON format")
    except Exception as e:
        await db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/problems", response_model=List[ProblemResponse])
async def list_problems(
    user_id: str = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    List all problems
    """
    result = await db.execute(select(Problem).order_by(Problem.created_at.desc()))
    problems = result.scalars().all()

    return [
        ProblemResponse(
            id=str(p.id),
            title=p.title,
            description=p.description[:200] + "..." if len(p.description) > 200 else p.description,
            algorithm_type=p.algorithm_type,
            difficulty=p.difficulty,
            story_seed=p.story_seed
        )
        for p in problems
    ]


@router.delete("/problems/{problem_id}")
async def delete_problem(
    problem_id: str,
    user_id: str = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Delete problem
    """
    await db.execute(delete(Problem).where(Problem.id == problem_id))
    await db.commit()
    return {"message": "Problem deleted successfully"}


class GenerateProblemRequest(BaseModel):
    leetcode_question: str
    story_direction: Optional[str] = None


class GeneratedProblemResponse(BaseModel):
    title: str
    description: str
    algorithm_type: str
    difficulty: str
    test_cases: list
    story_variations: List[str]  # Changed from story_seed to 3 variations
    function_signature: str


@router.post("/problems/generate", response_model=GeneratedProblemResponse)
async def generate_problem_from_leetcode(
    request: GenerateProblemRequest,
    user_id: str = Depends(get_current_user)
):
    """
    Use LLM to extract problem details and generate 3 creative story variations from LeetCode text
    """
    try:
        story_hint_text = ""
        if request.story_direction:
            story_hint_text = f"\n\n**CRITICAL: User wants the story to be about: '{request.story_direction}'**\nYou MUST base ALL 3 variations around this theme. Make each variation a different take on the SAME concept."

        prompt = f"""You are a problem analyzer for a coding education game. Extract details from this LeetCode problem and create 3 DIFFERENT story themes.

LeetCode Problem:
{request.leetcode_question}{story_hint_text}

Generate a JSON response with this EXACT structure:
{{
  "title": "Problem title (extract from text)",
  "description": "Clean problem description without examples",
  "algorithm_type": "one of: dynamic_programming, greedy, graph, array, string, tree, backtracking, two_pointers",
  "difficulty": "EASY, MEDIUM, or HARD",
  "test_cases": [
    {{"input": [2,7,9,3,1], "expected": 12}},
    {{"input": [1,2,3,1], "expected": 4}}
  ],
  "story_variations": [
    "Variation 1: Story theme in 1-2 sentences, under 100 words",
    "Variation 2: DIFFERENT story theme in 1-2 sentences, under 100 words",
    "Variation 3: ANOTHER DIFFERENT story theme in 1-2 sentences, under 100 words"
  ],
  "function_signature": "def functionName(params: types) -> returnType:"
}}

STORY WRITING RULES:
1. Keep stories CONCISE - 1-2 sentences MAX, under 100 words each
2. Focus on the CORE concept, not dramatic flair
3. Set the scene quickly without over-elaboration

IF USER PROVIDED DIRECTION HINT:
- ALL 3 variations MUST be based on their hint
- Example hint: "bus schedule bus timer"
  - Variation 1: City bus terminal with departure times
  - Variation 2: Airport shuttle schedule with boarding windows
  - Variation 3: Train station timetable with arrival gaps
- Do NOT ignore the hint or add unrelated fantasy/sci-fi elements

IF NO USER HINT:
- Create 3 different themes (dungeon, space, underwater, etc.)
- Still keep each under 100 words

EXAMPLE GOOD VARIATIONS (bus schedule hint):
- "At the busy central bus terminal, you're analyzing departure schedules to find the smallest gap between any two buses, helping optimize transfer times for passengers."
- "Managing a cross-city shuttle service, you must identify the tightest window between any two vehicle departures to improve route efficiency and reduce wait times."
- "In the underground metro hub, examine the platform arrival times to calculate the minimum interval between any two trains, crucial for crowd management during rush hour."

IMPORTANT:
1. Extract ALL examples from problem text as test cases
2. Respect user's story direction if provided
3. Keep stories focused and concise (100 words max each)
4. Infer algorithm type from problem nature"""

        response_text = await grok_client.chat(
            prompt=prompt,
            response_format={"type": "json_object"},
            temperature=0.9,  # Higher creativity for story variations
            max_tokens=2500
        )

        data = json.loads(response_text)

        return GeneratedProblemResponse(**data)

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"LLM generation failed: {str(e)}")


# Session cleanup endpoints
@router.post("/cleanup-abandoned-sessions")
async def cleanup_abandoned_sessions(
    max_age_hours: int = 24,
    user_id: str = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Manually trigger cleanup of abandoned sessions

    Args:
        max_age_hours: Sessions older than this will be cleaned (default: 24)

    Returns:
        Number of sessions cleaned
    """
    cleaned_count = await session_cleanup_service.cleanup_abandoned_sessions(db, max_age_hours)
    return {
        "cleaned_count": cleaned_count,
        "max_age_hours": max_age_hours,
        "message": f"Cleaned {cleaned_count} abandoned sessions"
    }


@router.get("/session-stats")
async def get_session_stats(user_id: str = Depends(get_current_user)):
    """
    Get Redis session statistics

    Returns:
        Active session count and memory usage
    """
    stats = await session_cleanup_service.get_session_stats()
    return stats


@router.post("/restore-session/{session_id}")
async def restore_session(
    session_id: str,
    user_id: str = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Restore session from PostgreSQL to Redis

    Use case: User's session expired from Redis (TTL), but they want to continue

    Args:
        session_id: UUID of session to restore

    Returns:
        Restored session data or error
    """
    session_data = await session_cleanup_service.restore_session(session_id, db)

    if not session_data:
        return {
            "success": False,
            "message": "Session not found or already completed"
        }

    return {
        "success": True,
        "message": "Session restored to Redis",
        "session_id": session_id,
        "current_round": session_data.get("current_round"),
        "rounds_count": len(session_data.get("rounds", []))
    }
