resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${local.name_prefix}"
  retention_in_days = 30
  tags              = { Name = "${local.name_prefix}-logs" }
}

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = { Name = "${local.name_prefix}-cluster" }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

locals {
  redis_host = aws_elasticache_cluster.main.cache_nodes[0].address
  redis_port = tostring(aws_elasticache_cluster.main.cache_nodes[0].port)

  # Internal DNS names other services reach this one at, via ECS Service Connect -
  # see networking.tf's aws_service_discovery_http_namespace.
  identity_internal_url     = "http://identity-service:${var.identity_container_port}"
  core_banking_internal_url = "http://core-banking:${var.core_banking_container_port}"
  notification_internal_url = "http://notification-service:${var.notification_container_port}"
  frontend_origin           = "https://${aws_cloudfront_distribution.main.domain_name}"

  # Real vendor if one's configured, otherwise the bundled WireMock mock - see
  # variables.tf's debit_eligibility_url and the debit-eligibility-mock resources below.
  debit_eligibility_url = var.debit_eligibility_url != "" ? var.debit_eligibility_url : "http://debit-eligibility-mock:${var.debit_eligibility_mock_port}"
}

# ── identity-service ─────────────────────────────────────────────────────────
# Owns Users/Auth/JWT issuance/2FA/OTP/KYC (incl. document/selfie uploads to
# aws_s3_bucket.kyc_documents) - see README Architecture.

