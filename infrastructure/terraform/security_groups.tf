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
# One security group per service, each with narrow, directional ingress rules
# matching the real call graph

resource "aws_security_group" "gateway" {
  name        = "${local.name_prefix}-sg-gateway"
  description = "gateway-service: accept from ALB only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Gateway traffic from ALB"
    from_port       = var.gateway_container_port
    to_port         = var.gateway_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Outbound: ECR pull, Secrets Manager, Redis, identity/core-banking/notification Service Connect"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name_prefix}-sg-gateway" }
}

resource "aws_security_group" "identity" {
  name        = "${local.name_prefix}-sg-identity"
  description = "identity-service: accept from gateway, core-banking, and notification only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "From gateway (/api/auth/**, /api/kyc/**)"
    from_port       = var.identity_container_port
    to_port         = var.identity_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.gateway.id]
  }

  ingress {
    description     = "From core-banking (KycStatusClient, DataSeeder calling /internal/**)"
    from_port       = var.identity_container_port
    to_port         = var.identity_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.core_banking.id]
  }

  ingress {
    description     = "From notification-service (MessageSeeder calling /internal/users)"
    from_port       = var.identity_container_port
    to_port         = var.identity_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.notification.id]
  }

  egress {
    description = "Outbound: ECR pull, Secrets Manager, RDS, Redis, S3, SMTP"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name_prefix}-sg-identity" }
}

resource "aws_security_group" "core_banking" {
  name        = "${local.name_prefix}-sg-core-banking"
  description = "core-banking-service: accept from gateway only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "From gateway (/api/accounts/**, /api/exchange-rates)"
    from_port       = var.core_banking_container_port
    to_port         = var.core_banking_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.gateway.id]
  }

  egress {
    description = "Outbound: ECR pull, Secrets Manager, RDS, Redis, identity + debit-eligibility-mock + notification Service Connect, external debit-eligibility vendor"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name_prefix}-sg-core-banking" }
}

resource "aws_security_group" "notification" {
  name        = "${local.name_prefix}-sg-notification"
  description = "notification-service: accept from gateway and core-banking only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "From gateway (/api/notifications/**)"
    from_port       = var.notification_container_port
    to_port         = var.notification_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.gateway.id]
  }

  ingress {
    description     = "From core-banking (its own /internal/messages - only trusted caller today)"
    from_port       = var.notification_container_port
    to_port         = var.notification_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.core_banking.id]
  }

  egress {
    description = "Outbound: ECR pull, Secrets Manager, Redis, DocumentDB, identity Service Connect"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name_prefix}-sg-notification" }
}

resource "aws_security_group" "debit_eligibility_mock" {
  name        = "${local.name_prefix}-sg-debit-eligibility-mock"
  description = "debit-eligibility-mock: accept from core-banking only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "From core-banking (DebitEligibilityClient)"
    from_port       = var.debit_eligibility_mock_port
    to_port         = var.debit_eligibility_mock_port
    protocol        = "tcp"
    security_groups = [aws_security_group.core_banking.id]
  }

  egress {
    description = "Outbound: ECR pull"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name_prefix}-sg-debit-eligibility-mock" }
}

# ── RDS ──────────────────────────────────────────────────────────────────────
# One SG per RDS instance (see rds.tf's local.rds_instances), each accepting only
# from the one service that owns that database - not the whole ECS tier.

locals {
  rds_owner_security_group = {
    core-banking = aws_security_group.core_banking.id
    identity     = aws_security_group.identity.id
  }
}

resource "aws_security_group" "rds" {
  for_each = local.rds_instances

  name        = "${local.name_prefix}-sg-rds-${each.key}"
  description = "RDS (${each.key}): accept PostgreSQL from ${each.key} only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from ${each.key}"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [local.rds_owner_security_group[each.key]]
  }

  tags = { Name = "${local.name_prefix}-sg-rds-${each.key}" }
}

# ── Redis (ElastiCache) ───────────────────────────────────────────────────────
# Shared by gateway/identity/core-banking/notification (see redis.tf) -
# deliberately excludes debit-eligibility-mock, which has no Redis client at all.

resource "aws_security_group" "redis" {
  name        = "${local.name_prefix}-sg-redis"
  description = "Redis: accept traffic from the four services that actually use it"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "Redis from gateway/identity/core-banking/notification"
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    security_groups = [
      aws_security_group.gateway.id,
      aws_security_group.identity.id,
      aws_security_group.core_banking.id,
      aws_security_group.notification.id,
    ]
  }

  tags = { Name = "${local.name_prefix}-sg-redis" }
}

# ── DocumentDB (notification-service) ────────────────────────────────────────

resource "aws_security_group" "docdb" {
  name        = "${local.name_prefix}-sg-docdb"
  description = "DocumentDB: accept MongoDB wire protocol from notification-service only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "MongoDB wire protocol from notification-service"
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [aws_security_group.notification.id]
  }

  tags = { Name = "${local.name_prefix}-sg-docdb" }
}
