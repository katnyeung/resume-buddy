#!/bin/bash
# Start Docling microservice

echo "🚀 Starting Docling Resume Parser Service..."

# Install dependencies if not already installed
pip install -r requirements.txt

# Start the FastAPI service
echo "📡 Starting service on http://localhost:8081"
python app.py