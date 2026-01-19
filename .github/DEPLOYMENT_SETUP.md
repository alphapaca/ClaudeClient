# GitHub Actions Deployment Setup Guide

This guide walks you through setting up automated deployment of the web-agent to your VPS using GitHub Actions.

## Prerequisites

- VPS with SSH access
- GitHub repository with admin access
- Required API keys (Anthropic, VoyageAI)

## Setup Steps

### 1. Generate SSH Key for GitHub Actions

On your local machine, generate a dedicated SSH key pair for GitHub Actions:

```bash
# Generate ED25519 key (more secure and faster than RSA)
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/github-actions-deploy -N ""
```

This creates two files:
- `~/.ssh/github-actions-deploy` (private key) - will be added to GitHub Secrets
- `~/.ssh/github-actions-deploy.pub` (public key) - will be added to VPS

### 2. Add Public Key to VPS

Copy the public key to your VPS authorized_keys:

```bash
# Automated method
ssh-copy-id -i ~/.ssh/github-actions-deploy.pub <VPS_USER>@<VPS_IP>

# Or manual method
cat ~/.ssh/github-actions-deploy.pub | ssh <VPS_USER>@<VPS_IP> "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```

### 3. Test SSH Connection

Verify the key works:

```bash
ssh -i ~/.ssh/github-actions-deploy <VPS_USER>@<VPS_IP> "echo 'SSH authentication successful!'"
```

Expected output: `SSH authentication successful!`

### 4. Configure GitHub Secrets

Go to your repository on GitHub:

**Settings → Secrets and variables → Actions → New repository secret**

Add the following secrets:

| Secret Name | Value | How to Get |
|-------------|-------|------------|
| `VPS_SSH_PRIVATE_KEY` | Full contents of `~/.ssh/github-actions-deploy` | `cat ~/.ssh/github-actions-deploy` |
| `VPS_HOST` | Your VPS IP address | Example: `123.45.67.89` |
| `VPS_USER` | Your SSH username | Example: `root` or `ubuntu` |
| `ANTHROPIC_API_KEY` | Your Claude API key | From Anthropic Console |
| `VOYAGEAI_API_KEY` | Your VoyageAI API key | From VoyageAI Dashboard |

**Important for `VPS_SSH_PRIVATE_KEY`:**
- Copy the **entire** file content including `-----BEGIN OPENSSH PRIVATE KEY-----` and `-----END OPENSSH PRIVATE KEY-----` lines
- Preserve all line breaks
- Do not add extra spaces or newlines

Example command to copy to clipboard:
```bash
# macOS
cat ~/.ssh/github-actions-deploy | pbcopy

# Linux with xclip
cat ~/.ssh/github-actions-deploy | xclip -selection clipboard
```

### 5. Create GitHub Environment for Manual Approval

This step enables manual approval before deployments run.

1. Go to **Settings → Environments** in your repository
2. Click **New environment**
3. Name: `production`
4. Click **Configure environment**
5. Enable **Required reviewers**
6. Add yourself (or team members) as reviewers
7. Optional: Set **Wait timer** (e.g., 5 minutes before allowing approval)
8. Click **Save protection rules**

**How it works:**
- After the build stage completes, the workflow pauses
- GitHub sends a notification: "Deployment waiting for approval"
- You review the changes and click "Review deployments" → "Approve and deploy"
- The deploy stage runs immediately after approval
- You can also reject to cancel the deployment

### 6. Verify GitHub Container Registry Permissions

The workflow uses GitHub Container Registry (GHCR) to store Docker images.

1. Go to **Settings → Actions → General**
2. Scroll to **Workflow permissions**
3. Ensure **Read and write permissions** is selected
4. Check **Allow GitHub Actions to create and approve pull requests** (optional)
5. Click **Save**

## Testing the Setup

### Test 1: Manual Workflow Trigger

1. Go to **Actions** tab in your repository
2. Select **Deploy Web Agent to VPS** workflow
3. Click **Run workflow** button
4. Select `main` branch
5. Click **Run workflow**
6. Monitor the execution:
   - Build stage should complete successfully
   - Workflow pauses waiting for approval
   - Click **Review deployments** → **Approve and deploy**
   - Deploy, verify, and cleanup stages should complete
