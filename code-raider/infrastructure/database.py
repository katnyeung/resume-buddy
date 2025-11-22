"""
Database connection and session management for PostgreSQL (Neon)
"""
import os
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import declarative_base
from dotenv import load_dotenv

load_dotenv()

# Build DATABASE_URL from components to ensure proper format
db_username = os.getenv("DB_USERNAME", "neondb_owner")
db_password = os.getenv("DB_PASSWORD", "")
db_url_base = os.getenv("DB_URL", "")

# Extract host, port, database from DB_URL
# Format: postgresql://host:port/database?params
if db_url_base:
    # Remove protocol
    url_without_protocol = db_url_base.replace("postgresql://", "")
    # Remove query params
    url_without_params = url_without_protocol.split("?")[0]
    # Parse host/port/database
    host_and_db = url_without_params.split("/")
    host_port = host_and_db[0] if len(host_and_db) > 0 else "localhost:5432"
    database = host_and_db[1] if len(host_and_db) > 1 else "neondb"

    # Rebuild URL with credentials for asyncpg
    DATABASE_URL = f"postgresql+asyncpg://{db_username}:{db_password}@{host_port}/{database}"
else:
    DATABASE_URL = None

# Create async engine with SSL support for Neon
engine = create_async_engine(
    DATABASE_URL,
    echo=False,  # Set to True for SQL debugging
    pool_size=5,
    max_overflow=10,
    pool_pre_ping=True,  # Verify connections before using
    connect_args={
        "ssl": "require",  # Enable SSL for Neon
        "server_settings": {
            "application_name": "code_raider"
        }
    }
)

# Create async session factory
AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False,
)

# Base class for models
Base = declarative_base()


async def get_db() -> AsyncSession:
    """
    Dependency for FastAPI to get database session
    Usage: db: AsyncSession = Depends(get_db)
    """
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()


async def init_db():
    """
    Initialize database (create tables if not exist)
    Call this on app startup
    """
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
