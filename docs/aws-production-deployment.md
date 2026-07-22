# AWS Low-Cost Deployment

This guide prepares CloudNotes for a near-free AWS portfolio deployment. It does not run `terraform apply` automatically.

## Architecture

```text
Client
  -> HTTPS
  -> Nginx on EC2, ports 80/443
  -> Spring Boot Docker container, internal port 8080
  -> PostgreSQL Docker container on the same EC2 host
  -> Private S3 bucket for attachments

GitHub Actions
  -> OIDC AssumeRoleWithWebIdentity
  -> Amazon ECR image push
  -> S3 deployment bundle upload
  -> SSM Run Command on EC2
```

This topology intentionally avoids NAT Gateway, RDS, ALB, and Elastic IP. It is suitable for a portfolio API, not a highly available production workload.

## Terraform First

```sh
cd infra
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform fmt -check -recursive
terraform validate
terraform plan
```

Stop after `terraform plan` and review resources and cost. Apply only after you intentionally approve the plan.

## Secrets

Runtime secrets are not stored in Terraform state. Use AWS Systems Manager Parameter Store standard `SecureString` parameters:

```sh
aws ssm put-parameter --name /cloudnotes/prod/database-password --type SecureString --tier Standard --value 'GENERATED_DATABASE_PASSWORD'
aws ssm put-parameter --name /cloudnotes/prod/jwt-secret --type SecureString --tier Standard --value 'GENERATED_32_BYTE_OR_LONGER_SECRET'
```

The default production Compose file uses:

```text
DATABASE_URL=jdbc:postgresql://postgres:5432/cloudnotes
DATABASE_USERNAME=cloudnotes
POSTGRES_DB=cloudnotes
```

You can override those with Parameter Store values if needed.

## GitHub Actions Setup

Required repository variables:

```text
AWS_REGION
AWS_ACCOUNT_ID
ECR_REPOSITORY
EC2_INSTANCE_ID
AWS_S3_BUCKET
APP_HEALTH_URL
DEPLOY_APP_DIR
```

Required repository or environment secret:

```text
AWS_DEPLOY_ROLE_ARN
```

The deploy workflow runs on pushes to `main` and manual dispatch. It verifies the app, pushes an immutable image to ECR, uploads deployment files to S3, deploys through SSM Run Command, and checks the public health endpoint.

## EC2 Runtime

Terraform user data reuses `scripts/bootstrap-ec2.sh` to install Docker, Docker Compose, AWS CLI, Certbot helpers, and deployment directories.

The EC2 role can:

- pull CloudNotes images from ECR
- read Parameter Store values under `/cloudnotes/prod/*`
- access S3 attachment objects under `users/*`
- read S3 deployment bundles under `deployments/*`
- use SSM managed instance functionality

Do not attach administrator permissions or static AWS credentials to EC2.

## HTTPS

The committed Nginx config uses `api.example.com` as a placeholder. Render the real config from templates with `APP_DOMAIN`, point DNS to the EC2 public address, then request a certificate:

```sh
APP_DOMAIN=api.example.com LETSENCRYPT_EMAIL=admin@example.com /opt/cloudnotes/scripts/request-certificate.sh
```

Public IPv4 addresses currently add monthly cost. Use a DNS record pointed at the instance public IP for the simple portfolio deployment, or intentionally add an Elastic IP only if you accept the cost.

## Health And Verification

```sh
curl --fail https://YOUR_DOMAIN/actuator/health
docker compose -f /opt/cloudnotes/docker-compose.prod.yml ps
docker compose -f /opt/cloudnotes/docker-compose.prod.yml logs -f app
```

Swagger is disabled in the production profile. Use local development for Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

## Backups

PostgreSQL data lives on the EC2 root EBS volume through a Docker named volume. For a portfolio project this is acceptable, but it is not managed database durability.

Before using this for important data, add an explicit backup plan:

- scheduled `pg_dump` to private S3
- EBS snapshots
- restore testing
- retention and cleanup rules

## Cost Awareness

Primary cost sources are EC2 instance hours, public IPv4, EBS storage, ECR image storage, S3 storage/requests, data transfer, DNS, and optional CloudWatch logs. Configure AWS Budgets before applying Terraform.

## Cleanup

```sh
terraform -chdir=infra destroy
aws ecr delete-repository --repository-name cloudnotes --force
aws s3 rb s3://YOUR_BUCKET_NAME --force
aws ssm delete-parameter --name /cloudnotes/prod/database-password
aws ssm delete-parameter --name /cloudnotes/prod/jwt-secret
```

Also remove DNS records, certificates, snapshots, and local Terraform state when they are no longer needed.
