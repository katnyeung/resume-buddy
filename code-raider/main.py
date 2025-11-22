"""
Code Raider - Story-Based Coding Education Game
FastAPI main application entry point
"""
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import os

from infrastructure.database import init_db
from api.routes import admin, round_robin, collaborative_ws


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Startup and shutdown events
    """
    # Startup: Initialize database
    print("🚀 Starting Code Raider...")
    await init_db()
    print("✅ Database initialized")

    yield

    # Shutdown
    print("👋 Shutting down Code Raider...")


# Create FastAPI app
app = FastAPI(
    title="Code Raider",
    description="Story-based coding education game with CIPHER AI mentor",
    version="1.0.0",
    lifespan=lifespan
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Allow all origins for development
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(admin.router, prefix="/api")
app.include_router(round_robin.router, prefix="/api")
app.include_router(collaborative_ws.router, prefix="/ws")

# Mount static files (frontend + audio)
os.makedirs("static", exist_ok=True)
os.makedirs("static/audio", exist_ok=True)
app.mount("/static", StaticFiles(directory="static"), name="static")
app.mount("/audio", StaticFiles(directory="static/audio"), name="audio")


@app.get("/")
async def root():
    """
    Health check
    """
    return {
        "message": "Code Raider API",
        "status": "running",
        "version": "1.0.0"
    }


@app.get("/health")
async def health():
    """
    Health check endpoint
    """
    return {"status": "healthy"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8087,
        reload=True,  # Auto-reload on code changes
        log_level="info"
    )
