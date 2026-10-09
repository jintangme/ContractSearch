package org.scse.cloudgateway.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class GatewayJwtAuthFilter implements GlobalFilter, Ordered {
    @Autowired
    private JwtTokenService jwtTokenService;

    // 手动创建日志记录器，替代 Lombok 的 @Slf4j
    private static final Logger log = LoggerFactory.getLogger(GatewayJwtAuthFilter.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Identity is established only from a validated JWT, including on public routes.
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove("X-Agent-Internal-Token");
                    headers.remove("X-Contract-Import-Token");
                }).build();
        exchange = exchange.mutate().request(sanitizedRequest).build();
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/api/contracts/process")) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        HttpMethod method = exchange.getRequest().getMethod();
        boolean isPublic = isPublicPath(path, method);

        String token = extractBearerToken(exchange.getRequest().getHeaders().getFirst("Authorization"));
        log.info("Gateway filter - Path: {}, Method: {}, IsPublic: {}, HasToken: {}",
                path, method, isPublic, token != null);

        // 公开路径：如果有 token 则尝试解析，无 token 则直接放行
        if (isPublic) {
            if (token != null && jwtTokenService.validateToken(token)) {
                Long userId = jwtTokenService.getUserIdFromToken(token);
                if (userId != null) {
                    log.info("Public path with valid token - Adding X-User-Id: {}", userId);
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.remove(USER_ID_HEADER);
                                headers.add(USER_ID_HEADER, String.valueOf(userId));
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                } else {
                    log.warn("Public path - Token valid but userId extraction failed");
                }
            } else {
                log.debug("Public path - No token or invalid token, proceeding as anonymous");
            }
            return chain.filter(exchange);
        }

        // 受保护路径：必须有有效 token
        if (token == null) {
            log.warn("Protected path - Token missing");
            return unauthorized(exchange, "Token missing");
        }
        if (!jwtTokenService.validateToken(token)) {
            log.warn("Protected path - Invalid or expired token");
            return unauthorized(exchange, "Invalid or expired token");
        }

        Long userId = jwtTokenService.getUserIdFromToken(token);
        if (userId == null) {
            log.warn("Protected path - Invalid token subject");
            return unauthorized(exchange, "Invalid token subject");
        }

        log.info("Protected path - Adding X-User-Id: {}", userId);
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.add(USER_ID_HEADER, String.valueOf(userId));
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublicPath(String path, HttpMethod method) {
        if (path == null) {
            return false;
        }
        if (path.startsWith("/api/auth/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.equals("/error")) {
            return true;
        }
        if (method == HttpMethod.POST && path.equals("/api/ai/chat")) {
            return true;
        }
        return method == HttpMethod.GET
                && (path.startsWith("/api/search/")
                || path.equals("/api/search")
                || path.startsWith("/api/groups/")
                || path.equals("/api/groups")
                || path.startsWith("/api/user/avatars/")
                || path.matches("^/api/contracts/\\d+$"));
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"status\":false,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
