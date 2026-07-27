variable "aws_region" {
  description = "AWS region for the tfstate bucket - should match the main config's aws_region"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name prefix - should match the main config's project_name"
  type        = string
  default     = "octopus-bank"
}
