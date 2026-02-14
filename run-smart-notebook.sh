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

./mvnw spring-boot:run
