"""HTTP client for Job Search API (port 8085)."""
import httpx
import os
from typing import Dict, Any, Optional


JOBSEARCH_API_URL = os.getenv("JOBSEARCH_API_URL", "http://localhost:8085/api/job-search")
JOBSEARCH_API_KEY = os.getenv("JOBSEARCH_API_KEY", "dev-internal-api-key-change-in-production")
TIMEOUT = 30.0  # 30 seconds timeout


def _get_headers() -> Dict[str, str]:
    """Get headers with API key for service-to-service auth."""
    return {
        "X-API-Key": JOBSEARCH_API_KEY,
        "Content-Type": "application/json"
    }


async def get_job_listing(job_id: int) -> Optional[Dict[str, Any]]:
    """
    Get job listing details for generating contextual interview questions.

    Args:
        job_id: Job listing ID

    Returns:
        {
            "id": 789,
            "jobTitle": "Senior React Developer",
            "companyName": "TechCorp",
            "location": "London, UK",
            "description": "...",
            "requiredSkills": ["React", "TypeScript", "Node.js"],
            "experienceLevel": "SENIOR",
            "salaryRange": "£70k-£90k",
            "postedDate": "2025-10-20"
        }
    """
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        try:
            response = await client.get(
                f"{JOBSEARCH_API_URL}/listings/{job_id}",
                headers=_get_headers()
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                return None
            raise


async def get_matching_profile(profile_id: int) -> Optional[Dict[str, Any]]:
    """
    Get user's job matching profile with skills.

    Args:
        profile_id: Profile ID

    Returns:
        {
            "profileId": 123,
            "userId": 123,
            "desiredJobTitle": "Senior React Developer",
            "location": "London",
            "skills": [
                {"skill": "React", "proficiencyLevel": "EXPERT"},
                {"skill": "TypeScript", "proficiencyLevel": "ADVANCED"}
            ]
        }
    """
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        try:
            response = await client.get(
                f"{JOBSEARCH_API_URL}/profiles/{profile_id}",
                headers=_get_headers()
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                return None
            raise


async def get_skill_gaps(profile_id: int, job_id: int) -> Dict[str, Any]:
    """
    Get skill gaps between user's profile and job requirements.
    Useful for focusing interview questions on weak areas.

    Args:
        profile_id: Profile ID
        job_id: Job listing ID

    Returns:
        {
            "strongSkills": ["React", "JavaScript"],
            "moderateSkills": ["TypeScript", "Node.js"],
            "missingSkills": ["AWS", "Docker"],
            "overallMatch": 75.5
        }
    """
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        try:
            response = await client.get(
                f"{JOBSEARCH_API_URL}/profiles/{profile_id}/skill-gaps",
                params={"jobId": job_id},
                headers=_get_headers()
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as e:
            # If endpoint doesn't exist, return empty gaps
            if e.response.status_code == 404:
                return {
                    "strongSkills": [],
                    "moderateSkills": [],
                    "missingSkills": [],
                    "overallMatch": 0.0
                }
            raise
