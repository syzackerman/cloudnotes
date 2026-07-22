output "ec2_instance_id" {
  value = aws_instance.app.id
}

output "ec2_public_ip" {
  value = var.enable_elastic_ip ? aws_eip.app[0].public_ip : aws_instance.app.public_ip
}

output "rds_endpoint" {
  value = aws_db_instance.postgres.address
}

output "rds_database_name" {
  value = aws_db_instance.postgres.db_name
}

output "s3_bucket_name" {
  value = aws_s3_bucket.attachments.bucket
}

output "ecr_repository_url" {
  value = aws_ecr_repository.cloudnotes.repository_url
}

output "ssm_parameter_paths" {
  value = local.parameter_paths
}

output "ec2_role_name" {
  value = aws_iam_role.ec2.name
}

output "github_deploy_role_arn" {
  value = length(aws_iam_role.github_deploy) > 0 ? aws_iam_role.github_deploy[0].arn : null
}
