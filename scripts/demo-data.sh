#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DEMO_EMAIL="${DEMO_EMAIL:-demo+$(date +%s)@example.com}"
DEMO_PASSWORD="${DEMO_PASSWORD:-correct-horse-battery}"
DISPLAY_NAME="${DISPLAY_NAME:-Demo User}"

json_value() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"
}

api() {
  curl --fail --silent --show-error "$@"
}

auth_token() {
  local register_response
  if register_response="$(curl --fail --silent --show-error -X POST "$BASE_URL/api/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$DEMO_EMAIL\",\"displayName\":\"$DISPLAY_NAME\",\"password\":\"$DEMO_PASSWORD\"}")"; then
    printf '%s' "$register_response" | json_value token
    return
  fi

  api -X POST "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\"}" | json_value token
}

TOKEN="$(auth_token)"

create_tag() {
  local name="$1"
  api -X POST "$BASE_URL/api/tags" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"$name\"}" | json_value id
}

create_note() {
  local title="$1"
  local content="$2"
  api -X POST "$BASE_URL/api/notes" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"$title\",\"content\":\"$content\"}" | json_value id
}

WORK_TAG_ID="$(create_tag "Work")"
IDEAS_TAG_ID="$(create_tag "Ideas")"
PERSONAL_TAG_ID="$(create_tag "Personal")"

LAUNCH_NOTE_ID="$(create_note "Launch checklist" "Review CI, smoke test, docs, and production environment variables.")"
API_NOTE_ID="$(create_note "API demo flow" "Register, login, create notes, search by keyword, tag notes, and test soft delete.")"
FUTURE_NOTE_ID="$(create_note "Future improvements" "Refresh tokens, email verification, object antivirus scanning, and hosted frontend.")"

api -X PUT "$BASE_URL/api/notes/$LAUNCH_NOTE_ID/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"tagIds\":[\"$WORK_TAG_ID\"]}" >/dev/null

api -X PUT "$BASE_URL/api/notes/$API_NOTE_ID/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"tagIds\":[\"$WORK_TAG_ID\",\"$IDEAS_TAG_ID\"]}" >/dev/null

api -X PUT "$BASE_URL/api/notes/$FUTURE_NOTE_ID/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"tagIds\":[\"$PERSONAL_TAG_ID\",\"$IDEAS_TAG_ID\"]}" >/dev/null

api -X PATCH "$BASE_URL/api/notes/$API_NOTE_ID/favorite" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"favorite":true}' >/dev/null

api -X DELETE "$BASE_URL/api/notes/$FUTURE_NOTE_ID" \
  -H "Authorization: Bearer $TOKEN" >/dev/null

echo "Demo data ready for $DEMO_EMAIL"
