# Single shared Redis instance for all three services (rate limiting on the gateway,
# token blacklist + OTP challenges on identity-service, idempotency-key dedup on
# core-banking) - see README's "Why everything moved to Redis". A single-node,
# non-replicated cluster is enough for a demo deployment; add a replication group
# with automatic failover for real production HA.

resource "aws_elasticache_subnet_group" "main" {
  name       = "${local.name_prefix}-redis-subnet-group"
  subnet_ids = aws_subnet.private_app[*].id
  tags       = { Name = "${local.name_prefix}-redis-subnet-group" }
}

resource "aws_elasticache_cluster" "main" {
  cluster_id           = "${local.name_prefix}-redis"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type
  num_cache_nodes      = 1
  port                 = 6379
  parameter_group_name = "default.redis7"

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]

  tags = { Name = "${local.name_prefix}-redis" }
}
