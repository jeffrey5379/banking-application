# One RDS instance per service - see variables.tf's "Database" section for the tradeoff notes.
locals {
  rds_instances = {
    core-banking = {
      db_name = var.core_banking_db_name
    }
    identity = {
      db_name = var.identity_db_name
    }
  }
}

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

resource "random_password" "db_password" {
  for_each = local.rds_instances

  length           = 32
  special          = true
  override_special = "!#$%&*-_=+?"
}

resource "aws_db_instance" "this" {
  for_each = local.rds_instances

  identifier        = "${local.name_prefix}-${each.key}-db"
  engine            = "postgres"
  engine_version    = "16.4"
  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = each.value.db_name
  username = var.db_username
  password = random_password.db_password[each.key].result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds[each.key].id]
  parameter_group_name   = aws_db_parameter_group.postgres.name

  multi_az            = var.db_multi_az
  publicly_accessible = false
  deletion_protection = false

  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "Sun:04:00-Sun:05:00"

  # This project's own workflow tears the whole stack down and back up regularly -
  # skipping the final snapshot lets `terraform destroy` remove each instance cleanly
  # instead of leaving behind a snapshot that has to be found and deleted by hand
  # (same reasoning as force_destroy/force_delete on the S3/ECR resources). A
  # long-lived deployment would want this false, backed by real deletion protection.
  skip_final_snapshot = true

  tags = { Name = "${local.name_prefix}-${each.key}-db" }
}
