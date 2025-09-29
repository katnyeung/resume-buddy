#!/bin/bash
# Complete Resume Buddy startup script with Docker

echo "🚀 Starting Resume Buddy Application (Docker Mode)..."

# Function to check if a port is in use
check_port() {
    lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null
}

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

# Start Docling service with Docker
echo "🐳 Starting Docling microservice with Docker..."
cd docling-service
chmod +x docker-start.sh
./docker-start.sh

# Check if Docling service started successfully
if ! curl -f http://localhost:8081/health &>/dev/null; then
    echo "❌ Failed to start Docling service"
    exit 1
fi

cd ..

# Start Spring Boot backend
echo "🔧 Starting Spring Boot backend..."
if check_port 8080; then
    echo "⚠️  Port 8080 already in use. Please stop the existing service."
    exit 1
else
    cd backend
    mvn spring-boot:run &
    BACKEND_PID=$!
    echo "🔧 Backend starting on PID $BACKEND_PID"
    cd ..
fi

# Wait for backend to start
echo "⏳ Waiting for backend to initialize..."
sleep 15

# Test connections
echo "🧪 Testing services..."

# Test Docling service
if curl -s http://localhost:8081/health > /dev/null; then
    echo "✅ Docling service (Docker) is healthy"
else
    echo "❌ Docling service is not responding"
fi

# Test backend
if curl -s http://localhost:8080/api/resumes/health > /dev/null; then
    echo "✅ Backend is healthy"
else
    echo "❌ Backend is not responding"
fi

echo ""
echo "🎉 Resume Buddy is running!"
echo "🐳 Docling Service (Docker): http://localhost:8081"
echo "🔧 Backend API: http://localhost:8080/api"
echo "📖 API Documentation: http://localhost:8080/api/swagger-ui.html"
echo ""
echo "📝 To stop all services, press Ctrl+C or run: ./stop-with-docker.sh"
echo "📋 To view Docling logs: cd docling-service && docker-compose logs -f"

# Keep script running
wait