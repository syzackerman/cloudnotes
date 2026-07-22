locals {
  parameter_paths = {
    database_url      = "${var.ssm_parameter_path}/database-url"
    database_username = "${var.ssm_parameter_path}/database-username"
    database_password = "${var.ssm_parameter_path}/database-password"
    jwt_secret        = "${var.ssm_parameter_path}/jwt-secret"
    aws_region        = "${var.ssm_parameter_path}/aws-region"
    s3_bucket         = "${var.ssm_parameter_path}/s3-bucket"
  }
}
