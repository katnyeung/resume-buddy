"""Interview Practice Service - FastAPI application."""
import os
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv
from api.routes import sessions, streaming
from domain.services.email_scheduler import start_scheduler, stop_scheduler
from infrastructure.clients.redis_client import init_redis_store

# Load environment variables
load_dotenv()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Application lifespan manager for startup and shutdown events.

    Startup:
    - Initialize Redis conversation store
    - Start email scheduler for round reminders

    Shutdown:
    - Stop email scheduler gracefully
    """
    # Startup
    redis_uri = os.getenv("REDIS_URI", "redis://localhost:6379")
    init_redis_store(redis_uri)
    print(f"[Startup] Redis conversation store initialized: {redis_uri}")

    start_scheduler()
    yield
    # Shutdown
    stop_scheduler()


# Create FastAPI app
app = FastAPI(
    title="Interview Practice Service",
    description="AI-powered voice interview practice with LangGraph state management",
    version="0.1.0",
    lifespan=lifespan
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify actual origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(sessions.router)
app.include_router(streaming.router)  # WebSocket streaming endpoints


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "service": "interview-practice-service",
        "version": "0.1.0"
    }


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "message": "Interview Practice Service",
        "docs": "/docs",
        "health": "/health"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=int(os.getenv("PORT", "8086")),
        reload=True
    )
