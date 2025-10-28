"""HTTP client for Resume API (port 8080)."""
import httpx
import os
from typing import Dict, Any, Optional


RESUME_API_URL = os.getenv("RESUME_API_URL", "http://localhost:8080/api")
RESUME_API_KEY = os.getenv("RESUME_API_KEY", "dev-internal-api-key-change-in-production")
TIMEOUT = 30.0  # 30 seconds timeout


def _get_headers() -> Dict[str, str]:
    """Get headers with API key for service-to-service auth."""
    return {
        "X-API-Key": RESUME_API_KEY,
        "Content-Type": "application/json"
    }


async def check_credits(user_id: str) -> Dict[str, Any]:
    """
    Check user's available credits.

    Args:
        user_id: User ID (UUID string)

    Returns:
        {
            "userId": "uuid-string",
            "availableCredits": 500,
            "totalCredits": 1000
        }
    """
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        response = await client.get(
            f"{RESUME_API_URL}/users/{user_id}/credits",
            headers=_get_headers()
        )
        response.raise_for_status()
        return response.json()


async def deduct_credits(user_id: str, amount: int, job_id: str, reason: str = "Interview Practice Session") -> Dict[str, Any]:
    """
    Deduct credits from user's account.

    Args:
        user_id: User ID (UUID string)
        amount: Amount of credits to deduct
        job_id: Job/Session ID for tracking
        reason: Reason for deduction

    Returns:
        {
            "success": true,
            "message": "Credits deducted successfully"
        }
    """
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        response = await client.post(
            f"{RESUME_API_URL}/users/{user_id}/credits/deduct",
            headers=_get_headers(),
            json={
                "amount": amount,
                "jobId": job_id,
                "description": reason
            }
        )
        response.raise_for_status()
        return response.json()


async def get_resume_analysis(resume_id: str) -> Optional[Dict[str, Any]]:
    """
    Get analyzed resume data for generating contextual questions.

    Args:
        resume_id: Resume ID (UUID string)

    Returns:
        {
            "resumeId": "uuid",
            "userId": "uuid",
            "fileName": "john_doe_resume.pdf",
            "status": "ANALYZED",
            "structuredData": {...},
            "analysis": {...}
        }
    """
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        try:
            response = await client.get(
                f"{RESUME_API_URL}/resumes/{resume_id}",
                headers=_get_headers()
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                return None
            raise


async def get_job_analysis(resume_id: str, experience_id: str) -> Optional[Dict[str, Any]]:
    """
    Get job analysis data for a specific experience (with skill assessments, O*NET mappings).

    Args:
        resume_id: Resume ID (UUID string)
        experience_id: Experience ID (UUID string)

    Returns:
        {
            "id": "analysis-uuid",
            "resumeId": "resume-uuid",
            "experienceId": "exp-uuid",
            "analysisResult": {
                "occupations": [...],  # O*NET mappings
                "technicalSkills": [...],  # Skill assessments with credibility
                "softSkills": [...],
                "taskCoverage": {...},  # Radar chart data
                "comprehensiveAnalysis": "..."
            },
            "createdAt": "2025-10-27T..."
        }
    """
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        try:
            response = await client.get(
                f"{RESUME_API_URL}/resumes/{resume_id}/experiences/{experience_id}/analysis",
                headers=_get_headers()
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                return None
            raise


async def get_user_info(user_id: int) -> Optional[Dict[str, Any]]:
    """
    Get user information for email sending.

    Args:
        user_id: User ID

    Returns:
        {
            "userId": 123,
            "email": "user@example.com",
            "fullName": "John Doe"
        }
    """
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        try:
            response = await client.get(
                f"{RESUME_API_URL}/users/{user_id}",
                headers=_get_headers()
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                return None
            raise


async def send_email(user_id: str, subject: str, body: str) -> Dict[str, Any]:
    """
    Send email to user (reuse Resume API's email service).

    Args:
        user_id: User ID (UUID string)
        subject: Email subject
        body: Email body (HTML or plain text)

    Returns:
        {
            "sent": true,
            "message": "Email sent successfully"
        }
    """
    async with httpx.AsyncClient(timeout=TIMEOUT) as client:
        response = await client.post(
            f"{RESUME_API_URL}/notifications/send",
            headers=_get_headers(),
            json={
                "userId": user_id,
                "subject": subject,
                "body": body
            }
        )
        response.raise_for_status()
        return response.json()
