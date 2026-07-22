# CloudNotes AWS Infrastructure

This Terraform foundation creates the AWS resources needed for a first production deployment:

- VPC with public subnets for EC2 and private subnets for RDS
- Internet gateway and public route table
- Security groups for EC2/Nginx and RDS
- EC2 instance with an IAM instance profile
- RDS PostgreSQL with encrypted storage, private networking, backups, and deletion protection
- Private S3 bucket for attachments with public access blocked and server-side encryption
- ECR repository with image scanning and lifecycle retention
- Parameter Store path outputs for runtime configuration
- Optional GitHub Actions OIDC deployment role when `github_repository` is set

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

This configuration does not create production secret values. Store runtime secrets in AWS Systems Manager Parameter Store after infrastructure exists.

Terraform can still store sensitive values if you pass them through variables or resources. Avoid putting database passwords, JWT secrets, TLS keys, or application secret values into Terraform variables or state.

## App Database User

RDS creates a master user managed by AWS. Do not use that master user from the application. Connect as an administrator and create a least-privilege app user:

```sql
CREATE USER cloudnotes_app WITH PASSWORD 'replace-with-generated-password';
GRANT CONNECT ON DATABASE cloudnotes TO cloudnotes_app;
GRANT USAGE ON SCHEMA public TO cloudnotes_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cloudnotes_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO cloudnotes_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO cloudnotes_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO cloudnotes_app;
```

Store `cloudnotes_app` and its generated password in Parameter Store.
