#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/cloudnotes}"
HEALTH_URL="${HEALTH_URL:-http://localhost/actuator/health}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-120}"
ROLLBACK_IMAGE="${1:-}"

if [[ -z "$ROLLBACK_IMAGE" && -f "$APP_DIR/.previous-image" ]]; then
  ROLLBACK_IMAGE="$(<"$APP_DIR/.previous-image")"
fi

if [[ -z "$ROLLBACK_IMAGE" ]]; then
  echo "Usage: $0 <previous-image-uri>; no .previous-image file was found" >&2
  exit 1
fi

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

ecr_login_if_needed() {
  local image_uri="$1"
  local registry="${image_uri%%/*}"
  if [[ "$registry" == *.amazonaws.com ]]; then
    require_command aws
    local region="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
    if [[ -z "$region" ]]; then
      echo "Set AWS_REGION before pulling from ECR." >&2
      exit 1
    fi
    aws ecr get-login-password --region "$region" \
      | docker login --username AWS --password-stdin "$registry" >/dev/null
  fi
}

wait_for_health() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if curl --fail --silent "$HEALTH_URL" >/dev/null; then
      return 0
    fi
    sleep 3
  done
  return 1
}

cd "$APP_DIR"

echo "Rolling back CloudNotes to $ROLLBACK_IMAGE"
ecr_login_if_needed "$ROLLBACK_IMAGE"
docker pull "$ROLLBACK_IMAGE"
export ECR_IMAGE_URI="$ROLLBACK_IMAGE"
"$APP_DIR/scripts/start-production.sh"

if wait_for_health; then
  if [[ -f "$APP_DIR/.current-image" ]]; then
    cp "$APP_DIR/.current-image" "$APP_DIR/.previous-image"
  fi
  printf '%s\n' "$ROLLBACK_IMAGE" >"$APP_DIR/.current-image"
  echo "Rollback healthy: $ROLLBACK_IMAGE"
  exit 0
fi

echo "Rollback did not become healthy. Database migrations were not rolled back automatically." >&2
exit 1
