#!/bin/bash
# Docker startup script for Docling service

echo "🐳 Starting Docling Parser Service with Docker..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

# Create uploads directory if it doesn't exist
mkdir -p uploads

# Stop any existing container
echo "🛑 Stopping existing container..."
docker-compose down

# Build and start the service
echo "🔨 Building Docker image..."
docker-compose build --no-cache

echo "🚀 Starting Docling service..."
docker-compose up -d

# Wait for service to be healthy
echo "⏳ Waiting for service to be ready..."
timeout 60 bash -c 'until curl -f http://localhost:8081/health &>/dev/null; do sleep 2; done'

if curl -f http://localhost:8081/health &>/dev/null; then
    echo "✅ Docling service is running successfully!"
    echo "📡 Service URL: http://localhost:8081"
    echo "🏥 Health check: http://localhost:8081/health"
    echo ""
    echo "📋 Container status:"
    docker-compose ps
    echo ""
    echo "📝 To view logs: docker-compose logs -f"
    echo "🛑 To stop: docker-compose down"
else
    echo "❌ Service failed to start properly"
    echo "📋 Container logs:"
    docker-compose logs
    exit 1
fi