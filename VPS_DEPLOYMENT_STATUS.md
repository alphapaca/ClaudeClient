# VPS Deployment Status

## 🚀 Automated Deployment Available

The web-agent now deploys automatically via **GitHub Actions** when code is pushed to the `main` branch.

### Access URLs
- **Web Frontend**: http://<VPS_IP>:8080
- **Health Check**: http://<VPS_IP>:8080/api/health
- **Q&A API**: POST http://<VPS_IP>:8080/api/ask
- **GitHub Actions**: [Deployment Workflow](../../actions/workflows/deploy-web-agent.yml)

> **Note:** Replace `<VPS_IP>` with your VPS IP address (configured in GitHub Secrets as `VPS_HOST`)

---

## Automated Deployment Process

### Overview

The web-agent automatically deploys to VPS when code changes are pushed to the `main` branch.

**Deployment Pipeline:**
1. **Build Stage**: Builds Docker image on GitHub runners → pushes to GHCR
2. **Approval Gate**: Manual approval required via GitHub Environment `production`
3. **Deploy Stage**: SSH to VPS → pull image → restart container
4. **Verify Stage**: Health checks with automatic rollback on failure
5. **Cleanup Stage**: Remove old images (keep last 3 versions)

### Triggered By

Automatic deployment when pushing to `main` with changes to:
- `web-agent-backend/**`
- `web-agent-frontend/**`
- `github-issues-mcp/**`
- `embedding-indexer/**`
- `Dockerfile`
- `docker-compose.yaml`

**Manual trigger:** Actions tab → "Deploy Web Agent to VPS" → Run workflow

### Approval Workflow

**Manual approval required before deployment:**
1. Build completes successfully
2. GitHub sends notification: "Deployment waiting for approval"
3. Reviewer clicks "Review deployments" → "Approve and deploy"
4. Deploy stage executes immediately
5. Can reject to cancel deployment

**Configured in:** Settings → Environments → `production`

### Image Registry

Images stored in **GitHub Container Registry** (ghcr.io):
- `ghcr.io/alphapaca/claudeclient/web-agent:main` (latest main build)
- `ghcr.io/alphapaca/claudeclient/web-agent:main-<sha>` (specific commit)
- `ghcr.io/alphapaca/claudeclient/web-agent:latest` (always latest)

**Why GHCR?**
- Faster than scp transfer (300MB+ tar.gz)
- Better caching and build performance
- Version history for rollbacks
- Industry standard

### Automatic Rollback

