#!/bin/bash
# Stop all Resume Buddy services (Docker mode)

echo "🛑 Stopping Resume Buddy services (Docker mode)..."

# Stop Spring Boot backend
echo "🔧 Stopping Spring Boot backend..."
lsof -ti:8080 | xargs kill -9 2>/dev/null || echo "Backend not running"
pkill -f "spring-boot:run" 2>/dev/null || true

# Stop Docling Docker service
echo "🐳 Stopping Docling Docker service..."
cd docling-service
docker-compose down
cd ..

echo "✅ All services stopped!"
echo "📋 To completely remove Docker containers and images:"
echo "   cd docling-service && docker-compose down --rmi all"