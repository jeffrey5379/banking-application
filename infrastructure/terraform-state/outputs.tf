output "bucket_name" {
  description = "S3 bucket name for the main config's Terraform state"
  value       = aws_s3_bucket.tfstate.bucket
}

output "region" {
  description = "Region the bucket was created in"
  value       = var.aws_region
}
