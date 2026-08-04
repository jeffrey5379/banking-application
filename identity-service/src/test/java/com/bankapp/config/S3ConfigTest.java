package com.bankapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Verifies the nested-placeholder default on S3Config.presignEndpoint - the actual mechanism this
// fix depends on, independent of the AWS SDK/network. See S3Config's field comment for why
// presignEndpoint must be able to fall back to kyc.storage.endpoint's own resolved value (not just
// a hardcoded default) when it isn't set separately.
class S3ConfigTest {

    private static final String PLACEHOLDER = "${kyc.storage.presign-endpoint:${kyc.storage.endpoint:}}";

    @Test
    void presignEndpointNotSetSeparately_fallsBackToWhateverEndpointResolvesTo() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("kyc.storage.endpoint", "http://minio:9000")));

        assertThat(env.resolvePlaceholders(PLACEHOLDER)).isEqualTo("http://minio:9000");
    }

    @Test
    void presignEndpointSetSeparately_winsOverEndpoint() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "kyc.storage.endpoint", "http://minio:9000",
                "kyc.storage.presign-endpoint", "http://localhost:9000"
        )));

        assertThat(env.resolvePlaceholders(PLACEHOLDER)).isEqualTo("http://localhost:9000");
    }

    @Test
    void neitherSet_resolvesToBlank_matchingProdsNoOverrideBehavior() {
        StandardEnvironment env = new StandardEnvironment();

        assertThat(env.resolvePlaceholders(PLACEHOLDER)).isEmpty();
    }
}
