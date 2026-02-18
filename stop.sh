#!/bin/bash

echo "🛑 Stopping Smart Notebook and all services..."

# 1. Stop Spring Boot App (if running continuously or in background)
# This script usually runs killing port 8080 checks
./kill_port.sh

# 2. Stop Python Worker
# Find python worker process and kill it
pkill -f "worker/worker.py" && echo "🐍 Python Worker stopped." || echo "ℹ️  No Python Worker found."

# 3. Stop Docker Containers (Postgres, Redis, Ollama)
echo "🐳 Stopping Docker containers..."
docker compose down

echo "✅ All services stopped successfully."
