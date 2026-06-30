resource "random_password" "db_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*-_=+?"
}

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

locals {
  jwt_secret_value = var.jwt_secret != "" ? var.jwt_secret : random_password.jwt_secret.result
}

# ── DB Password ───────────────────────────────────────────────────────────────

resource "aws_secretsmanager_secret" "db_password" {
  name                    = "/${var.project_name}/${var.environment}/db-password"
  description             = "RDS PostgreSQL master password"
  recovery_window_in_days = 7
  tags                    = { Name = "${local.name_prefix}-secret-db-password" }
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}

# ── JWT Secret ────────────────────────────────────────────────────────────────

resource "aws_secretsmanager_secret" "jwt_secret" {
  name                    = "/${var.project_name}/${var.environment}/jwt-secret"
  description             = "Spring Boot JWT signing secret"
  recovery_window_in_days = 7
  tags                    = { Name = "${local.name_prefix}-secret-jwt" }
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = local.jwt_secret_value
}
