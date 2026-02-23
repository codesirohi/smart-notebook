#!/bin/bash

# ==================================================================================
# Smart Notebook - Full Stack Startup Script
# ==================================================================================
# Purpose: Starts both the Spring Boot Backend and the Vite Frontend.
#          Handles cleanup of both processes on exit.
# ==================================================================================

# Change to project root
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

# 1. Configuration - use relative paths from project root
BACKEND_DIR="$PROJECT_ROOT"
FRONTEND_DIR="${FRONTEND_DIR:-$(dirname "$PROJECT_ROOT")/smart-notebook-frontend}"

echo "🚀 Starting Smart Notebook Services..."

# 2. Check Directories
if [ ! -d "$BACKEND_DIR" ]; then
    echo "❌ Error: Backend directory not found: $BACKEND_DIR"
    exit 1
fi

if [ ! -d "$FRONTEND_DIR" ]; then
    echo "❌ Error: Frontend directory not found: $FRONTEND_DIR"
    echo "   Set FRONTEND_DIR environment variable to the correct path"
    exit 1
fi

# 3. Define Cleanup Function
cleanup() {
    echo ""
    echo "🛑 Shutting down services..."

    # Kill backend (mvnw/java)
    if [ -n "$BACKEND_PID" ]; then
        echo "   - Stopping Backend (PID: $BACKEND_PID)..."
        kill $BACKEND_PID 2>/dev/null
    fi

    # Kill frontend (npm/vite)
    if [ -n "$FRONTEND_PID" ]; then
        echo "   - Stopping Frontend (PID: $FRONTEND_PID)..."
        kill $FRONTEND_PID 2>/dev/null
    fi

    # Run the backend's cleanup logic (docker down) manually if needed,
    # but killing the process usually triggers its own shutdown hooks.

    echo "✅ All services stopped."
}

# Register trap
trap cleanup EXIT INT TERM

# 4. Start Backend (Background)
echo "☕ Starting Backend (Spring Boot)..."
cd "$BACKEND_DIR"
# Use existing run script if possible, or direct mvnw
# Using run-smart-notebook.sh directly might trap signals and cause issues with backgrounding.
# Let's run mvnw directly but ensure docker env is ready if needed.
# Actually, run-smart-notebook.sh handles docker compose up. Let's use it.
scripts/run-smart-notebook.sh &
BACKEND_PID=$!
echo "   -> Backend started with PID: $BACKEND_PID"

# 5. Start Frontend (Background)
echo "⚛️  Starting Frontend (Vite)..."
cd "$FRONTEND_DIR"
npm run dev &
FRONTEND_PID=$!
echo "   -> Frontend started with PID: $FRONTEND_PID"

echo ""
echo "✨ Both services are running!"
echo "   - Frontend: http://localhost:5173"
echo "   - Backend:  http://localhost:8080"
echo "   - Logs:     See output above (mixed) or check log files."
echo ""
echo "Press Ctrl+C to stop everything."

# 6. Wait for processes
wait
