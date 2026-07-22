# Contributing

CloudNotes is a personal portfolio project, but small improvements should still keep the same quality bar.

## Local Checks

```bash
./mvnw spotless:check
./mvnw test
./mvnw verify
docker build -t cloudnotes:local-check .
docker compose config
bash -n scripts/*.sh
```

## Pull Requests

- Keep changes scoped.
- Do not commit `.env`, Terraform state, certificates, logs, generated coverage reports, or real tokens.
- Add or update tests for behavior changes.
- Update README or docs when public endpoints, environment variables, deployment steps, or engineering decisions change.
- Do not weaken authentication, ownership checks, CORS defaults, S3 privacy, or production secret handling.
