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

output "kyc_documents_bucket_name" {
  description = "S3 bucket name for KYC document/selfie uploads (identity-service)"
  value       = aws_s3_bucket.kyc_documents.bucket
}

output "ecr_repository_urls" {
  description = "ECR repository URLs by service - key into this for Docker pushes"
  value       = { for k, r in aws_ecr_repository.this : k => r.repository_url }
}

output "ecs_cluster_name" {
  description = "ECS cluster name (shared by all three services)"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_names" {
  description = "ECS service names by service - key into this for deployments"
  value = {
    identity               = aws_ecs_service.identity.name
    core-banking           = aws_ecs_service.core_banking.name
    gateway                = aws_ecs_service.gateway.name
    debit-eligibility-mock = aws_ecs_service.debit_eligibility_mock.name
    notification           = aws_ecs_service.notification.name
  }
}

output "alb_dns_name" {
  description = "ALB DNS (internal — accessed via CloudFront)"
  value       = aws_lb.main.dns_name
}

output "rds_endpoints" {
  description = "RDS PostgreSQL endpoints by service (private)"
  value       = { for k, db in aws_db_instance.this : k => db.endpoint }
  sensitive   = true
}

output "redis_endpoint" {
  description = "ElastiCache Redis endpoint (private, shared by all three services)"
  value       = "${aws_elasticache_cluster.main.cache_nodes[0].address}:${aws_elasticache_cluster.main.cache_nodes[0].port}"
}

output "docdb_endpoint" {
  description = "DocumentDB cluster endpoint (private, notification-service only)"
  value       = aws_docdb_cluster.notification.endpoint
  sensitive   = true
}

output "aws_region" {
  description = "Deployed AWS region"
  value       = var.aws_region
}
