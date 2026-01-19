# Multi-stage Dockerfile for Web Agent
# Builds: web-agent-frontend (WASM) + web-agent-backend + github-issues-mcp

# =============================================================================
# Stage 1: Build
# =============================================================================
FROM gradle:8.10-jdk17 AS builder

WORKDIR /app

# Copy Gradle configuration first (for better layer caching)
COPY gradle/ gradle/
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./

# Copy source code
COPY composeApp/ composeApp/
COPY embedding-indexer/ embedding-indexer/
COPY github-issues-mcp/ github-issues-mcp/
COPY web-agent-backend/ web-agent-backend/
COPY web-agent-frontend/ web-agent-frontend/

# Build frontend WASM distribution
RUN ./gradlew :web-agent-frontend:wasmJsBrowserDistribution --no-daemon --info

# Copy frontend to backend resources
RUN ./gradlew :web-agent-backend:copyFrontend --no-daemon

# Build backend fat JAR (includes frontend)
RUN ./gradlew :web-agent-backend:jar --no-daemon

# Build GitHub Issues MCP fat JAR
RUN ./gradlew :github-issues-mcp:jar --no-daemon

# =============================================================================
# Stage 2: Runtime
# =============================================================================
FROM eclipse-temurin:17-jre AS runtime

# Install dumb-init for proper signal handling (required for subprocess management)
RUN apt-get update && apt-get install -y --no-install-recommends dumb-init wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Build sqlite-vec extension from source (release binaries have arch issues)
ARG SQLITE_VEC_VERSION=0.1.6
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    gettext-base \
    libsqlite3-dev \
    && rm -rf /var/lib/apt/lists/* \
    && git clone --depth 1 --branch v${SQLITE_VEC_VERSION} https://github.com/asg017/sqlite-vec.git /tmp/sqlite-vec \
    && cd /tmp/sqlite-vec \
    && make loadable \
    && mkdir -p /usr/lib/sqlite-vec /usr/local/lib/sqlite-vec \
    && cp dist/vec0.so /usr/lib/sqlite-vec/ \
    && cp dist/vec0.so /usr/local/lib/sqlite-vec/ \
    && chmod 755 /usr/lib/sqlite-vec/vec0.so /usr/local/lib/sqlite-vec/vec0.so \
    && rm -rf /tmp/sqlite-vec \
    && apt-get purge -y --auto-remove build-essential git \
    && echo "sqlite-vec built from source"

# Create non-root user for security
RUN groupadd -r webagent && useradd -r -g webagent webagent

WORKDIR /app

# Copy built JARs from builder stage
COPY --from=builder /app/web-agent-backend/build/libs/web-agent-backend-1.0.0-all.jar /app/web-agent-backend.jar
COPY --from=builder /app/github-issues-mcp/build/libs/github-issues-mcp-1.0.0-all.jar /app/github-issues-mcp.jar

# Create data directory for vector DB persistence
RUN mkdir -p /data/vectors && chown -R webagent:webagent /data

# Switch to non-root user
USER webagent

# Environment variables
ENV PORT=8080 \
    GITHUB_ISSUES_MCP_JAR=/app/github-issues-mcp.jar \
    VECTOR_DB_PATH=/data/vectors/code-vectors.db \
    CLAUDE_MD_PATH=/app/CLAUDE.md \
    PROJECT_PATH=/app \
    AUTO_INDEX=true \
    LLM_MODEL=claude-sonnet-4 \
    MAX_TOKENS=4096 \
    TEMPERATURE=0.7 \
    MAX_AGENT_ITERATIONS=10

# JVM container-aware settings
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

# Use dumb-init as PID 1 for proper signal handling
ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/web-agent-backend.jar"]
