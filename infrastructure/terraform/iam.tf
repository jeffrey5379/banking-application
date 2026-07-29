data "aws_caller_identity" "current" {}

# ── ECS Task Execution Role ──────────────────────────────────────────────────
# Used by the ECS agent (all three services share it) to pull images from ECR and
# inject secrets - not the app's own runtime identity, see the per-service task
# roles below for that.

resource "aws_iam_role" "ecs_execution" {
  name = "${local.name_prefix}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_execution_secrets" {
  name = "${local.name_prefix}-ecs-secrets"
  role = aws_iam_role.ecs_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue"]
      Resource = concat(
        [for s in aws_secretsmanager_secret.db_password : s.arn],
        [
          aws_secretsmanager_secret.jwt_secret.arn,
          aws_secretsmanager_secret.mail_username.arn,
          aws_secretsmanager_secret.mail_password.arn,
          aws_secretsmanager_secret.mongo_uri.arn,
        ]
      )
    }]
  })
}

# ── ECS Task Roles ───────────────────────────────────────────────────────────
# One per service (the application process's own runtime identity), so each
# service only gets the AWS permissions it actually needs - e.g. only
# identity-service can touch the KYC document bucket.

locals {
  ecs_services = ["gateway", "core-banking", "identity", "debit-eligibility-mock", "notification"]
}

resource "aws_iam_role" "ecs_task" {
  for_each = toset(local.ecs_services)

  name = "${local.name_prefix}-ecs-task-role-${each.key}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "ecs_task_logs" {
  for_each = aws_iam_role.ecs_task

  name = "${local.name_prefix}-ecs-task-logs-${each.key}"
  role = each.value.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
      Resource = "${aws_cloudwatch_log_group.ecs.arn}:*"
    }]
  })
}

# identity-service only: read/write/presign access to its own KYC document bucket.
# No s3:CreateBucket - Terraform (s3_kyc.tf) already creates the bucket, so
# KycStorageInitializer's headBucket call always succeeds and createBucket is
# never reached in prod.
resource "aws_iam_role_policy" "ecs_task_identity_kyc_bucket" {
  name = "${local.name_prefix}-ecs-task-identity-kyc-bucket"
  role = aws_iam_role.ecs_task["identity"].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketCors", "s3:PutBucketCors"]
        Resource = aws_s3_bucket.kyc_documents.arn
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = "${aws_s3_bucket.kyc_documents.arn}/*"
      }
    ]
  })
}
