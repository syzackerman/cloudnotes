#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/cloudnotes}"
HEALTH_URL="${HEALTH_URL:-http://localhost/actuator/health}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-120}"
IMAGE_URI="${1:-${ECR_IMAGE_URI:-}}"

if [[ -z "$IMAGE_URI" ]]; then
  echo "Usage: $0 <image-uri-or-tag>" >&2
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

load_ssm_parameters() {
  require_command aws

  local prefix="${SSM_PARAMETER_PREFIX:-/cloudnotes/prod}"

  export DATABASE_URL="$(
    aws ssm get-parameter \
      --name "${prefix}/database-url" \
      --with-decryption \
      --query 'Parameter.Value' \
      --output text
  )"

  export DATABASE_USERNAME="$(
    aws ssm get-parameter \
      --name "${prefix}/database-username" \
      --with-decryption \
      --query 'Parameter.Value' \
      --output text
  )"

  export DATABASE_PASSWORD="$(
    aws ssm get-parameter \
      --name "${prefix}/database-password" \
      --with-decryption \
      --query 'Parameter.Value' \
      --output text
  )"

  export JWT_SECRET="$(
    aws ssm get-parameter \
      --name "${prefix}/jwt-secret" \
      --with-decryption \
      --query 'Parameter.Value' \
      --output text
  )"

  export AWS_S3_BUCKET="${AWS_S3_BUCKET:-$(
    aws ssm get-parameter \
      --name "${prefix}/s3-bucket" \
      --query 'Parameter.Value' \
      --output text
  )}"
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

require_command docker
require_command curl

cd "$APP_DIR"

PREVIOUS_IMAGE=""
if [[ -f "$APP_DIR/.current-image" ]]; then
  PREVIOUS_IMAGE="$(<"$APP_DIR/.current-image")"
fi

echo "Pulling CloudNotes image $IMAGE_URI"
ecr_login_if_needed "$IMAGE_URI"
docker pull "$IMAGE_URI"

load_ssm_parameters

export ECR_IMAGE_URI="$IMAGE_URI"
if ! "$APP_DIR/scripts/start-production.sh"; then
  echo "Failed to start CloudNotes with $IMAGE_URI" >&2
  exit 1
fi

if wait_for_health; then
  if [[ -n "$PREVIOUS_IMAGE" && "$PREVIOUS_IMAGE" != "$IMAGE_URI" ]]; then
    printf '%s\n' "$PREVIOUS_IMAGE" >"$APP_DIR/.previous-image"
  fi
  printf '%s\n' "$IMAGE_URI" >"$APP_DIR/.current-image"
  echo "Deployment healthy: $IMAGE_URI"
  exit 0
fi

echo "Deployment did not become healthy: $IMAGE_URI" >&2
if [[ -n "$PREVIOUS_IMAGE" && "$PREVIOUS_IMAGE" != "$IMAGE_URI" ]]; then
  echo "Rolling back to previous image $PREVIOUS_IMAGE"
  ecr_login_if_needed "$PREVIOUS_IMAGE"
  docker pull "$PREVIOUS_IMAGE"
  export ECR_IMAGE_URI="$PREVIOUS_IMAGE"
  "$APP_DIR/scripts/start-production.sh"
  if wait_for_health; then
    printf '%s\n' "$PREVIOUS_IMAGE" >"$APP_DIR/.current-image"
    echo "Rollback healthy: $PREVIOUS_IMAGE"
  else
    echo "Rollback did not become healthy. Inspect Docker logs immediately." >&2
  fi
else
  echo "No previous image recorded; rollback skipped." >&2
fi

exit 1
