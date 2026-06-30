#!/usr/bin/env bash
# Build Angular and deploy to S3 + invalidate CloudFront
# Usage: ./deploy-frontend.sh
# Requires: aws CLI, node/npm, terraform

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TF_DIR="$SCRIPT_DIR/../terraform"
FRONTEND_DIR="$SCRIPT_DIR/../../frontend"

echo "=== Reading Terraform outputs ==="
AWS_REGION=$(cd "$TF_DIR" && terraform output -raw aws_region)
BUCKET=$(cd "$TF_DIR" && terraform output -raw s3_bucket_name)
DISTRIBUTION_ID=$(cd "$TF_DIR" && terraform output -raw cloudfront_distribution_id)
APP_URL=$(cd "$TF_DIR" && terraform output -raw app_url)

echo "Region:   $AWS_REGION"
echo "Bucket:   $BUCKET"
echo "CF dist:  $DISTRIBUTION_ID"
echo ""

echo "=== Building Angular ==="
cd "$FRONTEND_DIR"
npm run build -- --configuration production
BUILD_DIR="dist/bank-frontend/browser"

echo "=== Syncing static assets to S3 (long-term cache) ==="
# Hashed assets (JS/CSS bundles) are immutably cached
aws s3 sync "$BUILD_DIR" "s3://$BUCKET" \
  --delete \
  --region "$AWS_REGION" \
  --cache-control "public,max-age=31536000,immutable" \
  --exclude "index.html" \
  --exclude "*.json"

echo "=== Uploading index.html (no-cache) ==="
# index.html must never be cached — it's the entry point for Angular routing
aws s3 cp "$BUILD_DIR/index.html" "s3://$BUCKET/index.html" \
  --region "$AWS_REGION" \
  --cache-control "no-cache,no-store,must-revalidate"

# Upload JSON files (ngsw, manifests) without long cache
if ls "$BUILD_DIR"/*.json 1>/dev/null 2>&1; then
  aws s3 sync "$BUILD_DIR" "s3://$BUCKET" \
    --region "$AWS_REGION" \
    --include "*.json" \
    --exclude "*" \
    --cache-control "no-cache"
fi

echo "=== Invalidating CloudFront cache ==="
INVALIDATION_ID=$(aws cloudfront create-invalidation \
  --distribution-id "$DISTRIBUTION_ID" \
  --paths "/*" \
  --query 'Invalidation.Id' \
  --output text)

echo ""
echo "✓ Frontend deployed!"
echo "  Invalidation: $INVALIDATION_ID"
echo "  App URL:      $APP_URL"
