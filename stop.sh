#!/bin/bash

# ==================================================================================
# Smart Notebook - Stop Script
# ==================================================================================
# Gracefully stops all services and unloads models to free RAM.
# ==================================================================================

# Model configuration (matches application.yml defaults)
EXTRACTION_MODEL="${EXTRACTION_MODEL:-llama3.2}"
EMBEDDING_MODEL="${EMBEDDING_MODEL:-nomic-embed-text}"
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"

echo "🛑 Stopping Smart Notebook..."

# 1. Stop Spring Boot App
./kill_port.sh

# 2. Stop Python Worker
pkill -f "worker/worker.py" && echo "🐍 Python Worker stopped." || echo "ℹ️  No Python Worker found."

# 3. Unload Ollama models (frees ~2-4GB RAM per model)
echo "🧠 Unloading Ollama models..."
if curl -sf "$OLLAMA_URL/api/tags" >/dev/null 2>&1; then
    curl -sf "$OLLAMA_URL/api/generate" -d "{\"model\": \"$EXTRACTION_MODEL\", \"keep_alive\": 0}" >/dev/null 2>&1 || true
    curl -sf "$OLLAMA_URL/api/embeddings" -d "{\"model\": \"$EMBEDDING_MODEL\", \"keep_alive\": 0}" >/dev/null 2>&1 || true
    echo "   ✅ Models unloaded"
else
    echo "   ℹ️  Ollama not running"
fi

# 4. Stop Ollama server (releases CPU/RAM)
echo "🦙 Stopping Ollama server..."
if pgrep -f "ollama serve" >/dev/null 2>&1; then
    pkill -f "ollama serve" >/dev/null 2>&1 || true
    echo "   ✅ Ollama server stopped"
else
    echo "   ℹ️  Ollama server not running"
fi

# 5. Stop Postgres Docker service
echo "🐳 Stopping PostgreSQL container..."
docker compose stop postgres >/dev/null 2>&1 || true

echo "✅ All services stopped."
