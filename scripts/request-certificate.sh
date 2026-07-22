#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/cloudnotes}"
APP_DOMAIN="${APP_DOMAIN:-}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-}"

if [[ -z "$APP_DOMAIN" || "$APP_DOMAIN" == "api.example.com" ]]; then
  echo "Set APP_DOMAIN to the real API hostname before requesting a certificate." >&2
  exit 1
fi

if [[ -z "$LETSENCRYPT_EMAIL" ]]; then
  echo "Set LETSENCRYPT_EMAIL to the certificate contact email." >&2
  exit 1
fi

cd "$APP_DIR"

mkdir -p deploy/nginx/conf.d deploy/nginx/certbot/www deploy/nginx/certbot/conf
export APP_DOMAIN
envsubst '${APP_DOMAIN}' < deploy/nginx/templates/cloudnotes-http.conf.template > deploy/nginx/conf.d/cloudnotes.conf

echo "Starting Nginx on port 80 for the ACME HTTP challenge"
docker compose -f docker-compose.prod.yml up -d nginx

echo "Requesting Let's Encrypt certificate for $APP_DOMAIN"
docker run --rm \
  -v "$APP_DIR/deploy/nginx/certbot/www:/var/www/certbot" \
  -v "$APP_DIR/deploy/nginx/certbot/conf:/etc/letsencrypt" \
  certbot/certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --email "$LETSENCRYPT_EMAIL" \
  --agree-tos \
  --no-eff-email \
  -d "$APP_DOMAIN"

envsubst '${APP_DOMAIN}' < deploy/nginx/templates/cloudnotes-https.conf.template > deploy/nginx/conf.d/cloudnotes.conf
docker compose -f docker-compose.prod.yml up -d nginx

echo "Certificate installed and HTTPS configuration enabled for $APP_DOMAIN"
