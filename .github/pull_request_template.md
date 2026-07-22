## Summary

- 

## Verification

- [ ] `./mvnw spotless:check`
- [ ] `./mvnw test`
- [ ] `./mvnw verify`
- [ ] `docker build -t cloudnotes:local-check .`
- [ ] `docker compose config`

## Security And Config

- [ ] No secrets, JWTs, presigned URLs, certificates, Terraform state, or local `.env` files are committed.
- [ ] Ownership-protected resources still return 404 when missing or owned by another user.
- [ ] New environment variables are documented.