If deployment fails (container won't start or health check fails):
1. Stop failed container
2. Identify previous working image
3. Start previous version automatically
4. Preserve vector database (no data loss)

### Setup Required

**First-time setup:** See [DEPLOYMENT_SETUP.md](./DEPLOYMENT_SETUP.md)

**GitHub Secrets required:**
- `VPS_SSH_PRIVATE_KEY` - SSH private key for VPS access
- `VPS_HOST` - VPS IP address
- `VPS_USER` - SSH username
- `ANTHROPIC_API_KEY` - Claude API key
- `VOYAGEAI_API_KEY` - VoyageAI embeddings key

### Monitoring Deployments

**Check deployment status:**
```bash
# View GitHub Actions workflow runs
# Go to: https://github.com/<owner>/<repo>/actions

# Check container on VPS
ssh <VPS_USER>@<VPS_IP> "docker ps | grep web-agent"

# View container logs
ssh <VPS_USER>@<VPS_IP> "docker logs web-agent --tail 50"

# Health check
curl http://<VPS_IP>:8080/api/health
```

**Expected health response:**
```json
{"status":"ok","codeChunksIndexed":564}
```

---

## Current Status
- Container: `web-agent` running with `claudeclient-web-agent:amd64` image
- Code chunks indexed: 564
- VoyageAI: Working without SOCKS proxy from VPS
- GitHub Issues MCP: 4 tools available (search, get, list, create)
- **Web Frontend**: ✅ Working with gzip compression enabled

### Web Frontend Issue - ✅ FULLY RESOLVED

**Original Problem:** Large WASM file (e9b28911e687b1ee6b42.wasm, 8.3MB) returned 0 bytes.

**Fixes Applied:** ✅ Complete
1. **Streaming fix**: Replaced `resource.readBytes()` with `respondOutputStream()` in `Routes.kt:196`
2. **Timeout increase**: Added Netty timeout configuration (60s for request read/write) in `Main.kt:158-173`
3. **Compression**: Added gzip compression for static assets in `Main.kt:198-213`

```kotlin
// Streaming fix in Routes.kt:
call.respondOutputStream(contentType) {
    resource.openStream().use { inputStream ->
        inputStream.copyTo(this)
    }
}

// Timeout fix in Main.kt:
embeddedServer(Netty, configure = {
    requestReadTimeoutSeconds = 60
    responseWriteTimeoutSeconds = 60
    connector { port = config.port }
})

// Compression fix in Main.kt:
install(Compression) {
    gzip {
        priority = 1.0
        matchContentType(
            ContentType.Application.JavaScript,
            ContentType("application", "wasm"),
            ContentType.Text.Html,
            ContentType.Text.CSS,
            ContentType.Application.Json
        )
        minimumSize(1024)
    }
}
```

**Final Status:** ✅ WORKING PERFECTLY
- **Frontend loads successfully** - No more infinite spinner
- **All WASM files load** - Including the 8.3MB Skiko file
- **Compression enabled** - Response headers show `content-encoding: gzip`
- **UI fully functional** - Can ask questions about the codebase
- **564 code chunks indexed** - Vector search ready

**Performance Impact:**
- WASM files compressed with gzip before transmission
- Estimated reduction: 8.3MB → ~2-3MB (60-70% smaller)
- Loads successfully over 0.72 MB/s VPS connection
- Total page load time: ~60 seconds (acceptable for first load)

**Build Performance Improvements:** ⚡
- Docker build with cache: ~5 minutes (vs 10+ minutes without)
- Only changed layers rebuilt
- Significant time savings on iterative deployments

**Architecture Insight Confirmed:**
- ARM64 (local Mac) builds produce different WASM hashes than AMD64 (Docker/VPS)
- `13f4f7af26d86354f076.wasm` = ARM64 build
- `eb72242eb77a047d6a1f.wasm` = AMD64 build
- Both are same source, different architectures

### API Test
```bash
curl -s -X POST "http://<VPS_IP>:8080/api/ask" \
  -H "Content-Type: application/json" \
  -d '{"question": "How does conversation persistence work?"}' | jq '.'
```

### Container Management
```bash
# SSH to VPS (use your SSH key or credentials)
ssh <VPS_USER>@<VPS_IP>

# Check container status
docker ps
docker logs web-agent

# Restart container
docker restart web-agent

# Stop/remove container
docker stop web-agent && docker rm web-agent
```

### Docker Image Info (Automated Deployment)
- Registry image: `ghcr.io/alphapaca/claudeclient/web-agent:main`
- Built on: GitHub Actions runners (linux/amd64)
- Deployed via: GitHub Container Registry pull
- Vector database: `/data/vectors` (persistent Docker volume)

---

## 📦 Manual Deployment Process (DEPRECATED)

> **Note:** This process is deprecated in favor of automated GitHub Actions deployment.
> Kept here for reference and emergency fallback.

### Original Manual Process

1. **Build locally:**
   ```bash
   docker buildx build --platform linux/amd64 -t claudeclient-web-agent:amd64 .
   ```

2. **Save and compress:**
   ```bash
   docker save claudeclient-web-agent:amd64 | gzip > web-agent-amd64.tar.gz
   ```

3. **Transfer to VPS:**
   ```bash
   scp web-agent-amd64.tar.gz <VPS_USER>@<VPS_IP>:/root/
   ```

4. **Deploy on VPS:**
   ```bash
   ssh <VPS_USER>@<VPS_IP>
   docker load < /root/web-agent-amd64.tar.gz
   docker stop web-agent && docker rm web-agent
   docker run -d --name web-agent --restart unless-stopped \
     -p 8080:8080 \
     -v web-agent-vectors:/data/vectors \
     [environment variables...] \
     claudeclient-web-agent:amd64
   ```

### When to Use Manual Deployment

Only use manual deployment if:
- GitHub Actions is down or unavailable
- Emergency hotfix needed immediately
- Testing before pushing to main branch
- Debugging deployment issues

### Local Image Info (for reference)
- Local image tag: `claudeclient-web-agent:amd64`
- Build platform: Cross-compiled for linux/amd64
- Transfer method: scp (deprecated)
