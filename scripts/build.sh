#!/bin/bash
set -e

# Change to project root
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

# Use JAVA_HOME from environment or fallback to Homebrew location
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -d "$JAVA_HOME" ]; then
    echo "❌ Error: Java 21 not found at $JAVA_HOME"
    echo "Set JAVA_HOME environment variable or install: brew install openjdk@21"
    exit 1
fi

echo "============================================="
echo "Building Smart Notebook with Java 21"
echo "JAVA_HOME: $JAVA_HOME"
echo "Java Version: $(java -version 2>&1 | head -n 1)"
echo "Maven Version: $(./mvnw -version 2>&1 | head -n 1)"
echo "============================================="

./mvnw clean install
