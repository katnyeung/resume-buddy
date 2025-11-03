# Interview Practice Service - Docker Image
# Python FastAPI + LangGraph + OpenAI (Whisper/TTS) + Grok LLM

FROM python:3.10-slim

# Set working directory
WORKDIR /app

# Install system dependencies
# - ffmpeg: Audio processing for OpenAI Whisper/TTS
# - libsndfile1: Audio file handling for pydub
# - gcc, g++, python3-dev: Build tools for webrtcvad
RUN apt-get update && apt-get install -y \
    ffmpeg \
    libsndfile1 \
    gcc \
    g++ \
    python3-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better layer caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create audio storage directory
RUN mkdir -p /app/audio_files

# Expose port
EXPOSE 8086

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD python -c "import requests; requests.get('http://localhost:8086/health')" || exit 1

# Run application with unbuffered output for real-time logs
ENV PYTHONUNBUFFERED=1
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8086"]
