#!/bin/bash
# Complete Resume Buddy startup script

echo "🚀 Starting Resume Buddy Application..."

# Function to check if a port is in use
check_port() {
    lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null
}

# Check and start Docling service
echo "📡 Starting Docling microservice..."
if check_port 8081; then
    echo "⚠️  Port 8081 already in use. Skipping Docling service startup."
else
    cd docling-service
    chmod +x start.sh
    ./start.sh &
    DOCLING_PID=$!
    echo "📡 Docling service starting on PID $DOCLING_PID"
    cd ..

    # Wait a bit for Docling service to start
    echo "⏳ Waiting for Docling service to initialize..."
    sleep 5
fi

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
sleep 10

# Test connections
echo "🧪 Testing services..."

# Test Docling service
if curl -s http://localhost:8081/health > /dev/null; then
    echo "✅ Docling service is healthy"
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
echo "📡 Docling Service: http://localhost:8081"
echo "🔧 Backend API: http://localhost:8080/api"
echo "📖 API Documentation: http://localhost:8080/api/swagger-ui.html"
echo ""
echo "📝 To stop all services, press Ctrl+C or run: ./stop-all.sh"

# Keep script running
wait