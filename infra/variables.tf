variable "project_name" {
  type    = string
  default = "cloudnotes"
}

variable "environment" {
  type    = string
  default = "prod"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "availability_zones" {
  type        = list(string)
  default     = []
  description = "Optional explicit availability zones. Defaults to the first two available zones in the region."
}

variable "vpc_cidr" {
  type    = string
  default = "10.40.0.0/16"
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.40.1.0/24"]
}

variable "private_db_subnet_cidrs" {
  type    = list(string)
  default = []
}

variable "admin_ssh_cidr" {
  default     = "203.0.113.10/32"
  type        = string
  description = "Administrator IP CIDR allowed to SSH to EC2, for example 203.0.113.10/32. Prefer SSM-only administration when possible."

  validation {
    condition     = var.admin_ssh_cidr != "0.0.0.0/0"
    error_message = "Do not expose SSH to the whole internet. Use a single administrator IP CIDR."
  }
}

variable "ami_id" {
  type        = string
  default     = null
  description = "Optional EC2 AMI override. When null, Terraform uses the latest Amazon Linux 2023 x86_64 AMI from the AWS public SSM parameter store."
}

variable "ec2_instance_type" {
  type    = string
  default = "t3.micro"
}

variable "root_volume_size_gb" {
  type    = number
  default = 20
}

variable "enable_elastic_ip" {
  type    = bool
  default = false
}

variable "enable_ssh_ingress" {
  type        = bool
  default     = false
  description = "Set true only when SSH is required. SSM Session Manager is preferred and needs no inbound SSH."
}

variable "enable_ec2_bootstrap" {
  type        = bool
  default     = true
  description = "Run the repository bootstrap script as EC2 user data to install Docker and prepare /opt/cloudnotes."
}

variable "enable_rds" {
  type        = bool
  default     = false
  description = "Optional paid managed PostgreSQL. Keep false for the near-free single-EC2 deployment."
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_engine_version" {
  type    = string
  default = "17"
}

variable "db_allocated_storage_gb" {
  type    = number
  default = 20
}

variable "db_name" {
  type    = string
  default = "cloudnotes"
}

variable "db_master_username" {
  type    = string
  default = "cloudnotes_admin"
}

variable "db_backup_retention_days" {
  type    = number
  default = 7
}

variable "db_deletion_protection" {
  type    = bool
  default = true
}

variable "db_multi_az" {
  type    = bool
  default = false
}

variable "db_performance_insights_enabled" {
  type    = bool
  default = false
}

variable "s3_bucket_name" {
  type        = string
  description = "Globally unique private S3 bucket name for CloudNotes attachments."
}

variable "s3_object_prefix" {
  type    = string
  default = "users/*"
}

variable "s3_deployment_prefix" {
  type        = string
  default     = "deployments/*"
  description = "Private S3 prefix used by GitHub Actions to stage small deployment bundles for EC2."
}

variable "s3_versioning_enabled" {
  type        = bool
  default     = false
  description = "Keep disabled for lowest cost. Enable if attachment version recovery is required."
}

variable "ssm_parameter_path" {
  type    = string
  default = "/cloudnotes/prod"
}

variable "kms_key_arn" {
  type        = string
  default     = ""
  description = "Optional customer-managed KMS key ARN used to encrypt SSM parameters."
}

variable "github_repository" {
  type        = string
  default     = ""
  description = "Optional GitHub repository in owner/name form. When set, creates a GitHub Actions OIDC deployment role."
}

variable "github_environment" {
  type    = string
  default = "production"
}

variable "ecr_image_tag_mutability" {
  type    = string
  default = "IMMUTABLE"
}

variable "ecr_images_to_keep" {
  type        = number
  default     = 2
  description = "Small image retention count to keep ECR storage close to the free tier."
}
