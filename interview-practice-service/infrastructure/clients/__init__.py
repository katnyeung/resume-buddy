"""HTTP clients for external APIs."""
from .resume_api_client import (
    check_credits,
    deduct_credits,
    get_resume_analysis,
    get_user_info,
    send_email
)
from .jobsearch_api_client import (
    get_job_listing,
    get_matching_profile,
    get_skill_gaps
)

__all__ = [
    "check_credits",
    "deduct_credits",
    "get_resume_analysis",
    "get_user_info",
    "send_email",
    "get_job_listing",
    "get_matching_profile",
    "get_skill_gaps"
]
