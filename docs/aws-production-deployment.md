# AWS Production Deployment

This guide prepares CloudNotes for a reproducible AWS deployment without creating live resources automatically from local scripts.

## Architecture

Request flow:

```text
Client
  -> HTTPS
  -> Nginx on EC2, ports 80/443
  -> Spring Boot Docker container, internal port 8080
  -> Amazon RDS PostgreSQL
```

Spring Boot reads RDS, JWT, and S3 settings from environment variables. In production, `scripts/start-production.sh` loads secret and runtime values from AWS Systems Manager Parameter Store and starts `docker-compose.prod.yml`. `AWS_REGION` must be set before running the script because the AWS CLI needs a region to read Parameter Store.

Attachments are stored in a private S3 bucket. The EC2 instance uses its IAM role and the AWS default credentials provider chain; do not configure long-lived AWS access keys on EC2.

This single-EC2 architecture is not highly available. Low-downtime deployment is limited to pulling the new image before restarting the app and rolling back the image when health checks fail. True high availability requires multiple instances, a load balancer, auto scaling, and rolling deployments across instances.

## Security Boundaries

- EC2 security group: allow `22` only from an administrator IP CIDR, `80` from the internet, and `443` from the internet.
- Port `8080`: only exposed on the Docker internal network; it must not be opened in the EC2 security group.
- RDS security group: allow PostgreSQL `5432` only from the EC2 security group.
- S3 bucket: private, public access blocked, encrypted, object access limited to `users/*`.
- GitHub Actions: uses OIDC to assume a limited AWS role; no permanent AWS access keys are stored in GitHub.
- EC2 IAM role: grants S3 object access for attachments, ECR pull, Parameter Store reads under `/cloudnotes/prod/*`, and SSM core access.

## Production Profile

Run the application with:

```sh
SPRING_PROFILES_ACTIVE=prod
```

The production profile uses:

- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRATION`
- `AWS_REGION`, `AWS_S3_BUCKET`
- `SERVER_PORT`
- conservative HikariCP defaults through `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, and `DB_CONNECTION_TIMEOUT_MS`
- Flyway validation and automatic ordered migrations
- Hibernate `ddl-auto=validate`, never destructive schema generation
- forwarded-header support for Nginx
- health probes without exposing environment values

## Secrets Strategy

Use AWS Systems Manager Parameter Store with `SecureString` for secrets. At minimum protect:

- `/cloudnotes/prod/database-password`
- `/cloudnotes/prod/jwt-secret`

Recommended production parameters:

```text
/cloudnotes/prod/database-url
/cloudnotes/prod/database-username
/cloudnotes/prod/database-password
/cloudnotes/prod/jwt-secret
/cloudnotes/prod/aws-region
/cloudnotes/prod/s3-bucket
```

Create parameters with placeholders replaced locally:

```sh
aws ssm put-parameter --name /cloudnotes/prod/database-url --type String --value 'jdbc:postgresql://RDS_ENDPOINT:5432/cloudnotes'
aws ssm put-parameter --name /cloudnotes/prod/database-username --type String --value 'cloudnotes_app'
aws ssm put-parameter --name /cloudnotes/prod/database-password --type SecureString --value 'GENERATED_DATABASE_PASSWORD'
aws ssm put-parameter --name /cloudnotes/prod/jwt-secret --type SecureString --value 'GENERATED_32_BYTE_OR_LONGER_SECRET'
aws ssm put-parameter --name /cloudnotes/prod/aws-region --type String --value 'us-east-1'
aws ssm put-parameter --name /cloudnotes/prod/s3-bucket --type String --value 'YOUR_PRIVATE_BUCKET'
```

If you use a customer-managed KMS key for SecureString values, scope `kms:Decrypt` to that single key.

## EC2 IAM Policy