resource "aws_ecs_task_definition" "identity" {
  family                   = "${local.name_prefix}-identity"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_cpu
  memory                   = var.ecs_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task["identity"].arn

  container_definitions = jsonencode([{
    name      = "identity"
    image     = "${aws_ecr_repository.this["identity"].repository_url}:latest"
    essential = true

    portMappings = [{
      name          = "identity-service"
      containerPort = var.identity_container_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "DB_HOST", value = aws_db_instance.this["identity"].address },
      { name = "DB_PORT", value = tostring(aws_db_instance.this["identity"].port) },
      { name = "DB_NAME", value = var.identity_db_name },
      { name = "DB_USERNAME", value = var.db_username },
      { name = "REDIS_HOST", value = local.redis_host },
      { name = "REDIS_PORT", value = local.redis_port },
      { name = "MAIL_HOST", value = var.mail_host },
      { name = "MAIL_PORT", value = tostring(var.mail_port) },
      { name = "MAIL_FROM", value = var.mail_from },
      { name = "KYC_STORAGE_BUCKET", value = aws_s3_bucket.kyc_documents.bucket },
      { name = "KYC_STORAGE_REGION", value = var.aws_region },
      { name = "KYC_STORAGE_ALLOWED_ORIGIN", value = local.frontend_origin },
    ]

    secrets = [
      { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password["identity"].arn },
      { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_secret.arn },
      { name = "MAIL_USERNAME", valueFrom = aws_secretsmanager_secret.mail_username.arn },
      { name = "MAIL_PASSWORD", valueFrom = aws_secretsmanager_secret.mail_password.arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "identity"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://localhost:${var.identity_container_port}/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = { Name = "${local.name_prefix}-identity-task-def" }
}

resource "aws_ecs_service" "identity" {
  name            = "${local.name_prefix}-identity"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.identity.arn
  desired_count   = var.ecs_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private_app[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn

    service {
      port_name = "identity-service"
      client_alias {
        port     = var.identity_container_port
        dns_name = "identity-service"
      }
    }
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [
    aws_iam_role_policy_attachment.ecs_execution_managed,
    aws_db_instance.this["identity"],
    aws_elasticache_cluster.main,
  ]

  # Ignore task definition changes after initial deploy; updates are done via
  # infrastructure/scripts/deploy-service.sh
  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = { Name = "${local.name_prefix}-identity-service" }
}

# ── core-banking-service (this repo's `backend`) ─────────────────────────────
# Owns Account/Operation/transfers/exchange - no Users table, gates money
# movement on identity-service's KYC status via internal HTTP calls.

resource "aws_ecs_task_definition" "core_banking" {
  family                   = "${local.name_prefix}-core-banking"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_cpu
  memory                   = var.ecs_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task["core-banking"].arn

  container_definitions = jsonencode([{
    name      = "core-banking"
    image     = "${aws_ecr_repository.this["core-banking"].repository_url}:latest"
    essential = true

    portMappings = [{
      name          = "core-banking"
      containerPort = var.core_banking_container_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "DB_HOST", value = aws_db_instance.this["core-banking"].address },
      { name = "DB_PORT", value = tostring(aws_db_instance.this["core-banking"].port) },
      { name = "DB_NAME", value = var.core_banking_db_name },
      { name = "DB_USERNAME", value = var.db_username },
      { name = "REDIS_HOST", value = local.redis_host },
      { name = "REDIS_PORT", value = local.redis_port },
      { name = "IDENTITY_SERVICE_URL", value = local.identity_internal_url },
      { name = "DEBIT_ELIGIBILITY_URL", value = local.debit_eligibility_url },
    ]

    secrets = [
      { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password["core-banking"].arn },
      { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_secret.arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "core-banking"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://localhost:${var.core_banking_container_port}/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = { Name = "${local.name_prefix}-core-banking-task-def" }
}

resource "aws_ecs_service" "core_banking" {
  name            = "${local.name_prefix}-core-banking"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.core_banking.arn
  desired_count   = var.ecs_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private_app[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn

    service {
      port_name = "core-banking"
      client_alias {
        port     = var.core_banking_container_port
        dns_name = "core-banking"
      }
    }
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Best-effort startup ordering only: this makes Terraform create the
  # core-banking ECS service after identity-service's, but ECS has no built-in
  # way to wait for a dependency's container to be fully healthy (only for the
  # AWS resource to exist) - core-banking's DataSeeder calls identity-service
  # over HTTP at boot and will crash-and-restart a few times if identity-service
  # isn't ready yet, self-healing once it is. Same caveat as local dev - see
  # README's "Running Locally" ordering notes.
  depends_on = [
    aws_ecs_service.identity,
    aws_ecs_service.debit_eligibility_mock,
    aws_iam_role_policy_attachment.ecs_execution_managed,
    aws_db_instance.this["core-banking"],
    aws_elasticache_cluster.main,
  ]

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = { Name = "${local.name_prefix}-core-banking-service" }
}

# ── debit-eligibility-mock ────────────────────────────────────────────────────
# The same WireMock stub used for local dev (backend/wiremock/mappings), packaged
# as its own image (backend/wiremock/Dockerfile) and deployed as a lightweight
# internal-only service - see variables.tf's debit_eligibility_url for how to
# swap in a real vendor instead once one exists.

resource "aws_ecs_task_definition" "debit_eligibility_mock" {
  family                   = "${local.name_prefix}-debit-eligibility-mock"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.debit_eligibility_mock_cpu
  memory                   = var.debit_eligibility_mock_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task["debit-eligibility-mock"].arn

  container_definitions = jsonencode([{
    name      = "debit-eligibility-mock"
    image     = "${aws_ecr_repository.this["debit-eligibility-mock"].repository_url}:latest"
    essential = true
    command   = ["--port", tostring(var.debit_eligibility_mock_port)]

    portMappings = [{
      name          = "debit-eligibility-mock"
      containerPort = var.debit_eligibility_mock_port
      protocol      = "tcp"
    }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "debit-eligibility-mock"
      }
    }
  }])

  tags = { Name = "${local.name_prefix}-debit-eligibility-mock-task-def" }
}

resource "aws_ecs_service" "debit_eligibility_mock" {
  name            = "${local.name_prefix}-debit-eligibility-mock"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.debit_eligibility_mock.arn
  desired_count   = var.ecs_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private_app[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn

    service {
      port_name = "debit-eligibility-mock"
      client_alias {
        port     = var.debit_eligibility_mock_port
        dns_name = "debit-eligibility-mock"
      }
    }
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [aws_iam_role_policy_attachment.ecs_execution_managed]

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = { Name = "${local.name_prefix}-debit-eligibility-mock-service" }
}

# ── notification-service ─────────────────────────────────────────────────────
# WebFlux/Netty - relays core-banking's balance-change events (published to the
# "account-events" Redis channel, see backend's BalanceEventPublisher) and its own
# message-created events (see MessageEventPublisher) to the browser over SSE.
# Owns its message store in DocumentDB (docdb.tf) - the one service on Mongo
# instead of Postgres/RDS, see README Architecture. MessageSeeder also calls
# identity-service over HTTP at boot (same pattern as core-banking's DataSeeder)
# to resolve the demo users it seeds messages for.

resource "aws_ecs_task_definition" "notification" {
  family                   = "${local.name_prefix}-notification"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_cpu
  memory                   = var.ecs_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task["notification"].arn

  container_definitions = jsonencode([{
    name      = "notification"
    image     = "${aws_ecr_repository.this["notification"].repository_url}:latest"
    essential = true

    portMappings = [{
      name          = "notification-service"
      containerPort = var.notification_container_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "REDIS_HOST", value = local.redis_host },
      { name = "REDIS_PORT", value = local.redis_port },
      { name = "IDENTITY_SERVICE_URL", value = local.identity_internal_url },
    ]

    secrets = [
      { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_secret.arn },
      { name = "MONGO_URI", valueFrom = aws_secretsmanager_secret.mongo_uri.arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "notification"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://localhost:${var.notification_container_port}/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = { Name = "${local.name_prefix}-notification-task-def" }
}

resource "aws_ecs_service" "notification" {
  name            = "${local.name_prefix}-notification"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.notification.arn
  desired_count   = var.ecs_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private_app[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn

    service {
      port_name = "notification-service"
      client_alias {
        port     = var.notification_container_port
        dns_name = "notification-service"
      }
    }
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Best-effort startup ordering only, same caveat as core-banking's own depends_on above:
  # MessageSeeder calls identity-service over HTTP at boot and will crash-and-restart a few
  # times if identity-service isn't ready yet, self-healing once it is.
  depends_on = [
    aws_ecs_service.identity,
    aws_iam_role_policy_attachment.ecs_execution_managed,
    aws_elasticache_cluster.main,
    aws_docdb_cluster_instance.notification,
  ]

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = { Name = "${local.name_prefix}-notification-service" }
}

# ── gateway-service ──────────────────────────────────────────────────────────
# Thin reverse proxy - the only service reachable from the ALB/CloudFront.

resource "aws_ecs_task_definition" "gateway" {
  family                   = "${local.name_prefix}-gateway"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_cpu
  memory                   = var.ecs_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task["gateway"].arn

  container_definitions = jsonencode([{
    name      = "gateway"
    image     = "${aws_ecr_repository.this["gateway"].repository_url}:latest"
    essential = true

    portMappings = [{
      name          = "gateway"
      containerPort = var.gateway_container_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "REDIS_HOST", value = local.redis_host },
      { name = "REDIS_PORT", value = local.redis_port },
      { name = "IDENTITY_SERVICE_URL", value = local.identity_internal_url },
      { name = "CORE_BANKING_SERVICE_URL", value = local.core_banking_internal_url },
      { name = "NOTIFICATION_SERVICE_URL", value = local.notification_internal_url },
      { name = "FRONTEND_ORIGIN", value = local.frontend_origin },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "gateway"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://localhost:${var.gateway_container_port}/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = { Name = "${local.name_prefix}-gateway-task-def" }
}

resource "aws_ecs_service" "gateway" {
  name            = "${local.name_prefix}-gateway"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.gateway.arn
  desired_count   = var.ecs_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private_app[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.gateway.arn
    container_name   = "gateway"
    container_port   = var.gateway_container_port
  }

  # Client-only Service Connect: no "service" block, since nothing reaches the
  # gateway via Service Connect (the ALB routes to it directly by IP) - this
  # just lets the gateway's own outbound calls resolve identity-service/
  # core-banking's Service Connect DNS aliases.
  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [
    aws_ecs_service.identity,
    aws_ecs_service.core_banking,
    aws_ecs_service.notification,
    aws_lb_listener.http,
    aws_iam_role_policy_attachment.ecs_execution_managed,
  ]

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = { Name = "${local.name_prefix}-gateway-service" }
}
