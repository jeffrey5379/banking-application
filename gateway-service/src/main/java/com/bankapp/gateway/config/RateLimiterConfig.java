package com.bankapp.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    private static final String CLOUDFRONT_VIEWER_ADDRESS_HEADER = "CloudFront-Viewer-Address";

    // Fallback
    private final RemoteAddressResolver remoteAddressResolver = XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = resolveClientIp(exchange);
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : "unknown-route";
            return Mono.just(ip + ":" + routeId);
        };
    }

    // CloudFront sets this to the real viewer's own address
    private String resolveClientIp(ServerWebExchange exchange) {
        String viewerAddress = exchange.getRequest().getHeaders().getFirst(CLOUDFRONT_VIEWER_ADDRESS_HEADER);
        if (viewerAddress != null && !viewerAddress.isBlank()) {
            return stripPort(viewerAddress.trim());
        }
        InetSocketAddress remoteAddress = remoteAddressResolver.resolve(exchange);
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    // CloudFront-Viewer-Address is "ip:port" for IPv4 or "[ipv6]:port" for IPv6
    private String stripPort(String viewerAddress) {
        if (viewerAddress.startsWith("[")) {
            int closeBracket = viewerAddress.indexOf(']');
            return closeBracket > 0 ? viewerAddress.substring(1, closeBracket) : viewerAddress;
        }
        int lastColon = viewerAddress.lastIndexOf(':');
        return lastColon > 0 ? viewerAddress.substring(0, lastColon) : viewerAddress;
    }
}
