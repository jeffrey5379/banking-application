package com.bankapp.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

// End-to-end coverage for the route table in application.yml. Spring Cloud Gateway picks the
// FIRST matching route, so a broad predicate listed too early (or a typo in path ordering) would
// silently swallow requests meant for a more specific route - a mistake pure config review won't
// always catch. These tests stand up two throwaway HTTP servers in place of identity-service and
// core-banking and assert which one actually receives each request path.
//
// Redis is deliberately pointed at an unreachable local port rather than relying on whatever
// Redis state happens to exist on the machine running the tests: RedisRateLimiter fails OPEN when
// Redis is unreachable (already verified manually - see the comment in application.yml), so
// routing behaves identically with or without Redis and the tests stay deterministic. Actually
// enforcing the per-route rate limits (e.g. login being stricter than other auth paths) needs a
// live Redis and isn't covered here.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    private static final List<String> identityRequests = new CopyOnWriteArrayList<>();
    private static final List<String> coreBankingRequests = new CopyOnWriteArrayList<>();
    private static final List<String> notificationRequests = new CopyOnWriteArrayList<>();
    private static final HttpServer identityServer;
    private static final HttpServer coreBankingServer;
    private static final HttpServer notificationServer;

    // Static initializer (not @BeforeAll) so both stub servers are guaranteed up before
    // @DynamicPropertySource resolves - that method is invoked while the ApplicationContext is
    // being prepared, which happens before JUnit's @BeforeAll/@BeforeEach callbacks run.
    static {
        try {
            identityServer = stubServer("identity-ok", identityRequests);
            coreBankingServer = stubServer("core-banking-ok", coreBankingRequests);
            notificationServer = stubServer("notification-ok", notificationRequests);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("IDENTITY_SERVICE_URL", () -> "http://127.0.0.1:" + identityServer.getAddress().getPort());
        registry.add("CORE_BANKING_SERVICE_URL", () -> "http://127.0.0.1:" + coreBankingServer.getAddress().getPort());
        registry.add("NOTIFICATION_SERVICE_URL", () -> "http://127.0.0.1:" + notificationServer.getAddress().getPort());
        registry.add("REDIS_HOST", () -> "127.0.0.1");
        registry.add("REDIS_PORT", () -> "1");
    }

    @AfterAll
    static void stopUpstreams() {
        identityServer.stop(0);
        coreBankingServer.stop(0);
        notificationServer.stop(0);
    }

    @LocalServerPort
    private int gatewayPort;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        identityRequests.clear();
        coreBankingRequests.clear();
        notificationRequests.clear();
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    void authLoginPath_routesToIdentityService() {
        client.get().uri("/api/auth/login")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("identity-ok");

        assertThat(identityRequests).containsExactly("/api/auth/login");
        assertThat(coreBankingRequests).isEmpty();
    }

    @Test
    void otherAuthPaths_alsoRouteToIdentityService() {
        client.get().uri("/api/auth/register")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("identity-ok");

        assertThat(identityRequests).containsExactly("/api/auth/register");
    }

    @Test
    void kycPaths_routeToIdentityServiceRatherThanTheCoreBankingCatchAll() {
        client.get().uri("/api/kyc/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("identity-ok");

        assertThat(identityRequests).containsExactly("/api/kyc/status");
        assertThat(coreBankingRequests).isEmpty();
    }

    @Test
    void everythingElseUnderApi_fallsThroughToTheCoreBankingCatchAll() {
        client.get().uri("/api/accounts/123")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("core-banking-ok");

        assertThat(coreBankingRequests).containsExactly("/api/accounts/123");
        assertThat(identityRequests).isEmpty();
    }

    @Test
    void adminMessagesPath_routesToNotificationServiceNotIdentityOrCoreBanking() {
        client.post().uri("/api/admin/messages")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("notification-ok");

        assertThat(notificationRequests).containsExactly("/api/admin/messages");
        assertThat(identityRequests).isEmpty();
        assertThat(coreBankingRequests).isEmpty();
    }

    @Test
    void otherAdminPaths_routeToIdentityServiceRatherThanTheCoreBankingCatchAll() {
        client.get().uri("/api/admin/users")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("identity-ok");

        assertThat(identityRequests).containsExactly("/api/admin/users");
        assertThat(coreBankingRequests).isEmpty();
        assertThat(notificationRequests).isEmpty();
    }

    @Test
    void allowedBrowserOrigin_getsAccessControlAllowOriginHeaderBack() {
        client.get().uri("/api/accounts/123")
                .header("Origin", "http://localhost:4200")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200");
    }

    private static HttpServer stubServer(String marker, List<String> receivedPaths) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                receivedPaths.add(exchange.getRequestURI().getPath());
                byte[] body = marker.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }
}
