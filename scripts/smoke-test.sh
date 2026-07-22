#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SMOKE_EMAIL="${SMOKE_EMAIL:-smoke+$(date +%s)@example.com}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-correct-horse-battery}"
DISPLAY_NAME="${DISPLAY_NAME:-Smoke Tester}"

json_value() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"
}

api() {
  curl --fail --silent --show-error "$@"
}

echo "Checking health at $BASE_URL"
api "$BASE_URL/actuator/health" >/dev/null

if [[ "${CHECK_OPENAPI:-true}" == "true" ]]; then
  echo "Checking OpenAPI docs"
  api "$BASE_URL/v3/api-docs" >/dev/null
fi

echo "Registering smoke user $SMOKE_EMAIL"
REGISTER_RESPONSE="$(api -X POST "$BASE_URL/api/auth/register" \
  -H 'Content-Type: application/json' \
  -H 'X-Request-ID: smoke-register' \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"displayName\":\"$DISPLAY_NAME\",\"password\":\"$SMOKE_PASSWORD\"}")"
TOKEN="$(printf '%s' "$REGISTER_RESPONSE" | json_value token)"

echo "Logging in smoke user"
LOGIN_RESPONSE="$(api -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -H 'X-Request-ID: smoke-login' \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"$SMOKE_PASSWORD\"}")"
TOKEN="$(printf '%s' "$LOGIN_RESPONSE" | json_value token)"

echo "Checking current user"
api "$BASE_URL/api/users/me" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'X-Request-ID: smoke-me' >/dev/null

echo "Creating note"
NOTE_RESPONSE="$(api -X POST "$BASE_URL/api/notes" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'X-Request-ID: smoke-create-note' \
  -d '{"title":"Smoke test note","content":"Created by scripts/smoke-test.sh"}')"
NOTE_ID="$(printf '%s' "$NOTE_RESPONSE" | json_value id)"

echo "Listing notes"
api "$BASE_URL/api/notes?page=0&size=5&sort=updatedAt,desc" \
  -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Reading note $NOTE_ID"
api "$BASE_URL/api/notes/$NOTE_ID" \
  -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Updating note"
api -X PUT "$BASE_URL/api/notes/$NOTE_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Smoke test note updated","content":"Updated by the smoke test"}' >/dev/null

echo "Favoriting note"
api -X PATCH "$BASE_URL/api/notes/$NOTE_ID/favorite" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"favorite":true}' >/dev/null

echo "Searching notes"
api "$BASE_URL/api/notes?q=smoke&favorite=true&page=0&size=5&sort=updatedAt,desc" \
  -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Creating tag"
TAG_RESPONSE="$(api -X POST "$BASE_URL/api/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Smoke"}')"
TAG_ID="$(printf '%s' "$TAG_RESPONSE" | json_value id)"

echo "Assigning tag $TAG_ID"
api -X PUT "$BASE_URL/api/notes/$NOTE_ID/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"tagIds\":[\"$TAG_ID\"]}" >/dev/null

echo "Soft deleting note"
api -X DELETE "$BASE_URL/api/notes/$NOTE_ID" \
  -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Listing trash"
api "$BASE_URL/api/notes/trash?page=0&size=5&sort=updatedAt,desc" \
  -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Restoring note"
api -X POST "$BASE_URL/api/notes/$NOTE_ID/restore" \
  -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Permanently deleting note"
api -X DELETE "$BASE_URL/api/notes/$NOTE_ID" \
  -H "Authorization: Bearer $TOKEN" >/dev/null
api -X DELETE "$BASE_URL/api/notes/$NOTE_ID/permanent" \
  -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Smoke test passed"
