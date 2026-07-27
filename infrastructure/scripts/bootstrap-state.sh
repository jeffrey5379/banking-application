#!/usr/bin/env bash
# Creates the S3 bucket the main Terraform config stores its state in (a
# separate bootstrap module - see infrastructure/terraform-state/), then
# initializes the main config against it. Run this once per create/destroy
# cycle, before `terraform plan`/`apply` in infrastructure/terraform.
# Usage: ./bootstrap-state.sh
# Requires: aws CLI, terraform

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_DIR="$SCRIPT_DIR/../terraform-state"
MAIN_DIR="$SCRIPT_DIR/../terraform"

echo "=== Provisioning the tfstate S3 bucket ==="
cd "$STATE_DIR"
terraform init -input=false
terraform apply -auto-approve

BUCKET=$(terraform output -raw bucket_name)
REGION=$(terraform output -raw region)

echo ""
echo "Bucket: $BUCKET"
echo "Region: $REGION"

echo ""
echo "=== Initializing the main config against s3://$BUCKET ==="
cd "$MAIN_DIR"
terraform init -input=false -reconfigure \
  -backend-config="bucket=$BUCKET" \
  -backend-config="region=$REGION"

echo ""
echo "✓ Ready. Next:"
echo "  cd infrastructure/terraform"
echo "  terraform plan"
echo "  terraform apply"
