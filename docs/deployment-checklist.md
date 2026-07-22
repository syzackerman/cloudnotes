# CloudNotes Deployment Checklist

Use this as the production readiness runbook before a real AWS launch.

## Local Verification

- [ ] `./mvnw spotless:check`
- [ ] `./mvnw test`
- [ ] `./mvnw verify`
- [ ] `docker build -t cloudnotes:release-check .`
- [ ] `docker compose config`
- [ ] Production Compose config with placeholder values:

```bash
ECR_IMAGE_URI=example.com/cloudnotes:latest \
DATABASE_URL=jdbc:postgresql://db.example.com:5432/cloudnotes \
DATABASE_USERNAME=cloudnotes \
DATABASE_PASSWORD=placeholder \
JWT_SECRET=placeholder-placeholder-placeholder-32 \
AWS_REGION=us-east-1 \
AWS_S3_BUCKET=placeholder \
docker compose -f docker-compose.prod.yml config
```

- [ ] Clean database migration check through Docker Compose:

```bash
docker compose down -v
JWT_SECRET=local-development-jwt-secret-change-me-32chars-minimum docker compose up --build -d postgres app
curl --fail http://localhost:8080/actuator/health
BASE_URL=http://localhost:8080 scripts/smoke-test.sh
docker compose down -v
```

## Terraform

- [ ] Choose AWS region and confirm expected cost.
- [ ] Copy `infra/terraform.tfvars.example` to a local, untracked tfvars file.
- [ ] Set a real `admin_ssh_cidr`, `ami_id`, globally unique `s3_bucket_name`, and optional `github_repository`.
- [ ] Run:

```bash
terraform -chdir=infra fmt -check -recursive
terraform -chdir=infra init -backend=false
terraform -chdir=infra validate
```

- [ ] Run `terraform plan` and review every resource before `terraform apply`.

## AWS Manual Setup

- [ ] Create or choose DNS hostname for the API.
- [ ] Apply Terraform.
- [ ] Create least-privilege application database user in RDS.
- [ ] Store runtime values in SSM Parameter Store:
  - `/cloudnotes/prod/database-url`
  - `/cloudnotes/prod/database-username`
  - `/cloudnotes/prod/database-password`
  - `/cloudnotes/prod/jwt-secret`
  - `/cloudnotes/prod/s3-bucket`
- [ ] Configure GitHub repository variables:
  - `AWS_REGION`
  - `AWS_ACCOUNT_ID`
  - `AWS_DEPLOY_ROLE_ARN`
  - `ECR_REPOSITORY`
  - `EC2_INSTANCE_ID`
  - `APP_HEALTH_URL`
- [ ] Bootstrap EC2 with `scripts/bootstrap-ec2.sh`.
- [ ] Copy `docker-compose.prod.yml`, `deploy/`, and `scripts/` into the EC2 app directory.
- [ ] Request TLS with `scripts/request-certificate.sh`.
- [ ] Deploy through GitHub Actions or `scripts/deploy.sh`.

## Post-Deploy Verification

- [ ] `curl --fail https://YOUR_DOMAIN/actuator/health`
- [ ] Register and log in with a test account.
- [ ] Create, search, favorite, tag, soft-delete, restore, and permanently delete a note.
- [ ] Upload an attachment and verify the returned download URL works.
- [ ] Confirm Swagger and Prometheus are not publicly exposed unless intentionally enabled.
- [ ] Confirm no secrets, tokens, presigned URLs, certificates, or Terraform state are committed.
