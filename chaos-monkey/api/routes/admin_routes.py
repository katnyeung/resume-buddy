"""
Admin API routes for chaos scenario management
CRUD operations for scenarios
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import uuid
import json

from infrastructure.database import db
from domain.services.scenario_generator import scenario_generator

router = APIRouter(prefix="/api/admin")


# Pydantic models for request/response
class GenerateRequest(BaseModel):
    incident_text: str
    story_theme: Optional[str] = ""


class ScenarioCreate(BaseModel):
    story_title: str
    story_theme: str
    story_intro: str
    story_stakes: str
    target_lesson: str
    anti_pattern: str
    difficulty: str
    initial_architecture: Dict[str, Any]
    attack_options: List[Dict[str, Any]] = []  # LLM-generated attack vectors


class ScenarioResponse(BaseModel):
    id: str
    story_title: str
    story_theme: str
    story_intro: str
    story_stakes: str
    target_lesson: str
    anti_pattern: str
    difficulty: str
    initial_architecture: Dict[str, Any]
    created_at: str


@router.post("/scenarios/generate")
async def generate_scenario(request: GenerateRequest):
    """
    Generate 3 scenario variations from real-world incident using LLM

    Example request:
    {
      "incident_text": "In 2017, AWS S3 went down for 4 hours...",
      "story_theme": "e-commerce Black Friday"
    }

    Returns:
    {
      "variations": [
        {
          "story_title": "The Black Friday Meltdown",
          "story_theme": "e-commerce",
          ...
        },
        ... (2 more)
      ]
    }
    """
    try:
        variations = await scenario_generator.generate_from_incident(
            incident_text=request.incident_text,
            story_theme_hint=request.story_theme
        )

        return {"variations": variations}

    except ValueError as e:
        raise HTTPException(status_code=400, detail=f"Generation failed: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Unexpected error: {str(e)}")


@router.post("/scenarios", response_model=ScenarioResponse)
async def create_scenario(scenario: ScenarioCreate):
    """
    Save scenario to database

    Example request:
    {
      "story_title": "The Black Friday Meltdown",
      "story_theme": "e-commerce",
      "story_intro": "It's 3 AM on Black Friday...",
      "story_stakes": "$500k/hour revenue loss",
      "target_lesson": "database_per_service",
      "anti_pattern": "shared_database",
      "difficulty": "MEDIUM",
      "initial_architecture": {...}
    }
    """
    try:
        scenario_id = str(uuid.uuid4())

        # Convert architecture dict to JSON string for PostgreSQL JSONB
        arch_json = json.dumps(scenario.initial_architecture)

        row = await db.fetch_one(
            """
            INSERT INTO chaos_scenarios
            (id, story_title, story_theme, story_intro, story_stakes,
             target_lesson, anti_pattern, difficulty, initial_architecture)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9::jsonb)
            RETURNING id, story_title, story_theme, story_intro, story_stakes,
                      target_lesson, anti_pattern, difficulty,
                      initial_architecture, created_at
            """,
            scenario_id,
            scenario.story_title,
            scenario.story_theme,
            scenario.story_intro,
            scenario.story_stakes,
            scenario.target_lesson,
            scenario.anti_pattern,
            scenario.difficulty,
            arch_json
        )

        # Parse JSONB field back to dict (asyncpg returns it as JSON string in some cases)
        architecture = row["initial_architecture"]
        if isinstance(architecture, str):
            architecture = json.loads(architecture)

        return {
            "id": str(row["id"]),
            "story_title": row["story_title"],
            "story_theme": row["story_theme"],
            "story_intro": row["story_intro"],
            "story_stakes": row["story_stakes"],
            "target_lesson": row["target_lesson"],
            "anti_pattern": row["anti_pattern"],
            "difficulty": row["difficulty"],
            "initial_architecture": architecture,
            "created_at": row["created_at"].isoformat()
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")


@router.get("/scenarios", response_model=List[ScenarioResponse])
async def list_scenarios():
    """
    List all scenarios

    Returns array of scenarios ordered by creation date (newest first)
    """
    try:
        rows = await db.fetch_all(
            """
            SELECT id, story_title, story_theme, story_intro, story_stakes,
                   target_lesson, anti_pattern, difficulty,
                   initial_architecture, created_at
            FROM chaos_scenarios
            ORDER BY created_at DESC
            """
        )

        scenarios = []
        for row in rows:
            # Parse JSONB field back to dict
            architecture = row["initial_architecture"]
            if isinstance(architecture, str):
                architecture = json.loads(architecture)

            scenarios.append({
                "id": str(row["id"]),
                "story_title": row["story_title"],
                "story_theme": row["story_theme"],
                "story_intro": row["story_intro"],
                "story_stakes": row["story_stakes"],
                "target_lesson": row["target_lesson"],
                "anti_pattern": row["anti_pattern"],
                "difficulty": row["difficulty"],
                "initial_architecture": architecture,
                "created_at": row["created_at"].isoformat()
            })

        return scenarios

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")


@router.delete("/scenarios/{scenario_id}")
async def delete_scenario(scenario_id: str):
    """
    Delete scenario by ID

    Returns status message
    """
    try:
        result = await db.execute(
            "DELETE FROM chaos_scenarios WHERE id = $1",
            uuid.UUID(scenario_id)
        )

        if result == "DELETE 0":
            raise HTTPException(status_code=404, detail="Scenario not found")

        return {"status": "deleted", "id": scenario_id}

    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid scenario ID format")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")


@router.get("/scenarios/{scenario_id}", response_model=ScenarioResponse)
async def get_scenario(scenario_id: str):
    """
    Get single scenario by ID
    """
    try:
        row = await db.fetch_one(
            """
            SELECT id, story_title, story_theme, story_intro, story_stakes,
                   target_lesson, anti_pattern, difficulty,
                   initial_architecture, created_at
            FROM chaos_scenarios
            WHERE id = $1
            """,
            uuid.UUID(scenario_id)
        )

        if not row:
            raise HTTPException(status_code=404, detail="Scenario not found")

        # Parse JSONB field back to dict
        architecture = row["initial_architecture"]
        if isinstance(architecture, str):
            architecture = json.loads(architecture)

        return {
            "id": str(row["id"]),
            "story_title": row["story_title"],
            "story_theme": row["story_theme"],
            "story_intro": row["story_intro"],
            "story_stakes": row["story_stakes"],
            "target_lesson": row["target_lesson"],
            "anti_pattern": row["anti_pattern"],
            "difficulty": row["difficulty"],
            "initial_architecture": architecture,
            "created_at": row["created_at"].isoformat()
        }

    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid scenario ID format")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
