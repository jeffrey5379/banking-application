# Bootstraps the S3 bucket the MAIN config (infrastructure/terraform/) stores its
# own state in. Kept as a separate root module deliberately: the main config's S3
# backend needs a bucket to already exist before `terraform init` can even run
# there, so that bucket can't be a resource managed BY the main config itself
# (chicken-and-egg). This module's own state stays local - it's small, rarely
# changes, and using a remote backend for it would just relocate the same
# bootstrapping problem one level up.
#
# Usage: see infrastructure/scripts/bootstrap-state.sh / teardown-state.sh, which
# apply/destroy this module and wire its output into the main config's backend.

terraform {
  required_version = ">= 1.10.0" # needs the S3 backend's native use_lockfile support

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "tfstate" {
  # Account ID suffix ensures a globally unique bucket name
  bucket = "${var.project_name}-tfstate-${data.aws_caller_identity.current.account_id}"

  # This project's own workflow tears the whole stack (including this bucket)
  # down and back up regularly - force_destroy lets `terraform destroy` remove
  # it even though it holds state file object versions. A long-lived team
  # deployment would likely want this false, backed by real deletion
  # protection instead.
  force_destroy = true

  tags = { Name = "${var.project_name}-tfstate" }
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket                  = aws_s3_bucket.tfstate.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
