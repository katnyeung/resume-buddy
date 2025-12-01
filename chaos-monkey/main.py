"""
Chaos Monkey - System Architecture Learning Game
Main FastAPI application entry point
"""
import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Import infrastructure
from infrastructure.database import init_db, close_db

# Import routers
from api.routes import admin_routes, session_routes, chaos_ws

app = FastAPI(
    title="Chaos Monkey - System Architecture Game",
    description="Learn system architecture patterns by exploiting flaws",
    version="1.0.0"
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production: specify frontend domain
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Serve static files
app.mount("/static", StaticFiles(directory="static"), name="static")

# Serve audio files (TTS output)
os.makedirs("static/audio", exist_ok=True)
app.mount("/audio", StaticFiles(directory="static/audio"), name="audio")

# Include routers
app.include_router(admin_routes.router)
app.include_router(session_routes.router)  # Week 2 ✅
app.include_router(chaos_ws.router)  # Week 3 ✅


@app.on_event("startup")
async def startup():
    """
    Initialize database connection pool on startup
    """
    print("🚀 Starting Chaos Monkey server...")
    await init_db()
    print("✅ Database connected")


@app.on_event("shutdown")
async def shutdown():
    """
    Close database connection pool on shutdown
    """
    print("👋 Shutting down Chaos Monkey server...")
    await close_db()
    print("✅ Database disconnected")


@app.get("/")
async def root():
    return {
        "message": "Chaos Monkey - System Architecture Game",
        "version": "1.0.0",
        "endpoints": {
            "lobby": "/static/lobby.html",
            "admin": "/static/admin.html",
            "gameplay": "/static/chaos.html?session={id} (Week 3)",
            "api_docs": "/docs"
        }
    }


@app.get("/health")
async def health_check():
    return {"status": "healthy", "database": "connected"}


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", 8088))
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=port,
        reload=True  # Hot reload during development
    )
