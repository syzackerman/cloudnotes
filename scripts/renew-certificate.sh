#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/cloudnotes}"
DRY_RUN="${DRY_RUN:-false}"

cd "$APP_DIR"

ARGS=(renew)
if [[ "$DRY_RUN" == "true" ]]; then
  ARGS+=(--dry-run)
fi

docker run --rm \
  -v "$APP_DIR/deploy/nginx/certbot/www:/var/www/certbot" \
  -v "$APP_DIR/deploy/nginx/certbot/conf:/etc/letsencrypt" \
  certbot/certbot "${ARGS[@]}"

docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
