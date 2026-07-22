#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/cloudnotes}"
DEPLOY_USER="${DEPLOY_USER:-cloudnotes-deploy}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run this script as root, for example: sudo APP_DIR=$APP_DIR $0" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

echo "Updating system packages"
apt-get update
apt-get upgrade -y

echo "Installing Docker Engine, Compose plugin, AWS CLI, Certbot helpers, and deployment utilities"
apt-get install -y ca-certificates curl gnupg lsb-release awscli unzip jq gettext-base logrotate
install -m 0755 -d /etc/apt/keyrings
if [[ ! -f /etc/apt/keyrings/docker.gpg ]]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
fi

ARCH="$(dpkg --print-architecture)"
CODENAME="$(. /etc/os-release && echo "$VERSION_CODENAME")"
cat >/etc/apt/sources.list.d/docker.list <<EOF
deb [arch=$ARCH signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $CODENAME stable
EOF

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable docker
systemctl start docker

if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  echo "Creating deployment user $DEPLOY_USER"
  useradd --create-home --shell /bin/bash "$DEPLOY_USER"
fi
usermod -aG docker "$DEPLOY_USER"

echo "Preparing $APP_DIR"
install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR"
install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR/scripts"
install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR/deploy/nginx/conf.d"
install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR/deploy/nginx/templates"
install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR/deploy/nginx/certbot/www"
install -d -m 0750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR/deploy/nginx/certbot/conf"

cat >/etc/logrotate.d/cloudnotes-docker <<'EOF'
/var/lib/docker/containers/*/*.log {
  rotate 7
  daily
  compress
  size=100M
  missingok
  delaycompress
  copytruncate
}
EOF

echo "Bootstrap complete. Copy docker-compose.prod.yml, deploy/, and scripts/ into $APP_DIR."
echo "Use SSM Parameter Store or host environment variables for runtime secrets; do not write secrets into this script."