7. Check the deployment:
   - Visit http://<VPS_IP>:8080
   - Frontend should load
   - Health check: `curl http://<VPS_IP>:8080/api/health`

### Test 2: Automatic Trigger on Push

1. Make a small change to any monitored file:
   ```bash
   echo "# Test deployment" >> web-agent-backend/README.md
   git add web-agent-backend/README.md
   git commit -m "Test: Trigger automated deployment"
   git push origin main
   ```
2. Go to **Actions** tab
3. Workflow should start automatically
4. Follow approval and verification steps as above

### Test 3: Health Check Verification

After successful deployment:

```bash
# Check health endpoint
curl http://<VPS_IP>:8080/api/health

# Expected response:
# {"status":"ok","codeChunksIndexed":564}

# Check container status via SSH
ssh <VPS_USER>@<VPS_IP> "docker ps | grep web-agent"

# Check container logs
ssh <VPS_USER>@<VPS_IP> "docker logs web-agent --tail 50"
```

### Test 4: Rollback Mechanism

Test the automatic rollback by intentionally breaking the build:

1. Add a syntax error to `Dockerfile`:
   ```dockerfile
   # Add this invalid line somewhere
   INVALID_INSTRUCTION this will fail
   ```
2. Commit and push to main
3. Workflow should fail at build stage
4. Check that the old container is still running:
   ```bash
   curl http://<VPS_IP>:8080/api/health
   ```

## Workflow Stages Explained

### 1. Build Stage
- Runs on every push to main (if monitored files changed)
- Builds Docker image for linux/amd64 platform
- Pushes to GitHub Container Registry with tags:
  - `main` (latest main branch build)
  - `main-<commit-sha>` (specific commit)
  - `latest` (always latest successful build)
- Uses GitHub Actions cache for faster builds

### 2. Deploy Stage (Requires Approval)
- **Waits for manual approval** via GitHub Environment
- Connects to VPS via SSH using private key
- Logs in to GitHub Container Registry on VPS
- Pulls new Docker image
- Stops old container gracefully
- Creates/verifies vector database volume
- Starts new container with all environment variables
- Waits 15 seconds for initialization

### 3. Verify Stage
- Waits additional 15 seconds for app startup
- Performs health check with 20 retries (15s intervals)
- Verifies container is running with new image
- Total timeout: 300 seconds (5 minutes)

### 4. Rollback Stage (On Failure Only)
- Automatically triggered if deploy or verify fails
- Stops failed container
- Identifies previous working image
- Starts previous version with same configuration
- Preserves vector database (no data loss)

### 5. Cleanup Stage (On Success Only)
- Removes old Docker images from VPS
- Keeps last 3 versions for rollback capability
- Frees disk space

## Environment Variables

The following environment variables are passed to the container:

```bash
ANTHROPIC_API_KEY      # Claude API key (from secrets)
VOYAGE_API_KEY         # VoyageAI embeddings key (from secrets)
GITHUB_TOKEN           # GitHub token for API access (from secrets)
GITHUB_OWNER=alphapaca # Repository owner
GITHUB_REPO=ClaudeClient # Repository name
LLM_MODEL=claude-sonnet-4 # Claude model to use
MAX_TOKENS=4096        # Max response tokens
TEMPERATURE=0.7        # LLM temperature
MAX_AGENT_ITERATIONS=10 # Max RAG iterations
AUTO_INDEX=true        # Auto-index codebase (skipped if DB exists)
ENABLE_CODE_SEARCH=true # Enable semantic code search
ENABLE_ISSUE_SEARCH=true # Enable GitHub issues search
PORT=8080              # Server port
VECTOR_DB_PATH=/data/vectors/code-vectors.db # Vector DB location
GITHUB_ISSUES_MCP_JAR=/app/github-issues-mcp.jar # MCP JAR path
```

## Volume Configuration

**Named Volume:** `web-agent-vectors`
- Mounted to: `/data/vectors` in container
- Contains vector database with code embeddings
- **Persists across deployments** - never deleted
- Current size: ~564 code chunks indexed

**AUTO_INDEX Behavior:**
- If vector database exists with data, indexing is skipped
- Only indexes on first deployment or if DB is empty
- Current VPS has existing DB, so deployments are fast (no reindexing)

## Troubleshooting

### Deployment Fails: "Permission denied (publickey)"

**Cause:** SSH key not properly configured

