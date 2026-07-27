#!/usr/bin/env bash
# Tears down the main application infrastructure, then the tfstate bucket
# itself - run this instead of `terraform destroy` directly in
# infrastructure/terraform, so the bucket doesn't outlive the stack it was
# storing state for.
# Usage: ./teardown-state.sh
# Requires: aws CLI, terraform

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_DIR="$SCRIPT_DIR/../terraform-state"
MAIN_DIR="$SCRIPT_DIR/../terraform"

echo "=== Destroying the main application infrastructure ==="
cd "$MAIN_DIR"
terraform destroy

echo ""
echo "=== Destroying the tfstate bucket ==="
cd "$STATE_DIR"
terraform destroy
