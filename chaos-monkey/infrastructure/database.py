"""
Database connection and session management for PostgreSQL
Adapted from code-raider for chaos-monkey
Uses DB_URL, DB_USERNAME, DB_PASSWORD pattern (same as code-raider)
"""
import os
import asyncpg
from typing import Optional
from dotenv import load_dotenv

load_dotenv()

# Build DATABASE_URL from components (same pattern as code-raider)
db_username = os.getenv("DB_USERNAME", "postgres")
db_password = os.getenv("DB_PASSWORD", "")
db_url_base = os.getenv("DB_URL", "postgresql://localhost:5432/chaos_monkey")

# Extract host, port, database from DB_URL
# Format: postgresql://host:port/database?params
if db_url_base and db_username and db_password:
    # Remove protocol
    url_without_protocol = db_url_base.replace("postgresql://", "")
    # Split query params
    url_parts = url_without_protocol.split("?")
    url_without_params = url_parts[0]
    query_params = url_parts[1] if len(url_parts) > 1 else ""

    # Parse host/port/database
    host_and_db = url_without_params.split("/")
    host_port = host_and_db[0] if len(host_and_db) > 0 else "localhost:5432"
    database = host_and_db[1] if len(host_and_db) > 1 else "chaos_monkey"

    # Rebuild URL with credentials
    DATABASE_URL = f"postgresql://{db_username}:{db_password}@{host_port}/{database}"
    if query_params:
        DATABASE_URL += f"?{query_params}"
else:
    DATABASE_URL = "postgresql://localhost:5432/chaos_monkey"


class Database:
    """
    PostgreSQL connection pool manager
    Uses asyncpg for async operations
    """
    def __init__(self):
        self.pool: Optional[asyncpg.Pool] = None

    async def connect(self):
        """
        Create connection pool on startup
        """
        self.pool = await asyncpg.create_pool(
            DATABASE_URL,
            min_size=2,
            max_size=10,
            command_timeout=60
        )

    async def disconnect(self):
        """
        Close connection pool on shutdown
        """
        if self.pool:
            await self.pool.close()

    async def fetch_one(self, query: str, *args):
        """
        Fetch single row
        """
        async with self.pool.acquire() as conn:
            return await conn.fetchrow(query, *args)

    async def fetch_all(self, query: str, *args):
        """
        Fetch multiple rows
        """
        async with self.pool.acquire() as conn:
            return await conn.fetch(query, *args)

    async def execute(self, query: str, *args):
        """
        Execute query (INSERT/UPDATE/DELETE)
        Returns: status string (e.g., "INSERT 0 1")
        """
        async with self.pool.acquire() as conn:
            return await conn.execute(query, *args)

    async def execute_many(self, query: str, args_list):
        """
        Execute query with multiple parameter sets
        """
        async with self.pool.acquire() as conn:
            return await conn.executemany(query, args_list)


# Singleton instance
db = Database()


async def init_db():
    """
    Initialize database connection on app startup
    """
    await db.connect()


async def close_db():
    """
    Close database connection on app shutdown
    """
    await db.disconnect()
