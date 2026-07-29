# CloudFront managed prefix list — restricts ALB to only accept traffic from CloudFront
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# ── ALB ─────────────────────────────────────────────────────────────────────
# The ALB only ever forwards to gateway-service - identity-service and
# core-banking-service are reached internally, over ECS Service Connect, and are
# never routed to directly (see README Architecture: "gateway-service is the only
# service the frontend/browser ever talks to").

resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-sg-alb"
  description = "ALB: accept HTTP from CloudFront only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "HTTP from CloudFront"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name_prefix}-sg-alb" }
}

# ── ECS Tasks ────────────────────────────────────────────────────────────────
# Shared by all four services. Self-referencing ingress rules let gateway-service
# reach identity-service/core-banking-service over ECS Service Connect, and let
# core-banking and notification-service reach identity-service's internal
# (unauthenticated) endpoints (/internal/kyc/**, /internal/users/**) - see
# README's "Known simplifications". The same self-referencing rule also covers
# notification-service's own /internal/messages, reached the same way by
# whichever other service ends up calling it.

resource "aws_security_group" "ecs" {
  name        = "${local.name_prefix}-sg-ecs"
  description = "ECS tasks: gateway from ALB, inter-service traffic between all four"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Gateway traffic from ALB"
    from_port       = var.gateway_container_port
    to_port         = var.gateway_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description = "identity-service, reached internally by gateway and core-banking"
    from_port   = var.identity_container_port
    to_port     = var.identity_container_port
    protocol    = "tcp"
    self        = true
  }

  ingress {
    description = "core-banking-service, reached internally by gateway"
    from_port   = var.core_banking_container_port
    to_port     = var.core_banking_container_port
    protocol    = "tcp"
    self        = true
  }

  ingress {
    description = "debit-eligibility-mock, reached internally by core-banking"
    from_port   = var.debit_eligibility_mock_port
    to_port     = var.debit_eligibility_mock_port
    protocol    = "tcp"
    self        = true
  }

  ingress {
    description = "notification-service, reached internally by gateway (/api/notifications/**)"
    from_port   = var.notification_container_port
    to_port     = var.notification_container_port
    protocol    = "tcp"
    self        = true
  }

  egress {
    description = "Outbound: ECR pull, Secrets Manager, RDS, Redis, DocumentDB, S3, SMTP, external debit-eligibility"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name_prefix}-sg-ecs" }
}

# ── RDS ──────────────────────────────────────────────────────────────────────

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-sg-rds"
  description = "RDS: accept PostgreSQL from ECS tasks only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from ECS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  tags = { Name = "${local.name_prefix}-sg-rds" }
}

# ── Redis (ElastiCache) ───────────────────────────────────────────────────────

resource "aws_security_group" "redis" {
  name        = "${local.name_prefix}-sg-redis"
  description = "Redis: accept traffic from ECS tasks only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Redis from ECS"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  tags = { Name = "${local.name_prefix}-sg-redis" }
}

# ── DocumentDB (notification-service) ────────────────────────────────────────

resource "aws_security_group" "docdb" {
  name        = "${local.name_prefix}-sg-docdb"
  description = "DocumentDB: accept MongoDB wire protocol from ECS tasks only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "MongoDB wire protocol from ECS"
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  tags = { Name = "${local.name_prefix}-sg-docdb" }
}
