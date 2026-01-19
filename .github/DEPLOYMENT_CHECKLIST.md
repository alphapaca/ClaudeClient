# GitHub Actions Deployment - Quick Start Checklist

Complete these steps to enable automated deployment to your VPS.

## Prerequisites

- [ ] VPS with SSH access
- [ ] GitHub repository with admin access
- [ ] Required API keys (Anthropic, VoyageAI)

> **Note:** Throughout this guide, replace `<VPS_IP>` with your VPS IP address and `<VPS_USER>` with your SSH username.

---

## Setup Steps

### 1. Generate SSH Key

```bash
# Generate dedicated SSH key for GitHub Actions
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/github-actions-deploy -N ""
```

- [ ] Key generated successfully

### 2. Add Public Key to VPS

```bash
# Copy public key to VPS
ssh-copy-id -i ~/.ssh/github-actions-deploy.pub <VPS_USER>@<VPS_IP>
```

- [ ] Public key added to VPS

### 3. Test SSH Connection

```bash
# Verify authentication works
ssh -i ~/.ssh/github-actions-deploy <VPS_USER>@<VPS_IP> "echo 'SSH works!'"
```

- [ ] SSH connection successful

### 4. Configure GitHub Secrets

Go to: **Repository Settings → Secrets and variables → Actions → New repository secret**

Add these 5 secrets:

| Secret Name | Command to Get Value | Status |
|-------------|---------------------|--------|
| `VPS_SSH_PRIVATE_KEY` | `cat ~/.ssh/github-actions-deploy` | [ ] |
| `VPS_HOST` | Your VPS IP address | [ ] |
| `VPS_USER` | Your SSH username | [ ] |
| `ANTHROPIC_API_KEY` | (from Anthropic Console) | [ ] |
| `VOYAGEAI_API_KEY` | (from VoyageAI Dashboard) | [ ] |

**Important:** For `VPS_SSH_PRIVATE_KEY`, copy the entire file including BEGIN/END lines.

```bash
# macOS - copy private key to clipboard
cat ~/.ssh/github-actions-deploy | pbcopy

# Linux - display private key
cat ~/.ssh/github-actions-deploy
```

- [ ] All 5 secrets configured

### 5. Create GitHub Environment

Go to: **Repository Settings → Environments**

1. Click **New environment**
2. Name: `production`
3. Click **Configure environment**
4. Enable **Required reviewers**
5. Add yourself as reviewer
6. Click **Save protection rules**

- [ ] Environment `production` created
- [ ] Reviewers configured

### 6. Verify Workflow Permissions

Go to: **Repository Settings → Actions → General**

Scroll to **Workflow permissions**:
- [ ] "Read and write permissions" selected
- [ ] Click **Save**

---

## Testing

### Test 1: Manual Trigger

1. Go to **Actions** tab in repository
2. Select **Deploy Web Agent to VPS**
3. Click **Run workflow** → Select `main` → **Run workflow**
4. Wait for build stage to complete
5. Click **Review deployments** → **Approve and deploy**
6. Verify all stages pass

- [ ] Build stage: ✅ Success
- [ ] Deploy stage: ✅ Success
- [ ] Verify stage: ✅ Success
- [ ] Cleanup stage: ✅ Success

### Test 2: Health Check

```bash
# Check application health
curl http://<VPS_IP>:8080/api/health

# Expected: {"status":"ok","codeChunksIndexed":564}
```

- [ ] Health endpoint responds correctly

### Test 3: Frontend Access

1. Open browser: http://<VPS_IP>:8080
2. Verify frontend loads (no infinite spinner)
3. Check "Connected (564 chunks indexed)" message
4. Try asking a question about the codebase

- [ ] Frontend loads successfully
- [ ] Q&A functionality works

### Test 4: Automatic Trigger

```bash
# Make a small change to trigger deployment
echo "# Test automated deployment" >> web-agent-backend/README.md
git add web-agent-backend/README.md
git commit -m "Test: Automated deployment trigger"
git push origin main
```

1. Go to **Actions** tab
2. Verify workflow started automatically
3. Approve deployment when prompted
4. Verify successful completion

- [ ] Workflow triggered automatically
- [ ] Deployment successful

---

## Verification Commands

```bash
# Check container status on VPS
ssh <VPS_USER>@<VPS_IP> "docker ps | grep web-agent"

# View container logs
ssh <VPS_USER>@<VPS_IP> "docker logs web-agent --tail 50"

# Check which image is running
ssh <VPS_USER>@<VPS_IP> "docker inspect web-agent | grep Image"

# List available images
ssh <VPS_USER>@<VPS_IP> "docker images | grep web-agent"
```

---

## Troubleshooting

### Issue: "Permission denied (publickey)"

**Fix:**
```bash
# Verify public key on VPS
ssh <VPS_USER>@<VPS_IP> "cat ~/.ssh/authorized_keys | grep github-actions"

# Re-add if missing
ssh-copy-id -i ~/.ssh/github-actions-deploy.pub <VPS_USER>@<VPS_IP>
```

### Issue: "pull access denied" on VPS

**Fix:**
- Verify `GITHUB_TOKEN` secret is set
- Check Settings → Actions → General → Workflow permissions
- Ensure "Read and write permissions" enabled

### Issue: Health check timeout

**Fix:**
```bash
# Check container logs for errors
ssh <VPS_USER>@<VPS_IP> "docker logs web-agent"

# Check if container is running
ssh <VPS_USER>@<VPS_IP> "docker ps -a | grep web-agent"

# Check port availability
ssh <VPS_USER>@<VPS_IP> "lsof -i :8080"
```

### Issue: Frontend shows infinite spinner

**Fix:**
- Wait 30 seconds for initialization
- Check browser console for errors
- Verify health endpoint: `curl http://<VPS_IP>:8080/api/health`
- Check container logs for startup errors

---

## Success Criteria

- [x] GitHub Actions workflow file created
- [ ] All GitHub secrets configured
- [ ] GitHub Environment `production` created with reviewers
- [ ] SSH authentication working
- [ ] Manual workflow trigger succeeds
- [ ] Automatic trigger on push to main works
- [ ] Health check passes
- [ ] Frontend accessible and functional
- [ ] Q&A functionality works

---

## Next Steps After Setup

1. **Monitor First Few Deployments**
   - Watch for any issues
   - Verify automatic rollback works if needed

2. **Document Custom Configurations**
   - Add team-specific notes to DEPLOYMENT_SETUP.md
   - Document any environment-specific settings

3. **Set Up Monitoring (Optional)**
   - Configure uptime monitoring (e.g., UptimeRobot)
   - Set up alerts for deployment failures

4. **Clean Up Local SSH Key (Optional)**
   ```bash
   # After verifying deployment works, optionally remove local copy
   rm ~/.ssh/github-actions-deploy
   rm ~/.ssh/github-actions-deploy.pub
   # Key remains on VPS for GitHub Actions to use
   ```

---

## Quick Reference

**Workflow file:** `.github/workflows/deploy-web-agent.yml`
**Setup guide:** `.github/DEPLOYMENT_SETUP.md`
**Status doc:** `VPS_DEPLOYMENT_STATUS.md`

**Deployment flow:**
1. Push to `main` → 2. Build → 3. Approval → 4. Deploy → 5. Verify → 6. Cleanup

**Manual trigger:** Actions tab → Deploy Web Agent to VPS → Run workflow

**Health check:** http://<VPS_IP>:8080/api/health

**Frontend:** http://<VPS_IP>:8080
