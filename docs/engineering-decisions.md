# CloudNotes Engineering Decisions

CloudNotes is a portfolio-grade backend project, so the decisions below favor clear production habits without pretending the first version is a large distributed platform.

## Authentication

- JWT bearer tokens are used instead of server sessions because the API is stateless and designed for browser, mobile, or CLI clients.
- Passwords are hashed with BCrypt and never returned in API responses.
- JWT secrets are read from environment configuration. Local defaults are intentionally placeholders; production must provide strong secrets through a secret manager or runtime environment.
- The authenticated user ID comes from the JWT principal. Ownership decisions never trust request body or URL user IDs.

## API Versioning

CloudNotes keeps the current `/api` routes and documents them as version 1 behavior. Adding `/api/v1` aliases would be simple, but it would add duplicate route surface before a real breaking-change policy exists.

## Persistence

- PostgreSQL is the production database because CloudNotes depends on relational ownership, constraints, pagination, and durable migrations.
- Flyway owns schema evolution. Existing migrations are not edited after being created.
- Hibernate uses `ddl-auto=validate` so application startup verifies the schema without mutating production tables.
- Repository methods filter by owner, such as `findActiveByIdAndUserId`, so private resources return 404 whether they are missing or belong to another user.

## Notes

- Notes support soft deletion so users can restore accidental deletes.
- Permanent deletion is explicit and only allowed after soft deletion.
- Normal list and search endpoints exclude deleted notes.
- Search is case-insensitive across title and content, scoped to the authenticated user, paginated in the database, and escapes SQL wildcard characters so user input is treated as literal text.

## Tags

- Tag names are normalized for uniqueness per user.
- Tag assignment verifies that every requested tag belongs to the authenticated user.

## Attachments

- Files are stored in a private S3 bucket. PostgreSQL stores metadata only.
- Download access uses short-lived presigned URLs generated after ownership checks.
- S3 object keys contain user, note, and attachment IDs but are not returned as public API details.
- Delete operations keep database metadata if S3 deletion fails so cleanup can be retried.

## Errors

- API errors use a stable JSON shape with timestamp, status, code, safe message, path, request ID, and optional field errors.
- Internal exception details, SQL, storage keys, bucket names, passwords, JWT parser errors, and stack traces are not returned to clients.
- Authentication failures use the same response model through a custom Spring Security entry point.

## Request IDs And Logging

- `X-Request-ID` is accepted only when it matches a safe short format. Otherwise a UUID is generated.
- The request ID is returned in the response header, included in error bodies, and placed in MDC for logs.
- Production logs include timestamp, level, logger, request ID, HTTP method, and path. Sensitive headers and request bodies are not logged.

## Observability

- Actuator health is public.
- Prometheus metrics are supported but disabled by default and require authentication when enabled.
- Custom metrics use counters for auth successes/failures, note lifecycle events, and attachment upload outcomes.
- Metrics intentionally avoid high-cardinality labels such as user IDs, emails, note IDs, filenames, or JWT values.

## Code Quality

- Spotless provides automated Java formatting and runs in CI.
- JaCoCo generates coverage during `./mvnw verify` with a modest project-level baseline intended to protect meaningful tests without incentivizing empty coverage.
- Heavyweight static analysis and dependency vulnerability scanning are not enabled by default in this milestone. Tools such as OWASP Dependency-Check can be added later, but they should be configured carefully because remote vulnerability database availability can make local and CI builds flaky.

## Rate Limiting

- Registration and login are limited per client remote address.
- Attachment upload and presigned download URL creation are limited per authenticated user.
- The implementation is in-memory fixed-window rate limiting. This is useful for a single-instance deployment and tests, but distributed deployments should use shared state such as Redis or an API gateway/WAF.
- The app does not trust `X-Forwarded-For` directly for rate limits. Production proxy trust should be configured deliberately.

## CORS And Headers

- CORS denies unknown origins by default. Allowed origins come from `CORS_ALLOWED_ORIGINS`.
- Credentials are only allowed when explicit origins are configured, never with `*`.
- Spring Security manages content type, frame, referrer, and permissions policy headers.
- Nginx should manage HTTPS-only concerns such as HSTS in production so local HTTP development is not accidentally pinned.

## Optimistic Locking

Optimistic locking is intentionally deferred. `@Version` on notes would be reasonable once clients send and handle version values, but adding it now would create conflict semantics without a frontend retry/refresh flow. The global exception handler is ready to return HTTP 409 for optimistic locking failures if this is added later.

## Deployment

- Docker provides a repeatable application artifact.
- Terraform documents the low-cost AWS shape: one EC2 host running Dockerized app/PostgreSQL/Nginx, plus S3, ECR, IAM, SSM, and security groups. RDS remains optional and disabled by default.
- EC2 uses an IAM role for AWS access instead of long-lived access keys.
- GitHub Actions uses OIDC for deployment credentials and avoids deploying from pull requests.
- The first production topology is single-instance and not highly available. A multi-AZ or container-orchestrated deployment would be the next availability step.

## License

No license is selected in this milestone. Add a `LICENSE` file only after intentionally choosing terms for reuse.
