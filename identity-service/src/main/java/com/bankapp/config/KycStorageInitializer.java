package com.bankapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

// Creates the KYC document bucket on startup if it doesn't already exist, and best-effort
// configures its browser-upload CORS rule via the S3 API. Runs identically against MinIO (local)
// and real AWS S3 (prod) - only S3Config's endpoint/credentials differ between the two.
//
// MinIO (Community Edition) doesn't implement PutBucketCors - it always answers 501 "Not
// Implemented" for it regardless of SDK version or request shape (verified: identical failure on
// AWS SDK 2.20.0 through 2.31.78), because MinIO expects CORS to be configured server-wide via
// its own MINIO_API_CORS_ALLOW_ORIGIN environment variable instead of per-bucket through the S3
// API - see README for the docker run flag. So this call is expected to fail locally; it's kept
// (rather than removed) because real AWS S3 *does* implement PutBucketCors correctly, and a
// real deployment would still want it self-configured rather than provisioned by hand. A real
// deployment would provision this via Terraform instead of app-startup code either way (see
// README's "Known simplifications").

@Component
@RequiredArgsConstructor
@Slf4j
public class KycStorageInitializer implements ApplicationRunner {

    private final S3Client s3Client;

    @Value("${kyc.storage.bucket}")
    private String bucket;

    @Value("${kyc.storage.allowed-origin}")
    private String allowedOrigin;

    @Override
    public void run(ApplicationArguments args) {
        ensureBucketExists();
        ensureCorsConfigured();
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created KYC document bucket: {}", bucket);
        }
    }

    private void ensureCorsConfigured() {
        try {
            s3Client.putBucketCors(PutBucketCorsRequest.builder()
                    .bucket(bucket)
                    .corsConfiguration(CORSConfiguration.builder()
                            .corsRules(CORSRule.builder()
                                    .allowedMethods("PUT")
                                    .allowedOrigins(allowedOrigin)
                                    .allowedHeaders("*")
                                    .build())
                            .build())
                    .build());
        } catch (S3Exception e) {
            log.warn("PutBucketCors failed (expected on MinIO Community Edition, which doesn't "
                    + "implement it - set MINIO_API_CORS_ALLOW_ORIGIN on the MinIO container "
                    + "instead, see README): {}", e.getMessage());
        }
    }
}