**Fix:**
1. Verify public key on VPS: `ssh <VPS_USER>@<VPS_IP> "cat ~/.ssh/authorized_keys"`
2. Check private key in GitHub secrets is complete (includes BEGIN/END lines)
3. Re-run ssh-copy-id command from Step 2

### Deployment Fails: "Error response from daemon: pull access denied"

**Cause:** VPS cannot authenticate to GitHub Container Registry

**Fix:**
1. Verify `GITHUB_TOKEN` secret is set correctly
2. Check workflow permissions in Settings → Actions → General
3. Ensure token has `packages: write` permission

### Health Check Times Out

**Cause:** Application taking longer than 5 minutes to start

**Fix:**
1. Check container logs: `ssh <VPS_USER>@<VPS_IP> "docker logs web-agent"`
2. If AUTO_INDEX=true is causing delay, wait for indexing to complete
3. Increase health check timeout in workflow (edit `MAX_RETRIES` or `RETRY_INTERVAL`)

### Container Fails to Start: "port is already allocated"

**Cause:** Another process using port 8080

**Fix:**
```bash
ssh <VPS_USER>@<VPS_IP>
lsof -i :8080  # Find process using port
kill -9 <PID>  # Kill the process
# Or change PORT environment variable in workflow
```

### Rollback Fails: "No previous image found"

**Cause:** First deployment or all images cleaned up

**Fix:**
1. This is expected on first deployment
2. Fix the current deployment issue manually
3. Subsequent deployments will have previous images for rollback

### Out of Disk Space

**Cause:** Too many old Docker images

**Fix:**
```bash
ssh <VPS_USER>@<VPS_IP>
docker system df  # Check disk usage
docker system prune -a -f  # Remove all unused images
docker volume prune -f  # Remove unused volumes (careful!)
```

### Frontend Shows Infinite Spinner

**Cause:** Backend not responding or CORS issue

**Fix:**
1. Check health endpoint: `curl http://<VPS_IP>:8080/api/health`
2. Check container logs: `docker logs web-agent --tail 100`
3. Verify API keys are valid in secrets
4. Check browser console for errors

## Monitoring

### View Deployment History

1. Go to **Actions** tab in repository
2. Select **Deploy Web Agent to VPS** workflow
3. View past runs and their logs

### Check Current Deployment

```bash
# Container status
ssh <VPS_USER>@<VPS_IP> "docker ps --filter name=web-agent"

# Container logs (live)
ssh <VPS_USER>@<VPS_IP> "docker logs -f web-agent"

# Health check
curl http://<VPS_IP>:8080/api/health

# Vector database status
ssh <VPS_USER>@<VPS_IP> "docker exec web-agent ls -lh /data/vectors/"
```

### Metrics to Watch

- **Build time:** Should be ~3-5 minutes with cache
- **Deploy time:** ~30 seconds (pull + restart)
- **Health check:** Should pass within 30 seconds (or 3-5 minutes if indexing)
- **Image size:** ~300-400 MB

## Security Considerations

1. **SSH Key:** Dedicated key used only for deployments
2. **API Keys:** Stored as encrypted GitHub Secrets
3. **GHCR:** Images are private (require authentication)
4. **Approval:** Manual approval required before deployment
5. **Rollback:** Automatic on failure (no downtime)

## Cleanup Old SSH Keys (Optional)

After deployment is working, you can optionally remove the test SSH key from your local machine:

```bash
# Remove local key files
rm ~/.ssh/github-actions-deploy
rm ~/.ssh/github-actions-deploy.pub

# Key remains on VPS for GitHub Actions to use
```

## Next Steps

1. ✅ Complete all setup steps above
2. ✅ Run Test 1 (manual trigger)
3. ✅ Verify deployment successful
4. ✅ Run Test 2 (automatic trigger)
5. ✅ Monitor a few deployments
6. ✅ Document any custom configurations
7. ✅ Set up monitoring/alerting (optional)

## Support

If you encounter issues:

1. Check the troubleshooting section above
2. Review GitHub Actions logs (Actions tab)
3. Check VPS logs: `ssh <VPS_USER>@<VPS_IP> "docker logs web-agent"`
4. Verify all secrets are configured correctly
5. Test SSH connection manually

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [GitHub Environments](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment)
- [Docker Documentation](https://docs.docker.com/)
