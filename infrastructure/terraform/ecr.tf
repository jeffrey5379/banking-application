locals {
  ecr_repos = {
    core-banking           = "core-banking-service" # this repo's original `backend`, see README Architecture
    identity               = "identity-service"
    gateway                = "gateway-service"
    debit-eligibility-mock = "debit-eligibility-mock" # WireMock stub, see backend/wiremock/Dockerfile
  }
}

resource "aws_ecr_repository" "this" {
  for_each = local.ecr_repos

  name                 = "${local.name_prefix}-${each.key}"
  image_tag_mutability = "MUTABLE"

  # This project's own workflow tears the whole stack down and back up regularly -
  # force_delete lets `terraform destroy` remove each repo even though it holds
  # pushed images. A long-lived deployment would likely want this false, backed
  # by real deletion protection instead.
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = { Name = "${local.name_prefix}-${each.key}" }
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images, expire the rest"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
