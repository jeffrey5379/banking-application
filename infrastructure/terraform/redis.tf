# Single shared Redis instance for all four services (rate limiting on the gateway,
# token blacklist + OTP challenges on identity-service, idempotency-key dedup on
# core-banking, tickets + pub/sub on notification-service) - see README's "Why
# everything moved to Redis". A single-node (num_cache_clusters = 1), non-replicated
# replication group is enough for a demo deployment; bump num_cache_clusters and add
# automatic_failover_enabled for real production HA.
#
# This is a replication_group (not the simpler aws_elasticache_cluster resource) purely
# because auth_token - and therefore any Redis authentication at all - is only
# supported on replication groups, and requires transit_encryption_enabled, which is
# also only a replication_group setting.

resource "aws_elasticache_subnet_group" "main" {
  name       = "${local.name_prefix}-redis-subnet-group"
  subnet_ids = aws_subnet.private_app[*].id
  tags       = { Name = "${local.name_prefix}-redis-subnet-group" }
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "${local.name_prefix}-redis"
  description          = "Shared Redis for gateway/identity/core-banking/notification"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type
  num_cache_clusters   = 1
  port                 = 6379
  parameter_group_name = "default.redis7"

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]

  transit_encryption_enabled = true
  auth_token                 = random_password.redis_auth_token.result

  tags = { Name = "${local.name_prefix}-redis" }
}
