variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name prefix for all resources"
  type        = string
  default     = "octopus-bank"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "prod"
}

# ── Networking ──────────────────────────────────────────────────────────────

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "AZs to deploy into (min 2 for RDS subnet group)"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

# ── Database ────────────────────────────────────────────────────────────────
# One RDS instance per service (identity-service, core-banking-service), matching
# the "one DB per service" rule the split enforces at the application level too -
# see README Architecture. This doubles RDS cost vs. a single shared instance;
# consolidating into one instance with two logical databases is a valid
# cost-optimization for a real deployment, at the expense of a bit more Terraform
# complexity (a second provider to manage databases inside the instance).

variable "db_username" {
  description = "PostgreSQL master username (shared by both RDS instances, each with its own password)"
  type        = string
  default     = "bankapp"
}

variable "core_banking_db_name" {
  description = "PostgreSQL database name for core-banking-service"
  type        = string
  default     = "corebankingdb"
}

variable "identity_db_name" {
  description = "PostgreSQL database name for identity-service"
  type        = string
  default     = "identitydb"
}

variable "db_instance_class" {
  description = "RDS instance class (applies to both instances)"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "RDS storage in GB (applies to both instances)"
  type        = number
  default     = 10
}

variable "db_multi_az" {
  description = "Enable RDS Multi-AZ (recommended for production)"
  type        = bool
  default     = false
}

# ── ECS ─────────────────────────────────────────────────────────────────────

variable "ecs_cpu" {
  description = "Fargate task CPU units (512 = 0.5 vCPU) - applies to all three services"
  type        = number
  default     = 512
}

variable "ecs_memory" {
  description = "Fargate task memory in MB - applies to all three services"
  type        = number
  default     = 1024
}

variable "ecs_desired_count" {
  description = "Number of running ECS tasks per service"
  type        = number
  default     = 1
}

variable "gateway_container_port" {
  description = "Port gateway-service listens on - the only service reachable from the ALB"
  type        = number
  default     = 8080
}

variable "identity_container_port" {
  description = "Port identity-service listens on (internal only, reached via ECS Service Connect)"
  type        = number
  default     = 8081
}

variable "core_banking_container_port" {
  description = "Port core-banking-service listens on (internal only, reached via ECS Service Connect)"
  type        = number
  default     = 8082
}

# ── Redis (ElastiCache) ───────────────────────────────────────────────────────
# Shared by all three services: rate limiting (gateway), token blacklist + OTP
# challenges (identity-service), idempotency-key dedup (core-banking) - see README.

variable "redis_node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.t3.micro"
}

# ── Mail (identity-service OTP delivery in prod) ─────────────────────────────
# EmailOtpClient (@Profile("prod")) sends real emails; MockOtpClient only runs
# outside prod. A real SMTP account is required for a working prod deployment.

variable "mail_host" {
  description = "SMTP host used by identity-service to send OTP codes in prod"
  type        = string
}

variable "mail_port" {
  description = "SMTP port"
  type        = number
  default     = 587
}

variable "mail_username" {
  description = "SMTP username"
  type        = string
  sensitive   = true
}

variable "mail_password" {
  description = "SMTP password"
  type        = string
  sensitive   = true
}

variable "mail_from" {
  description = "From address for OTP emails - must be a sender verified (or domain authenticated) in the SMTP relay account"
  type        = string
}

# ── Debit eligibility (core-banking) ─────────────────────────────────────────
# Locally this points at a WireMock stub (see README's "Debit eligibility").
# By default this deploys that same stub (same mappings, same alice/bob/carol
# behavior) as its own lightweight ECS Fargate service - see the
# debit-eligibility-mock resources in ecs.tf. Set debit_eligibility_url to a
# real vendor endpoint to bypass the mock once one is actually integrated;
# core-banking's DebitEligibilityClient fails CLOSED (rejects transfers) if
# whatever this ultimately resolves to is unreachable or misconfigured.

variable "debit_eligibility_url" {
  description = "Base URL of a real debit-eligibility vendor. Leave empty to deploy and use the bundled WireMock mock instead."
  type        = string
  default     = ""
}

variable "debit_eligibility_mock_port" {
  description = "Port the bundled debit-eligibility-mock (WireMock) service listens on"
  type        = number
  default     = 8083
}

variable "debit_eligibility_mock_cpu" {
  description = "Fargate CPU units for debit-eligibility-mock - far lighter than the Spring Boot services"
  type        = number
  default     = 256
}

variable "debit_eligibility_mock_memory" {
  description = "Fargate memory (MB) for debit-eligibility-mock"
  type        = number
  default     = 512
}

# ── Secrets ──────────────────────────────────────────────────────────────────

variable "jwt_secret" {
  description = "JWT signing secret shared by identity-service (issues) and core-banking (verifies) - leave empty to auto-generate"
  type        = string
  default     = ""
  sensitive   = true
}
