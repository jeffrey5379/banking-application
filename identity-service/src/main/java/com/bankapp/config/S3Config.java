package com.bankapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

// Builds the S3 client/presigner used to store KYC document/selfie uploads
@Configuration
public class S3Config {

    @Value("${kyc.storage.endpoint:}")
    private String endpoint;

    // Separate from `endpoint` on purpose: this one is baked into presigned URLs handed to the
    // browser, which runs outside the backend's own network
    @Value("${kyc.storage.presign-endpoint:${kyc.storage.endpoint:}}")
    private String presignEndpoint;

    @Value("${kyc.storage.region}")
    private String region;

    @Value("${kyc.storage.access-key:}")
    private String accessKey;

    @Value("${kyc.storage.secret-key:}")
    private String secretKey;

    @Value("${kyc.storage.path-style-access:false}")
    private boolean pathStyleAccess;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(s3Configuration());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        AwsCredentialsProvider credentials = staticCredentialsOrNull();
        if (credentials != null) {
            builder.credentialsProvider(credentials);
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .serviceConfiguration(s3Configuration());
        if (!presignEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(presignEndpoint));
        }
        AwsCredentialsProvider credentials = staticCredentialsOrNull();
        if (credentials != null) {
            builder.credentialsProvider(credentials);
        }
        return builder.build();
    }

    // Pinned to an AWS SDK version that predates the "default request checksums" feature (added
    // for S3 write operations like PutBucketCors around SDK 2.24+) - that feature attaches a
    // CRC32 checksum trailer MinIO answers with "501 Not Implemented - A header you provided
    // implies functionality that is not implemented" for, and no per-request/per-client override
    // in later SDK versions actually avoids it for operations the API marks checksum-required.
    // Real AWS S3 works fine with either the old or new SDK behavior.
    private S3Configuration s3Configuration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess)
                .build();
    }

    // Static test credentials locally (MinIO); null in prod so the SDK falls back to its default
    // credentials chain (the task's IAM role) instead of a hardcoded key.
    private AwsCredentialsProvider staticCredentialsOrNull() {
        if (accessKey.isBlank()) {
            return null;
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
