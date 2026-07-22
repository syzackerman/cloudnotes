#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/cloudnotes}"
PARAMETER_PATH="${PARAMETER_PATH:-/cloudnotes/prod}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

fetch_parameter() {
  local name="$1"
  aws ssm get-parameter \
    --name "${PARAMETER_PATH}/${name}" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text
}

set_from_env_or_parameter() {
  local variable_name="$1"
  local parameter_name="$2"
  local current_value="${!variable_name:-}"

  if [[ -n "$current_value" ]]; then
    export "$variable_name=$current_value"
    return
  fi

  local fetched_value
  if ! fetched_value="$(fetch_parameter "$parameter_name")"; then
    echo "Missing required configuration: $variable_name or ${PARAMETER_PATH}/${parameter_name}" >&2
    exit 1
  fi
  export "$variable_name=$fetched_value"
}

set_from_env_or_parameter_or_default() {
  local variable_name="$1"
  local parameter_name="$2"
  local default_value="$3"
  local current_value="${!variable_name:-}"

  if [[ -n "$current_value" ]]; then
    export "$variable_name=$current_value"
    return
  fi

  local fetched_value
  if fetched_value="$(fetch_parameter "$parameter_name" 2>/dev/null)"; then
    export "$variable_name=$fetched_value"
    return
  fi

  export "$variable_name=$default_value"
}

require_nonempty() {
  local variable_name="$1"
  if [[ -z "${!variable_name:-}" ]]; then
    echo "Missing required environment variable: $variable_name" >&2
    exit 1
  fi
}

require_command aws
require_command docker

cd "$APP_DIR"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
export JWT_EXPIRATION="${JWT_EXPIRATION:-PT1H}"
export SERVER_PORT="${SERVER_PORT:-8080}"
export POSTGRES_DB="${POSTGRES_DB:-cloudnotes}"
export DB_POOL_MAX_SIZE="${DB_POOL_MAX_SIZE:-10}"
export DB_POOL_MIN_IDLE="${DB_POOL_MIN_IDLE:-2}"
export DB_CONNECTION_TIMEOUT_MS="${DB_CONNECTION_TIMEOUT_MS:-30000}"
export MAX_FILE_SIZE="${MAX_FILE_SIZE:-10MB}"
export MAX_REQUEST_SIZE="${MAX_REQUEST_SIZE:-10MB}"
export AWS_REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"

if [[ -z "$AWS_REGION" ]]; then
  echo "Set AWS_REGION or AWS_DEFAULT_REGION before loading Parameter Store values." >&2
  exit 1
fi

set_from_env_or_parameter_or_default DATABASE_URL "database-url" "jdbc:postgresql://postgres:5432/cloudnotes"
set_from_env_or_parameter_or_default DATABASE_USERNAME "database-username" "cloudnotes"
set_from_env_or_parameter DATABASE_PASSWORD "database-password"
set_from_env_or_parameter JWT_SECRET "jwt-secret"
set_from_env_or_parameter_or_default AWS_S3_BUCKET "s3-bucket" "${AWS_S3_BUCKET:-}"

if [[ -z "${ECR_IMAGE_URI:-}" && -f "$APP_DIR/.current-image" ]]; then
  ECR_IMAGE_URI="$(<"$APP_DIR/.current-image")"
  export ECR_IMAGE_URI
fi

require_nonempty SPRING_PROFILES_ACTIVE
require_nonempty DATABASE_URL
require_nonempty DATABASE_USERNAME
require_nonempty DATABASE_PASSWORD
require_nonempty JWT_SECRET
require_nonempty JWT_EXPIRATION
require_nonempty AWS_REGION
require_nonempty AWS_S3_BUCKET
require_nonempty ECR_IMAGE_URI

ECR_REGISTRY="${ECR_IMAGE_URI%%/*}"
if [[ "$ECR_REGISTRY" == *.amazonaws.com ]]; then
  echo "Logging in to ECR registry $ECR_REGISTRY"
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null
fi

echo "Starting CloudNotes production stack with image $ECR_IMAGE_URI"
docker compose -f "$COMPOSE_FILE" up -d
