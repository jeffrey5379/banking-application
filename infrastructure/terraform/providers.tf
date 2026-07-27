terraform {
  required_version = ">= 1.10.0" # needs the S3 backend's native use_lockfile support

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Partial configuration: bucket/region come from infrastructure/scripts/
  # bootstrap-state.sh via `terraform init -backend-config=...`, not hardcoded
  # here - the bucket is created by infrastructure/terraform-state/ (a separate
  # bootstrap module) precisely so this config never needs to hardcode or own a
  # specific AWS account's bucket name. use_lockfile stores the lock directly in
  # the bucket (native S3 locking) - no separate DynamoDB table needed.
  backend "s3" {
    key          = "bankapp/terraform.tfstate"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
