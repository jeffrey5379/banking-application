#!/usr/bin/env bash
# Deploy Spring Boot to ECS Fargate
# Usage: ./deploy-backend.sh [image-tag]
# Requires: aws CLI, docker, mvn, jq, terraform

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TF_DIR="$SCRIPT_DIR/../terraform"
BACKEND_DIR="$SCRIPT_DIR/../../backend"

IMAGE_TAG="${1:-$(git -C "$BACKEND_DIR" rev-parse --short HEAD 2>/dev/null || echo "latest")}"

echo "=== Reading Terraform outputs ==="
AWS_REGION=$(cd "$TF_DIR" && terraform output -raw aws_region)
ECR_URL=$(cd "$TF_DIR" && terraform output -raw ecr_repository_url)
CLUSTER=$(cd "$TF_DIR" && terraform output -raw ecs_cluster_name)
SERVICE=$(cd "$TF_DIR" && terraform output -raw ecs_service_name)

echo "Region:  $AWS_REGION"
echo "ECR:     $ECR_URL"
echo "Cluster: $CLUSTER / $SERVICE"
echo "Tag:     $IMAGE_TAG"
echo ""

echo "=== Building JAR ==="
cd "$BACKEND_DIR"
mvn clean package -DskipTests -q
echo "JAR built."

echo "=== Authenticating with ECR ==="
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin "$ECR_URL"

echo "=== Building Docker image ==="
docker build -t "$ECR_URL:$IMAGE_TAG" -t "$ECR_URL:latest" .

echo "=== Pushing to ECR ==="
docker push "$ECR_URL:$IMAGE_TAG"
docker push "$ECR_URL:latest"

echo "=== Triggering ECS deployment ==="
aws ecs update-service \
  --cluster "$CLUSTER" \
  --service "$SERVICE" \
  --force-new-deployment \
  --region "$AWS_REGION" \
  --output json | jq -r '"Service: " + .service.serviceArn'

echo ""
echo "✓ Deployment triggered. Monitor progress:"
echo "  https://$AWS_REGION.console.aws.amazon.com/ecs/v2/clusters/$CLUSTER/services/$SERVICE/deployments"