The Terraform foundation creates a scoped EC2 role. Its custom policy is equivalent to:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "CloudNotesS3AttachmentAccess",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::YOUR_BUCKET/users/*"
    },
    {
      "Sid": "ReadCloudNotesParameters",
      "Effect": "Allow",
      "Action": ["ssm:GetParameter", "ssm:GetParameters"],
      "Resource": "arn:aws:ssm:REGION:ACCOUNT_ID:parameter/cloudnotes/prod/*"
    },
    {
      "Sid": "EcrAuthorizationToken",
      "Effect": "Allow",
      "Action": ["ecr:GetAuthorizationToken"],
      "Resource": "*"
    },
    {
      "Sid": "PullCloudNotesImages",
      "Effect": "Allow",
      "Action": ["ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"],
      "Resource": "arn:aws:ecr:REGION:ACCOUNT_ID:repository/cloudnotes"
    }
  ]
}
```

Do not attach administrator permissions or static AWS credentials to the instance.

## RDS Recommendations

- Place RDS in private subnets.
- Set public accessibility to disabled.
- Enable encrypted storage.
- Enable automated backups and choose a retention period that matches recovery requirements.
- Enable deletion protection for production.
- Enable minor version upgrades.
- Monitor CPU, free storage, connections, latency, and backup status in CloudWatch.
- Allow PostgreSQL traffic only from the EC2 security group.
- Generate a strong application database password.
- Do not use the RDS master user from the application.

Create a least-privilege app user after RDS is available:

```sql
CREATE USER cloudnotes_app WITH PASSWORD 'replace-with-generated-password';
GRANT CONNECT ON DATABASE cloudnotes TO cloudnotes_app;
GRANT USAGE ON SCHEMA public TO cloudnotes_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cloudnotes_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO cloudnotes_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO cloudnotes_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO cloudnotes_app;
```

## Database Pool Sizing

Defaults are intentionally conservative:

```text
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
DB_CONNECTION_TIMEOUT_MS=30000
```

Pool sizing must account for RDS connection limits, number of app instances, background jobs, admin sessions, and migration tools. Do not multiply a large pool across multiple instances without checking RDS limits.

## Flyway Deployment Behavior

Flyway runs when the production app starts. It validates migration history, applies new migrations in order, and fails startup if validation fails. Never edit already-applied migrations. Prefer backward-compatible migrations so an application rollback remains possible.

Application rollback does not roll back database migrations. If a migration is incompatible with the previous image, rolling back the container may not restore service.

## Nginx and HTTPS

Nginx proxies to the app container on the internal Docker network and forwards:

- `Host`
- `X-Real-IP`
- `X-Forwarded-For`
- `X-Forwarded-Proto`

The default committed config uses `api.example.com` as a placeholder. Replace it or render from templates with `APP_DOMAIN`.

Initial certificate process:

1. Allocate or use an Elastic IP.
2. Create an A record for the API hostname pointing to the EC2 Elastic IP.
3. Wait for DNS propagation.
4. Verify DNS:

```sh
dig +short api.example.com
```

5. Start the app and HTTP Nginx config.
6. Request a certificate:

```sh
APP_DOMAIN=api.example.com LETSENCRYPT_EMAIL=admin@example.com /opt/cloudnotes/scripts/request-certificate.sh
```

7. Test renewal:

```sh
DRY_RUN=true /opt/cloudnotes/scripts/renew-certificate.sh
```

Do not commit certificates, account keys, or private keys.

## GitHub OIDC Deployment

GitHub Actions OIDC lets the workflow request a short-lived AWS token for a specific repository and production environment. AWS verifies the signed GitHub token and allows `sts:AssumeRoleWithWebIdentity` only when the trust policy matches.

This avoids permanent AWS access keys in GitHub secrets. The deployment role should allow only:

- ECR image push to the CloudNotes repository
- SSM Run Command to the intended EC2 instance
- reading SSM command results

Required repository variables:

```text
AWS_REGION
AWS_ACCOUNT_ID
AWS_DEPLOY_ROLE_ARN
ECR_REPOSITORY
EC2_INSTANCE_ID
DEPLOY_APP_DIR
APP_HEALTH_URL
```

`deploy.yml` deploys only after CI succeeds on `main` or when manually dispatched. It never deploys pull requests and uses image tags containing the Git commit SHA.

## EC2 Deployment Access

Prefer AWS Systems Manager Session Manager and Run Command. This avoids broadly exposing SSH and avoids storing SSH private keys in GitHub.

If SSH is still enabled, restrict port `22` to one administrator CIDR. Do not open SSH to `0.0.0.0/0`.

## Deployment Flow

On EC2, `/opt/cloudnotes/scripts/deploy.sh IMAGE_URI`:

1. Pulls the new image before changing the running container.
2. Starts the Compose stack with `ECR_IMAGE_URI=IMAGE_URI`.
3. Loads runtime secrets from Parameter Store.
4. Waits for `/actuator/health`.
5. Records `.current-image` and `.previous-image` on success.
6. Rolls back to the previous image if health fails and a previous image is known.

Manual deploy:

```sh
AWS_REGION=us-east-1 APP_DIR=/opt/cloudnotes /opt/cloudnotes/scripts/deploy.sh ACCOUNT_ID.dkr.ecr.REGION.amazonaws.com/cloudnotes:GIT_SHA
```

Manual rollback:

```sh
AWS_REGION=us-east-1 APP_DIR=/opt/cloudnotes /opt/cloudnotes/scripts/rollback.sh ACCOUNT_ID.dkr.ecr.REGION.amazonaws.com/cloudnotes:PREVIOUS_GIT_SHA
```

## Logs and Health

Docker logs go to standard output. Avoid logging JWTs, passwords, AWS credentials, sensitive request bodies, and complete presigned URLs.

View logs:

```sh
docker compose -f /opt/cloudnotes/docker-compose.prod.yml logs -f app
docker compose -f /opt/cloudnotes/docker-compose.prod.yml logs -f nginx
```

Health checks:

```sh
curl --fail https://api.example.com/actuator/health
curl --fail http://localhost/actuator/health
docker compose -f /opt/cloudnotes/docker-compose.prod.yml ps
```

## Backups and Recovery

- Enable RDS automated backups.
- Take manual snapshots before risky migrations.
- Consider S3 versioning for attachment recovery.
- Test database restoration before claiming backups are complete.
- Balance retention against cost.

## Monitoring

Essential:

- EC2 status checks
- RDS CPU, storage, connections, and latency alarms
- application health endpoint monitoring
- Docker container restart checks
- AWS Budgets billing alarms

Optional:

- CloudWatch logs centralization
- custom application metrics
- synthetics canaries
- disk usage alarms

## Cost Awareness

Likely cost sources include EC2, RDS, EBS, Elastic IP behavior, S3 storage and requests, data transfer, Route 53 hosted zone and queries, CloudWatch logs, ECR storage, and NAT gateway if introduced. Prices change, so configure AWS Budgets before deployment rather than relying on static estimates.

## Production Checklist

- Private S3 bucket with public access blocked
- RDS not publicly accessible
- EC2 IAM role instead of AWS access keys
- Strong JWT secret in Parameter Store
- Database password in Parameter Store
- HTTPS enabled
- Port `8080` not publicly exposed
- Restricted SSH or SSM-only administration
- Encrypted RDS storage
- Backups enabled
- Least-privilege IAM
- GitHub OIDC enabled
- No secrets in Git history
- Dependency and container updates planned
- Logs reviewed for sensitive data
