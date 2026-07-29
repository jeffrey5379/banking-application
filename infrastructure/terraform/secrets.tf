resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

locals {
  jwt_secret_value = var.jwt_secret != "" ? var.jwt_secret : random_password.jwt_secret.result
}

# ── DB Passwords (one per service, see rds.tf) ───────────────────────────────

resource "aws_secretsmanager_secret" "db_password" {
  for_each = local.rds_instances

  name                    = "/${var.project_name}/${var.environment}/${each.key}-db-password"
  description             = "RDS PostgreSQL master password for ${each.key}"
  recovery_window_in_days = 0
  tags                    = { Name = "${local.name_prefix}-secret-${each.key}-db-password" }
}

resource "aws_secretsmanager_secret_version" "db_password" {
  for_each = local.rds_instances

  secret_id     = aws_secretsmanager_secret.db_password[each.key].id
  secret_string = random_password.db_password[each.key].result
}

# ── JWT Secret ────────────────────────────────────────────────────────────────
# Shared by identity-service (issues tokens) and core-banking (verifies them
# statelessly) - see README Authentication.

resource "aws_secretsmanager_secret" "jwt_secret" {
  name                    = "/${var.project_name}/${var.environment}/jwt-secret"
  description             = "Spring Boot JWT signing secret"
  recovery_window_in_days = 0
  tags                    = { Name = "${local.name_prefix}-secret-jwt" }
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = local.jwt_secret_value
}

# ── Mail credentials (identity-service OTP delivery in prod) ────────────────

resource "aws_secretsmanager_secret" "mail_username" {
  name                    = "/${var.project_name}/${var.environment}/mail-username"
  description             = "SMTP username for OTP email delivery"
  recovery_window_in_days = 0
  tags                    = { Name = "${local.name_prefix}-secret-mail-username" }
}

resource "aws_secretsmanager_secret_version" "mail_username" {
  secret_id     = aws_secretsmanager_secret.mail_username.id
  secret_string = var.mail_username
}

resource "aws_secretsmanager_secret" "mail_password" {
  name                    = "/${var.project_name}/${var.environment}/mail-password"
  description             = "SMTP password for OTP email delivery"
  recovery_window_in_days = 0
  tags                    = { Name = "${local.name_prefix}-secret-mail-password" }
}

resource "aws_secretsmanager_secret_version" "mail_password" {
  secret_id     = aws_secretsmanager_secret.mail_password.id
  secret_string = var.mail_password
}

# ── MongoDB connection string (notification-service) ────────────────────────
# local.mongo_uri (docdb.tf) is the full connection string, credentials included -
# ECS secrets injection only supports whole env-var values from Secrets Manager,
# not string-interpolating a secret into a larger plain env var, so the whole URI
# has to be stored as one secret rather than DB_HOST/DB_PASSWORD-style pieces.

resource "aws_secretsmanager_secret" "mongo_uri" {
  name                    = "/${var.project_name}/${var.environment}/mongo-uri"
  description             = "MongoDB connection string for notification-service (DocumentDB cluster)"
  recovery_window_in_days = 0
  tags                    = { Name = "${local.name_prefix}-secret-mongo-uri" }
}

resource "aws_secretsmanager_secret_version" "mongo_uri" {
  secret_id     = aws_secretsmanager_secret.mongo_uri.id
  secret_string = local.mongo_uri
}
