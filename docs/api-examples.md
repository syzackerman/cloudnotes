# CloudNotes API Examples

These examples use safe placeholders. Do not paste real production JWTs, AWS URLs, credentials, or personal information into committed files.

```bash
BASE_URL="http://localhost:8080"
EMAIL="sophia@example.com"
PASSWORD="correct-horse-battery"
```

## JSON Helpers

With `jq`:

```bash
TOKEN="$(curl -sS -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | jq -r .token)"
```

Without `jq`:

```bash
TOKEN="$(curl -sS -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"
```

## Register

```bash
curl -i -X POST "$BASE_URL/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"displayName\":\"Sophia\",\"password\":\"$PASSWORD\"}"
```

## Login

```bash
curl -i -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
```

## Current User

```bash
curl -i "$BASE_URL/api/users/me" \
  -H "Authorization: Bearer $TOKEN"
```

## Create A Note

```bash
NOTE_ID="$(curl -sS -X POST "$BASE_URL/api/notes" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"First note","content":"Hello from CloudNotes"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
```

## List And Search Notes

```bash
curl -i "$BASE_URL/api/notes?page=0&size=20&sort=updatedAt,desc" \
  -H "Authorization: Bearer $TOKEN"

curl -i "$BASE_URL/api/notes?q=project&favorite=true&tag=work&page=0&size=20&sort=title,asc" \
  -H "Authorization: Bearer $TOKEN"
```

Supported note sort fields are `createdAt`, `updatedAt`, and `title`.

## Update And Favorite

```bash
curl -i -X PUT "$BASE_URL/api/notes/$NOTE_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Updated note","content":"Updated content"}'

curl -i -X PATCH "$BASE_URL/api/notes/$NOTE_ID/favorite" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"favorite":true}'
```

## Tags

```bash
TAG_ID="$(curl -sS -X POST "$BASE_URL/api/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Work"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

curl -i "$BASE_URL/api/tags" \
  -H "Authorization: Bearer $TOKEN"

curl -i -X PUT "$BASE_URL/api/notes/$NOTE_ID/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"tagIds\":[\"$TAG_ID\"]}"
```

## Trash, Restore, And Permanent Delete

```bash
curl -i -X DELETE "$BASE_URL/api/notes/$NOTE_ID" \
  -H "Authorization: Bearer $TOKEN"

curl -i "$BASE_URL/api/notes/trash?page=0&size=20&sort=updatedAt,desc" \
  -H "Authorization: Bearer $TOKEN"

curl -i -X POST "$BASE_URL/api/notes/$NOTE_ID/restore" \
  -H "Authorization: Bearer $TOKEN"

curl -i -X DELETE "$BASE_URL/api/notes/$NOTE_ID/permanent" \
  -H "Authorization: Bearer $TOKEN"
```

Permanent deletion requires the note to already be in trash.

## Attachments

Attachment examples require S3 configuration.

```bash
curl -i -X POST "$BASE_URL/api/notes/$NOTE_ID/attachments" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'file=@./example.pdf;type=application/pdf'

curl -i "$BASE_URL/api/notes/$NOTE_ID/attachments" \
  -H "Authorization: Bearer $TOKEN"

ATTACHMENT_ID="replace-with-attachment-id"

curl -i "$BASE_URL/api/notes/$NOTE_ID/attachments/$ATTACHMENT_ID/download" \
  -H "Authorization: Bearer $TOKEN"

curl -i -X DELETE "$BASE_URL/api/notes/$NOTE_ID/attachments/$ATTACHMENT_ID" \
  -H "Authorization: Bearer $TOKEN"
```

The download response contains a short-lived presigned URL. Do not log or commit real presigned URLs.

## Swagger

Local Swagger UI is available when `SWAGGER_ENABLED=true`:

```bash
open "$BASE_URL/swagger-ui.html"
```

Register or log in, copy the returned JWT, click Swagger UI's Authorize button, and enter the token value.
