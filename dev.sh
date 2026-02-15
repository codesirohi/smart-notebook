#!/bin/bash

# Smart Notebook Development Runner
# Runs Python Worker (background) and Spring Boot App (foreground)
# Kills both on Ctrl+C

set -e

# Function to kill all background jobs on exit
cleanup() {
    echo ""
    echo "🛑 Stopping Smart Notebook environment..."
    kill $(jobs -p) 2>/dev/null
    wait
    echo "✅ All services stopped."
}

# Trap SIGINT (Ctrl+C) and SIGTERM
trap cleanup SIGINT SIGTERM

echo "🚀 Starting Smart Notebook..."

# 1. Check for Python environment
if [ ! -d ".venv" ]; then
    echo "❌ Error: .venv directory not found."
    echo "   Please run: python3 -m venv .venv && source .venv/bin/activate && pip install -r worker/requirements.txt"
    exit 1
fi

# 2. Start Python Worker in background
echo "🐍 [Worker] Starting Python ingestion worker..."
source .venv/bin/activate
# Run unbuffered output so logs appear immediately
python -u worker/worker.py &
WORKER_PID=$!

# 3. Start Spring Boot App in foreground
echo "☕ [App] Starting Spring Boot application..."
# Use -Dspring-boot.run.profiles=local to ensure local profile is active
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Wait for cleanup (mvnw usually blocks, but if it exits, we cleanup)
wait $WORKER_PID
