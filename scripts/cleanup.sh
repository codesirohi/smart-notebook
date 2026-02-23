#!/bin/bash

# ==================================================================================
# Smart Notebook - Manual Cleanup Script
# ==================================================================================
# Purpose: Forcefully stops all related containers and cleans up Docker resources.
# Use this if the application didn't close properly or to free up disk/RAM.
# ==================================================================================

# Change to project root
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

echo "🧹 Starting Cleanup..."

# 1. Stop and Remove Containers defined in docker-compose.yml
if [ -f "docker-compose.yml" ] || [ -f "compose.yaml" ]; then
    echo "🐳 Stopping Docker Compose services..."
    docker compose down --remove-orphans
else
    echo "⚠️ docker-compose.yml not found. Skipping docker compose down."
fi

# 2. Force kill any lingering containers related to 'smart-notebook'
# This finds containers with names containing "smart-notebook" and kills them
echo "🔍 Checking for lingering containers..."
LINGERING=$(docker ps -a -q -f name=smart-notebook)
if [ -n "$LINGERING" ]; then
    echo "   Found lingering containers. Removing..."
    docker rm -f $LINGERING
else
    echo "   No lingering containers found."
fi

# 3. Optional: Prune Docker System (Frees disk space)
# We won't run 'prune -a' automatically as it deletes images, but we will prune stopped containers/networks
echo "🛁 Pruning stopped containers and unused networks..."
docker container prune -f
docker network prune -f

# 4. Show Disk Usage
echo ""
echo "📊 Current Docker Disk Usage:"
docker system df

# 5. Clean uploads directory (local environment only)
if [ "$ENV" = "local" ] || [ "$ENVIRONMENT" = "local" ]; then
    echo "🗑️  Cleaning uploads directory..."
    rm -f "$PROJECT_ROOT"/uploads/*
fi

echo ""
echo "✅ Cleanup Complete! RAM and Disk resources freed."
