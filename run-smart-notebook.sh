#!/bin/bash

# ==================================================================================
# Smart Notebook - Lifecycle Management Script
# ==================================================================================
# Purpose: Starts the application and ensures all Docker resources (RAM hungry!)
#          are shut down immediately when you exit the application.
# ==================================================================================

# 1. Set Java 21 Environment (Required)
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"

if [ ! -d "$JAVA_HOME" ]; then
    echo "❌ Error: Java 21 not found at $JAVA_HOME"
    echo "Please install it using: brew install openjdk@21"
    exit 1
fi

echo "✅ Using Java: $JAVA_HOME"

# 2. Define Cleanup Function
# This runs automatically when you press Ctrl+C or the app stops
shutdown_resources() {
    echo ""
    echo "🛑 Shutting down Smart Notebook..."

    if [ -n "$WORKER_PID" ] && kill -0 "$WORKER_PID" 2>/dev/null; then
        echo "🐍 Stopping ingestion worker (PID: $WORKER_PID)..."
        kill "$WORKER_PID" 2>/dev/null || true
    fi

    echo "🐳 Stopping Docker containers to free up RAM..."
    
    # Explicitly stop and remove containers defined in compose.yaml
    docker compose down
    
    echo "✅ System Cleaned. Docker resources released."
}

# 3. Register the trap to catch Exit and Ctrl+C
trap shutdown_resources EXIT INT TERM

# 4. Start the Application
echo "🚀 Starting Application..."
echo "   - Docker services (Ollama, Redis, Postgres) will start automatically."
echo "   - Press Ctrl+C to stop everything and free resources."

echo "🐳 Ensuring Docker dependencies are running..."
docker compose up -d postgres redis ollama

echo "⏳ Waiting for Ollama to become reachable..."
for i in {1..60}; do
    if curl -fsS http://localhost:11434/api/tags >/dev/null 2>&1; then
        break
    fi
    sleep 2
    if [ "$i" -eq 60 ]; then
        echo "❌ Ollama did not become ready in time"
        exit 1
    fi
done

if [ ! -x ".venv/bin/python" ]; then
    echo "❌ Error: Python virtualenv not found at .venv/bin/python"
    echo "Please run: python3 -m venv .venv && source .venv/bin/activate && pip install -r worker/requirements.txt"
    exit 1
fi

echo "🐍 Starting ingestion worker..."
.venv/bin/python -u worker/worker.py > worker/worker.log 2>&1 &
WORKER_PID=$!
echo "   - Worker PID: $WORKER_PID (logs: worker/worker.log)"

sleep 1
if ! kill -0 "$WORKER_PID" 2>/dev/null; then
    echo "❌ Worker failed to start. Check worker/worker.log"
    exit 1
fi

./kill_port.sh
./mvnw spring-boot:run
