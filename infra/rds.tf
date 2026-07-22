resource "aws_db_subnet_group" "main" {
  count = var.enable_rds ? 1 : 0

  name       = "${local.name_prefix}-db-subnet-group"
  subnet_ids = aws_subnet.private_db[*].id

  tags = {
    Name = "${local.name_prefix}-db-subnet-group"
  }
}

resource "aws_db_instance" "postgres" {
  count = var.enable_rds ? 1 : 0

  identifier                   = "${local.name_prefix}-postgres"
  engine                       = "postgres"
  engine_version               = var.db_engine_version
  instance_class               = var.db_instance_class
  allocated_storage            = var.db_allocated_storage_gb
  storage_type                 = "gp3"
  storage_encrypted            = true
  db_name                      = var.db_name
  username                     = var.db_master_username
  manage_master_user_password  = true
  db_subnet_group_name         = aws_db_subnet_group.main[0].name
  vpc_security_group_ids       = [aws_security_group.rds[0].id]
  publicly_accessible          = false
  multi_az                     = var.db_multi_az
  backup_retention_period      = var.db_backup_retention_days
  deletion_protection          = var.db_deletion_protection
  auto_minor_version_upgrade   = true
  copy_tags_to_snapshot        = true
  performance_insights_enabled = var.db_performance_insights_enabled
  skip_final_snapshot          = false
  final_snapshot_identifier    = "${local.name_prefix}-postgres-final"
  enabled_cloudwatch_logs_exports = [
    "postgresql",
    "upgrade"
  ]

  tags = {
    Name = "${local.name_prefix}-postgres"
  }
}
