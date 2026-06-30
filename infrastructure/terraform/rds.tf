resource "aws_db_subnet_group" "main" {
  name        = "${local.name_prefix}-db-subnet-group"
  description = "RDS subnet group"
  subnet_ids  = aws_subnet.private_db[*].id
  tags        = { Name = "${local.name_prefix}-db-subnet-group" }
}

resource "aws_db_parameter_group" "postgres" {
  name        = "${local.name_prefix}-pg16-params"
  family      = "postgres16"
  description = "Custom parameter group for bankapp"
  tags        = { Name = "${local.name_prefix}-pg16-params" }
}

resource "aws_db_instance" "main" {
  identifier        = "${local.name_prefix}-db"
  engine            = "postgres"
  engine_version    = "16.4"
  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db_password.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.postgres.name

  multi_az            = var.db_multi_az
  publicly_accessible = false
  deletion_protection = false

  backup_retention_period   = 7
  backup_window             = "03:00-04:00"
  maintenance_window        = "Sun:04:00-Sun:05:00"
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name_prefix}-db-final-snapshot"

  tags = { Name = "${local.name_prefix}-db" }
}
