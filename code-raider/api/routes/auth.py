"""
Authentication using Resume Buddy backend JWT
"""
import os
from fastapi import HTTPException, Header, Depends
from jose import JWTError, jwt
from typing import Optional
from dotenv import load_dotenv

load_dotenv()

RESUME_API_URL = os.getenv("RESUME_API_URL", "http://localhost:8080")
JWT_SECRET = os.getenv("JWT_SECRET", "your-secret-key-from-resume-buddy")
JWT_ALGORITHM = "HS512"  # Resume Buddy uses HS512


async def verify_token(authorization: Optional[str] = Header(None)) -> str:
    """
    Verify JWT token from Resume Buddy backend

    Args:
        authorization: Authorization header (Bearer token)

    Returns:
        user_id extracted from token

    Raises:
        HTTPException if token is invalid
    """
    if not authorization:
        print("❌ Auth: Missing authorization header")
        raise HTTPException(status_code=401, detail="Missing authorization header")

    try:
        # Extract token from "Bearer <token>"
        token = authorization.split(" ")[1] if " " in authorization else authorization
        print(f"🔍 Auth: Attempting to decode token (first 20 chars): {token[:20]}...")

        # Decode JWT
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        print(f"✅ Auth: Token decoded successfully. Payload keys: {list(payload.keys())}")

        # Extract user_id (check multiple possible field names)
        user_id = (
            payload.get("sub") or
            payload.get("userId") or
            payload.get("user_id") or
            payload.get("id")
        )

        if not user_id:
            print(f"❌ Auth: No user_id found in payload: {payload}")
            raise HTTPException(status_code=401, detail=f"Invalid token payload. Available keys: {list(payload.keys())}")

        print(f"✅ Auth: User authenticated: {user_id}")
        return str(user_id)

    except JWTError as e:
        print(f"❌ Auth: JWT decode error: {str(e)}")
        raise HTTPException(status_code=401, detail=f"Invalid token: {str(e)}")
    except IndexError:
        print("❌ Auth: Invalid authorization header format")
        raise HTTPException(status_code=401, detail="Invalid authorization header format")
    except Exception as e:
        print(f"❌ Auth: Unexpected error: {str(e)}")
        raise HTTPException(status_code=401, detail=f"Authentication error: {str(e)}")


# Dependency for protecting routes
def get_current_user(user_id: str = Depends(verify_token)) -> str:
    """
    FastAPI dependency to get current user from JWT

    Usage: user_id: str = Depends(get_current_user)
    """
    return user_id
