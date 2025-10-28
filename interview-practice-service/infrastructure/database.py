"""PostgreSQL database connection for interview practice service."""
import os
from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

# Load environment variables first (critical for DATABASE_URL)
load_dotenv()

# Support both DB_URL (backend convention) and DATABASE_URL (fallback)
DATABASE_URL = os.getenv("DB_URL") or os.getenv("DATABASE_URL")

# If DB_URL is JDBC format (from Java backend), convert to SQLAlchemy format
if DATABASE_URL and DATABASE_URL.startswith("jdbc:"):
    # jdbc:postgresql://host:port/dbname?params -> postgresql://user:pass@host:port/dbname?params
    DATABASE_URL = DATABASE_URL.replace("jdbc:", "")
    # Need to add username and password from separate env vars
    DB_USERNAME = os.getenv("DB_USERNAME")
    DB_PASSWORD = os.getenv("DB_PASSWORD")
    if DB_USERNAME and DB_PASSWORD:
        # Extract host and remaining parts
        if "://" in DATABASE_URL:
            protocol, rest = DATABASE_URL.split("://", 1)
            DATABASE_URL = f"{protocol}://{DB_USERNAME}:{DB_PASSWORD}@{rest}"

if not DATABASE_URL:
    # Fallback to MySQL for local development
    MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
    MYSQL_PORT = os.getenv("MYSQL_PORT", "3307")
    MYSQL_USER = os.getenv("MYSQL_USER", "root")
    MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "password")
    MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "interview_practice")
    DATABASE_URL = f"mysql+pymysql://{MYSQL_USER}:{MYSQL_PASSWORD}@{MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}"

# PostgreSQL requires psycopg2, MySQL requires pymysql
engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
    pool_size=5,
    max_overflow=10
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def get_db():
    """Get database session."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
