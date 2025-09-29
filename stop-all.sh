#!/bin/bash
# Stop all Resume Buddy services

echo "🛑 Stopping Resume Buddy services..."

# Kill services by port
echo "🔧 Stopping backend (port 8080)..."
lsof -ti:8080 | xargs kill -9 2>/dev/null || echo "Backend not running"

echo "📡 Stopping Docling service (port 8081)..."
lsof -ti:8081 | xargs kill -9 2>/dev/null || echo "Docling service not running"

# Also kill by process name
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "uvicorn" 2>/dev/null || true
pkill -f "docling" 2>/dev/null || true

echo "✅ All services stopped!"