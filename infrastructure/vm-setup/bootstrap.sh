#!/usr/bin/env bash
# =============================================================================
# bootstrap.sh — Post-install setup for the banking-platform deploy VM
# Ubuntu 24.04 LTS
#
# Run as root on the fresh VM:
#   curl -fsSL https://raw.githubusercontent.com/theoduluard/banking-platform/main/infrastructure/vm-setup/bootstrap.sh | sudo bash
#
# What this script does:
#   1. System update
#   2. Install Docker Engine + Compose plugin (official Docker repo)
#   3. Create a dedicated "deploy" user with passwordless sudo for docker
#   4. Configure UFW firewall (SSH + app ports only)
#   5. Print next steps (SSH key setup)
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }

# ── 0. Verify we are root ─────────────────────────────────────────────────────
if [[ $EUID -ne 0 ]]; then
  echo -e "${RED}[ERROR]${NC} Run this script as root (sudo bash bootstrap.sh)"
  exit 1
fi

DEPLOY_USER="deploy"
DEPLOY_PATH="/opt/banking-platform"

info "Starting Solaris Bank VM bootstrap..."
echo ""

# ── 1. System update ──────────────────────────────────────────────────────────
info "Updating system packages..."
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq \
  ca-certificates curl gnupg lsb-release ufw git
success "System updated."

# ── 2. Install Docker Engine (official repo) ──────────────────────────────────
info "Installing Docker Engine..."

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update -qq
apt-get install -y -qq \
  docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin

systemctl enable --now docker
success "Docker $(docker --version) installed."

# ── 3. Create deploy user ──────────────────────────────────────────────────────
info "Creating user '${DEPLOY_USER}'..."

if id "${DEPLOY_USER}" &>/dev/null; then
  warn "User '${DEPLOY_USER}' already exists — skipping creation."
else
  useradd -m -s /bin/bash "${DEPLOY_USER}"
  success "User '${DEPLOY_USER}' created."
fi

# Add to docker group (no sudo needed for docker commands)
usermod -aG docker "${DEPLOY_USER}"
success "User '${DEPLOY_USER}' added to docker group."

# ── 4. Create deploy directory ────────────────────────────────────────────────
info "Creating deploy directory at ${DEPLOY_PATH}..."
mkdir -p "${DEPLOY_PATH}"
chown "${DEPLOY_USER}:${DEPLOY_USER}" "${DEPLOY_PATH}"
success "Deploy directory ready."

# ── 5. Copy docker-compose.prod.yml ──────────────────────────────────────────
info "You will need to copy infrastructure/docker-compose.prod.yml to ${DEPLOY_PATH}/"
info "and create a ${DEPLOY_PATH}/.env file with your secrets."
echo ""
cat <<ENV_TEMPLATE
  # Template for ${DEPLOY_PATH}/.env
  # ─────────────────────────────────
  DB_PASSWORD=<strong_password_here>
  JWT_SECRET=<base64_256bit_key>        # openssl rand -base64 32
  JWT_EXPIRATION=86400000
  JWT_REFRESH_EXPIRATION=604800000
ENV_TEMPLATE
echo ""

# ── 6. UFW Firewall ───────────────────────────────────────────────────────────
info "Configuring UFW firewall..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow ssh          # port 22 — GitHub Actions deploy
ufw allow 8080/tcp     # api-gateway (optional: remove if behind reverse proxy)
ufw --force enable
success "UFW configured. Active rules:"
ufw status numbered

# ── 7. SSH key placeholder for deploy user ────────────────────────────────────
info "Setting up SSH authorized_keys for '${DEPLOY_USER}'..."
DEPLOY_SSH_DIR="/home/${DEPLOY_USER}/.ssh"
mkdir -p "${DEPLOY_SSH_DIR}"
touch "${DEPLOY_SSH_DIR}/authorized_keys"
chmod 700 "${DEPLOY_SSH_DIR}"
chmod 600 "${DEPLOY_SSH_DIR}/authorized_keys"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${DEPLOY_SSH_DIR}"
success "SSH directory ready — add your GitHub Actions public key next (see below)."

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo -e "  ${GREEN}VM bootstrap complete!${NC}"
echo "============================================================"
echo ""
echo "  Next steps (run on your local machine):"
echo ""
echo "  1. Generate an SSH key pair for GitHub Actions:"
echo "     ssh-keygen -t ed25519 -C 'github-actions-deploy' -f ~/.ssh/banking_deploy"
echo ""
echo "  2. Copy the public key to this VM:"
echo "     ssh-copy-id -i ~/.ssh/banking_deploy.pub ${DEPLOY_USER}@<VM_IP>"
echo "     # or manually: cat ~/.ssh/banking_deploy.pub >> ${DEPLOY_SSH_DIR}/authorized_keys"
echo ""
echo "  3. Test the connection:"
echo "     ssh -i ~/.ssh/banking_deploy ${DEPLOY_USER}@<VM_IP>"
echo ""
echo "  4. Add these GitHub repository secrets (Settings → Secrets → Actions):"
echo "     NAS_HOST        = <VM_IP or DDNS>"
echo "     NAS_USER        = ${DEPLOY_USER}"
echo "     NAS_SSH_KEY     = \$(cat ~/.ssh/banking_deploy)   ← private key content"
echo "     NAS_PORT        = 22"
echo "     NAS_DEPLOY_PATH = ${DEPLOY_PATH}"
echo "     GHCR_TOKEN      = <GitHub PAT with read:packages scope>"
echo ""
echo "  5. Copy docker-compose.prod.yml and create .env on the VM:"
echo "     scp infrastructure/docker-compose.prod.yml ${DEPLOY_USER}@<VM_IP>:${DEPLOY_PATH}/"
echo "     ssh ${DEPLOY_USER}@<VM_IP> 'nano ${DEPLOY_PATH}/.env'"
echo ""
echo "  6. Push to main → GitHub Actions will deploy automatically."
echo "============================================================"
