#!/bin/bash
# GitHub Issues MCP Server launcher

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR_PATH="$SCRIPT_DIR/build/libs/github-issues-mcp-1.0.0-all.jar"

# Load configuration from .env if exists
if [ -f "$PROJECT_DIR/.env" ]; then
    set -a
    source "$PROJECT_DIR/.env"
    set +a
fi

# Defaults
GITHUB_OWNER="${GITHUB_OWNER:-alphapaca}"
GITHUB_REPO="${GITHUB_REPO:-ClaudeClient}"

# Build JAR if needed
if [ ! -f "$JAR_PATH" ] || [ "$1" = "--rebuild" ]; then
    echo "Building github-issues-mcp..." >&2
    "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" :github-issues-mcp:jar -q
fi

if [ -z "$GITHUB_TOKEN" ]; then
    echo "Error: GITHUB_TOKEN not set. Add it to .env or export it." >&2
    exit 1
fi

exec env GITHUB_TOKEN="$GITHUB_TOKEN" GITHUB_OWNER="$GITHUB_OWNER" GITHUB_REPO="$GITHUB_REPO" \
    java -jar "$JAR_PATH"
