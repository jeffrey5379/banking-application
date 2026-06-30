output "app_url" {
  description = "Application URL (CloudFront)"
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID — used for cache invalidation"
  value       = aws_cloudfront_distribution.main.id
}

output "s3_bucket_name" {
  description = "S3 bucket name for Angular frontend"
  value       = aws_s3_bucket.frontend.bucket
}

output "ecr_repository_url" {
  description = "ECR repository URL for Docker pushes"
  value       = aws_ecr_repository.backend.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS service name"
  value       = aws_ecs_service.backend.name
}

output "alb_dns_name" {
  description = "ALB DNS (internal — accessed via CloudFront)"
  value       = aws_lb.main.dns_name
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint (private)"
  value       = aws_db_instance.main.endpoint
  sensitive   = true
}

output "aws_region" {
  description = "Deployed AWS region"
  value       = var.aws_region
}
