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

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "bankdb"
}

variable "db_username" {
  description = "PostgreSQL master username"
  type        = string
  default     = "bankapp"
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "RDS storage in GB"
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
  description = "Fargate task CPU units (512 = 0.5 vCPU)"
  type        = number
  default     = 512
}

variable "ecs_memory" {
  description = "Fargate task memory in MB"
  type        = number
  default     = 1024
}

variable "ecs_desired_count" {
  description = "Number of running ECS tasks"
  type        = number
  default     = 1
}

variable "container_port" {
  description = "Port the Spring Boot container listens on"
  type        = number
  default     = 8080
}

# ── Secrets ──────────────────────────────────────────────────────────────────

variable "jwt_secret" {
  description = "JWT signing secret (leave empty to auto-generate)"
  type        = string
  default     = ""
  sensitive   = true
}
