# KYC document/selfie uploads (identity-service). Private bucket - the browser uploads
# directly to it via presigned URLs (KycService#requestUploadUrl), never through
# CloudFront/S3 public access. See README's "KYC document upload" section.

resource "aws_s3_bucket" "kyc_documents" {
  bucket = "${local.name_prefix}-kyc-documents-${data.aws_caller_identity.current.account_id}"

  # This project's own workflow tears the whole stack down and back up regularly -
  # force_destroy lets `terraform destroy` remove the bucket even though it holds
  # uploaded document/selfie objects. A long-lived deployment would likely want
  # this false, backed by real deletion protection instead.
  force_destroy = true

  tags = { Name = "${local.name_prefix}-kyc-documents" }
}

resource "aws_s3_bucket_public_access_block" "kyc_documents" {
  bucket                  = aws_s3_bucket.kyc_documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "kyc_documents" {
  bucket = aws_s3_bucket.kyc_documents.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Allows the browser to PUT directly to a presigned URL. identity-service's own
# KycStorageInitializer also tries to set this at boot (defensively, and so local
# MinIO/prod S3 stay on the same code path) - on real S3 that call succeeds and this
# resource's configuration is what it converges to; unlike MinIO, S3 doesn't reject it.
resource "aws_s3_bucket_cors_configuration" "kyc_documents" {
  bucket = aws_s3_bucket.kyc_documents.id

  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = ["https://${aws_cloudfront_distribution.main.domain_name}"]
    allowed_headers = ["*"]
  }
}
