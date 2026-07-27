package com.bankapp.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    // Referenced from application.yml route filters via "#{@ipKeyResolver}". Keyed by IP + route
    // id, not just IP: RedisRateLimiter's Redis key is derived purely from what this resolver
    // returns, with no route id mixed in on its side - if every route resolved to the same key
    // (e.g. just the IP), they'd all share one Redis bucket and whichever route's config ran
    // last would clobber the others' token count, even though each route declares its own
    // replenishRate/burstCapacity. Including the route id gives each route its own independent
    // bucket per IP, which is what "stricter limit on login, looser elsewhere" actually requires.
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = clientIp(exchange.getRequest());
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : "unknown-route";
            return Mono.just(ip + ":" + routeId);
        };
    }

    private String clientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
