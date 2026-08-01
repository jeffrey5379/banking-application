#!/usr/bin/env bash
# Deploy one of the five services to ECS Fargate
# Usage: ./deploy-service.sh <identity|core-banking|gateway|notification|debit-eligibility-mock> [image-tag]
# Requires: aws CLI, docker, jq, terraform (+ mvn for the four Spring Boot services)

set -euo pipefail

SERVICE="${1:?Usage: deploy-service.sh <identity|core-banking|gateway|notification|debit-eligibility-mock> [image-tag]}"

# Whether this service needs a Maven build first, and where its Dockerfile lives.
NEEDS_MAVEN_BUILD=true
case "$SERVICE" in
  identity)     SERVICE_DIR_NAME="identity-service" ;;
  core-banking) SERVICE_DIR_NAME="core-banking" ;;
  gateway)      SERVICE_DIR_NAME="gateway-service" ;;
  notification) SERVICE_DIR_NAME="notification-service" ;;
  debit-eligibility-mock)
    SERVICE_DIR_NAME="core-banking/wiremock" # just packages the WireMock stub, no JVM app to build
    NEEDS_MAVEN_BUILD=false
    ;;
  *)
    echo "Unknown service: $SERVICE (expected identity, core-banking, gateway, notification, or debit-eligibility-mock)" >&2
    exit 1
    ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TF_DIR="$SCRIPT_DIR/../terraform"
SERVICE_DIR="$SCRIPT_DIR/../../$SERVICE_DIR_NAME"

IMAGE_TAG="${2:-$(git -C "$SERVICE_DIR" rev-parse --short HEAD 2>/dev/null || echo "latest")}"

echo "=== Reading Terraform outputs ==="
AWS_REGION=$(cd "$TF_DIR" && terraform output -raw aws_region)
ECR_URL=$(cd "$TF_DIR" && terraform output -json ecr_repository_urls | jq -r ".\"$SERVICE\"")
CLUSTER=$(cd "$TF_DIR" && terraform output -raw ecs_cluster_name)
ECS_SERVICE=$(cd "$TF_DIR" && terraform output -json ecs_service_names | jq -r ".\"$SERVICE\"")

echo "Service: $SERVICE ($SERVICE_DIR_NAME)"
echo "Region:  $AWS_REGION"
echo "ECR:     $ECR_URL"
echo "Cluster: $CLUSTER / $ECS_SERVICE"
echo "Tag:     $IMAGE_TAG"
echo ""

if [ "$NEEDS_MAVEN_BUILD" = true ]; then
  echo "=== Building JAR ==="
  cd "$SERVICE_DIR"
  mvn clean package -DskipTests -q
  echo "JAR built."
else
  cd "$SERVICE_DIR"
fi

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
  --service "$ECS_SERVICE" \
  --force-new-deployment \
  --region "$AWS_REGION" \
  --output json | jq -r '"Service: " + .service.serviceArn'

echo ""
echo "✓ Deployment triggered. Monitor progress:"
echo "  https://$AWS_REGION.console.aws.amazon.com/ecs/v2/clusters/$CLUSTER/services/$ECS_SERVICE/deployments"
