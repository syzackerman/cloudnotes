# CloudNotes AWS Infrastructure

This Terraform foundation creates a low-cost portfolio deployment for CloudNotes.

Default resources:

- VPC with one public subnet
- Internet gateway and public route table
- EC2 instance with an IAM instance profile
- Dockerized Spring Boot, Nginx, and PostgreSQL on the EC2 instance
- Private S3 bucket for attachments and small deployment bundles
- ECR repository with image scanning and low image retention
- GitHub Actions OIDC deployment role when `github_repository` is set
- SSM permissions for deployment and standard Parameter Store runtime secrets

RDS, NAT Gateway, ALB, Elastic IP, and SSH ingress are disabled by default to keep cost as close to $0 as possible. You can opt into RDS later with `enable_rds = true`, but that is not the recommended free-tier portfolio path.

Terraform may incur AWS cost. Review every planned resource before applying.

## State

Use a remote encrypted backend for shared environments, such as an S3 backend with DynamoDB locking. Do not commit local state files or real `*.tfvars` files.

## Commands

```sh
cd infra
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform fmt -check
terraform validate
terraform plan
```

Apply only after reviewing the plan:

```sh
terraform apply
```

## Secrets

This configuration does not create production secret values. Store runtime secrets in AWS Systems Manager Parameter Store after infrastructure exists. Use standard `SecureString` parameters to avoid AWS Secrets Manager monthly charges.

For the default single-EC2 deployment, create:

- `/cloudnotes/prod/database-password`
- `/cloudnotes/prod/jwt-secret`

Optional overrides:

- `/cloudnotes/prod/database-url`
- `/cloudnotes/prod/database-username`
- `/cloudnotes/prod/s3-bucket`

Example:

```sh
aws ssm put-parameter --name /cloudnotes/prod/database-password --type SecureString --tier Standard --value 'replace-with-generated-password'
aws ssm put-parameter --name /cloudnotes/prod/jwt-secret --type SecureString --tier Standard --value 'replace-with-32-byte-or-longer-secret'
```

Terraform can still store sensitive values if you pass them through variables or resources. Avoid putting database passwords, JWT secrets, TLS keys, or application secret values into Terraform variables or state.

## Optional RDS

RDS is intentionally off for the near-free deployment. If you later enable it, set `enable_rds = true`, provide private database subnets, review the added monthly cost, and create a least-privilege application user rather than using the RDS master user.
