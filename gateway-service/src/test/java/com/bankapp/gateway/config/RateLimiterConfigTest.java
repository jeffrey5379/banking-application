package com.bankapp.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

// Regression coverage for a real bug found via manual testing: RedisRateLimiter derives its Redis
// key purely from what the KeyResolver returns, with no route id mixed in on the framework's
// side. An IP-only key resolver would put every route behind the same client IP into one shared
// token bucket, silently defeating the per-route replenishRate/burstCapacity configured in
// application.yml (e.g. the strict identity-login limit would be consumed by unrelated traffic to
// other routes, and vice versa).
class RateLimiterConfigTest {

    private final RateLimiterConfig config = new RateLimiterConfig();
    private final KeyResolver resolver = config.ipKeyResolver();

    @Test
    void sameIpDifferentRoutes_produceDifferentKeys() {
        String loginKey = resolveKey(exchangeFor("1.2.3.4", "identity-login", null));
        String coreBankingKey = resolveKey(exchangeFor("1.2.3.4", "core-banking", null));

        assertThat(loginKey).isNotEqualTo(coreBankingKey);
        assertThat(loginKey).isEqualTo("1.2.3.4:identity-login");
        assertThat(coreBankingKey).isEqualTo("1.2.3.4:core-banking");
    }

    @Test
    void sameIpSameRoute_producesTheSameKeyOnEveryCall() {
        ServerWebExchange exchange = exchangeFor("5.6.7.8", "identity-auth", null);

        assertThat(resolveKey(exchange)).isEqualTo(resolveKey(exchange));
    }

    @Test
    void differentIpsSameRoute_produceDifferentKeys() {
        String first = resolveKey(exchangeFor("1.1.1.1", "identity-kyc", null));
        String second = resolveKey(exchangeFor("2.2.2.2", "identity-kyc", null));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void noRouteAttribute_fallsBackToUnknownRoute() {
        String key = resolveKey(exchangeFor("9.9.9.9", null, null));

        assertThat(key).isEqualTo("9.9.9.9:unknown-route");
    }

    @Test
    void xForwardedForHeader_takesPrecedenceOverRemoteAddress() {
        String key = resolveKey(exchangeFor("10.0.0.1", "core-banking", "203.0.113.5, 10.0.0.1"));

        assertThat(key).isEqualTo("203.0.113.5:core-banking");
    }

    @Test
    void blankXForwardedForHeader_fallsBackToRemoteAddress() {
        String key = resolveKey(exchangeFor("10.0.0.1", "core-banking", "  "));

        assertThat(key).isEqualTo("10.0.0.1:core-banking");
    }

    private String resolveKey(ServerWebExchange exchange) {
        return resolver.resolve(exchange).block();
    }

    private ServerWebExchange exchangeFor(String remoteIp, String routeId, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/whatever")
                .remoteAddress(new InetSocketAddress(remoteIp, 12345));
        if (forwardedFor != null) {
            builder = builder.header("X-Forwarded-For", forwardedFor);
        }

        MockServerWebExchange exchange = MockServerWebExchange.from(builder);
        if (routeId != null) {
            Route route = Route.async()
                    .id(routeId)
                    .uri(URI.create("http://localhost:8081"))
                    .predicate(e -> true)
                    .build();
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        }
        return exchange;
    }
}
