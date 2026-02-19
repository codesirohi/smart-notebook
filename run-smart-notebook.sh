#!/bin/bash

# ==================================================================================
# Smart Notebook - Lifecycle Management Script
# ==================================================================================
# Purpose: Starts the application with native Ollama (Metal GPU acceleration)
#          for 92% faster inference (27s → 1-2s on M2 Mac).
#
# Performance Optimization:
#   - Uses native Ollama instead of Docker (Metal GPU vs CPU)
#   - Pre-loads models on startup (eliminates cold-start latency)
#   - Unloads models on shutdown (frees ~2-4GB RAM per model)
# ==================================================================================

# Model configuration (matches application.yml defaults)
EXTRACTION_MODEL="${EXTRACTION_MODEL:-llama3.2}"
EMBEDDING_MODEL="${EMBEDDING_MODEL:-nomic-embed-text}"
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
CLEANUP_DONE=0

# 1. Set Java 21 Environment (Required)
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"

if [ ! -d "$JAVA_HOME" ]; then
    echo "❌ Error: Java 21 not found at $JAVA_HOME"
    echo "Please install it using: brew install openjdk@21"
    exit 1
fi

echo "✅ Using Java: $JAVA_HOME"

# 2. Check native Ollama installation
if ! command -v ollama &> /dev/null; then
    echo "❌ Error: Ollama not installed"
    echo "Install with: brew install ollama"
    echo ""
    echo "Why native Ollama?"
    echo "  - Docker Ollama (CPU): 27s latency"
    echo "  - Native Ollama (Metal GPU): 1-2s latency (92% faster)"
    exit 1
fi

# 3. Define Model Unload Function
unload_ollama_models() {
    echo "🧠 Unloading Ollama models to free RAM..."

    # Unload models by setting keep_alive to 0 (immediate unload)
    curl -sf "$OLLAMA_URL/api/generate" -d "{\"model\": \"$EXTRACTION_MODEL\", \"keep_alive\": 0}" >/dev/null 2>&1 || true
    curl -sf "$OLLAMA_URL/api/embeddings" -d "{\"model\": \"$EMBEDDING_MODEL\", \"keep_alive\": 0}" >/dev/null 2>&1 || true

    echo "   ✅ Models unloaded: $EXTRACTION_MODEL, $EMBEDDING_MODEL"
}

# 4. Check if a model is already installed in Ollama
model_is_installed() {
    local requested_model="$1"
    local requested_base="${requested_model%%:*}"
    local installed_model

    while read -r installed_model _; do
        # Skip header/empty lines
        [ -z "$installed_model" ] && continue
        [ "$installed_model" = "NAME" ] && continue

        local installed_base="${installed_model%%:*}"
        if [ "$installed_model" = "$requested_model" ] || [ "$installed_base" = "$requested_base" ]; then
            return 0
        fi
    done < <(ollama list 2>/dev/null)

    return 1
}

# 5. Stop native Ollama server
stop_ollama_server() {
    if pgrep -f "ollama serve" >/dev/null 2>&1; then
        echo "🦙 Stopping Ollama server..."
        pkill -f "ollama serve" >/dev/null 2>&1 || true
        echo "   ✅ Ollama server stopped"
    else
        echo "   ℹ️  Ollama server not running"
    fi
}

# 4. Define Cleanup Function
shutdown_resources() {
    # Guard against duplicate trap execution (INT/TERM followed by EXIT)
    if [ "${CLEANUP_DONE}" -eq 1 ]; then
        return
    fi
    CLEANUP_DONE=1
    trap - EXIT INT TERM

    echo ""
    echo "🛑 Shutting down Smart Notebook..."

    if [ -n "$WORKER_PID" ] && kill -0 "$WORKER_PID" 2>/dev/null; then
        echo "🐍 Stopping ingestion worker (PID: $WORKER_PID)..."
        kill "$WORKER_PID" 2>/dev/null || true
    fi

    # Unload models before stopping (frees RAM faster)
    unload_ollama_models

    # Stop Ollama server to release memory/CPU
    stop_ollama_server

    echo "🐳 Stopping PostgreSQL container..."
    docker compose stop postgres >/dev/null 2>&1 || true

    echo "✅ System Cleaned. Resources released."
}

# 6. Register the trap to catch Exit and Ctrl+C
trap shutdown_resources EXIT INT TERM

# 7. Start Docker services (Postgres only - Ollama is native)
echo "🚀 Starting Smart Notebook..."
echo "   - PostgreSQL: Docker container"
echo "   - Ollama: Native installation (Metal GPU)"
echo "   - Press Ctrl+C to stop everything"

echo "🐳 Starting PostgreSQL..."
docker compose up -d postgres

# 8. Start native Ollama if not running
if ! curl -sf "$OLLAMA_URL/api/tags" >/dev/null 2>&1; then
    echo "🦙 Starting Ollama server..."
    ollama serve > /dev/null 2>&1 &
    OLLAMA_PID=$!
    echo "   - Ollama PID: $OLLAMA_PID"

    # Wait for Ollama to become ready
    echo "⏳ Waiting for Ollama..."
    for i in {1..30}; do
        if curl -sf "$OLLAMA_URL/api/tags" >/dev/null 2>&1; then
            break
        fi
        sleep 1
        if [ "$i" -eq 30 ]; then
            echo "❌ Ollama did not start in time"
            exit 1
        fi
    done
    echo "   ✅ Ollama ready (Metal GPU enabled)"
else
    echo "✅ Ollama already running"
fi

# 9. Pre-load models (warm startup - eliminates first-query latency)
echo "🧠 Pre-loading models for instant first query..."
echo "   - Extraction: $EXTRACTION_MODEL"
echo "   - Embedding: $EMBEDDING_MODEL"

# Check if models are installed, pull if needed
if model_is_installed "$EXTRACTION_MODEL"; then
    echo "   ✅ $EXTRACTION_MODEL already installed"
else
    echo "   📥 Pulling $EXTRACTION_MODEL..."
    ollama pull "$EXTRACTION_MODEL"
fi

if model_is_installed "$EMBEDDING_MODEL"; then
    echo "   ✅ $EMBEDDING_MODEL already installed"
else
    echo "   📥 Pulling $EMBEDDING_MODEL..."
    ollama pull "$EMBEDDING_MODEL"
fi

# Warm up extraction model (keep loaded for 5 minutes idle timeout)
curl -sf "$OLLAMA_URL/api/generate" -d "{
    \"model\": \"$EXTRACTION_MODEL\",
    \"prompt\": \"Hello\",
    \"keep_alive\": \"5m\",
    \"options\": {\"num_predict\": 1}
}" >/dev/null 2>&1 && echo "   ✅ $EXTRACTION_MODEL loaded (Metal GPU)" || echo "   ⚠️  $EXTRACTION_MODEL warmup failed"

# Warm up embedding model
curl -sf "$OLLAMA_URL/api/embeddings" -d "{
    \"model\": \"$EMBEDDING_MODEL\",
    \"prompt\": \"warmup\",
    \"keep_alive\": \"5m\"
}" >/dev/null 2>&1 && echo "   ✅ $EMBEDDING_MODEL loaded" || echo "   ⚠️  $EMBEDDING_MODEL warmup failed"

# 10. Start Python worker
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

# 11. Start Spring Boot
./kill_port.sh
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  Performance: Native Ollama + Metal GPU"
echo "  Expected latency: 1-2s (was 27s with Docker/CPU)"
echo "═══════════════════════════════════════════════════════════════"
echo ""
./mvnw spring-boot:run
