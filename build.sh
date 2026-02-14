#!/bin/bash
set -e

# Force Java 21 environment
export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
export PATH="$JAVA_HOME/bin:$PATH"

echo "============================================="
echo "Building Smart Notebook with Java 21"
echo "JAVA_HOME: $JAVA_HOME"
echo "Java Version: $(java -version 2>&1 | head -n 1)"
echo "Maven Version: $(./mvnw -version 2>&1 | head -n 1)"
echo "============================================="

./mvnw clean install
