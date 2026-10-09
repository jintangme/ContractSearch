package org.scse.cloudgateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayJwtAuthFilterTest {
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final GatewayJwtAuthFilter filter = new GatewayJwtAuthFilter();
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(filter, "jwtTokenService", tokens);
    }

    @Test
    void anonymousPublicRequestCannotSupplyInternalIdentityOrTokens() {
        var request = MockServerHttpRequest.get("/api/contracts/1")
                .header("X-User-Id", "999")
                .header("X-Agent-Internal-Token", "caller-controlled")
                .header("X-Contract-Import-Token", "caller-controlled").build();
        forward(MockServerWebExchange.from(request));
        assertThat(forwarded.get().getRequest().getHeaders())
                .doesNotContainKeys("X-User-Id", "X-Agent-Internal-Token", "X-Contract-Import-Token");
    }

    @Test
    void invalidJwtOnPublicRequestDoesNotRetainCallerIdentity() {
        var request = MockServerHttpRequest.get("/api/contracts/1")
                .header("X-User-Id", "999").header("Authorization", "Bearer invalid").build();
        forward(MockServerWebExchange.from(request));
        assertThat(forwarded.get().getRequest().getHeaders()).doesNotContainKey("X-User-Id");
    }

    @Test
    void validJwtReplacesCallerIdentityWithItsSubject() {
        when(tokens.validateToken("valid")).thenReturn(true);
        when(tokens.getUserIdFromToken("valid")).thenReturn(7L);
        var request = MockServerHttpRequest.get("/api/contracts/1")
                .header("X-User-Id", "999").header("Authorization", "Bearer valid").build();
        forward(MockServerWebExchange.from(request));
        assertThat(forwarded.get().getRequest().getHeaders().get("X-User-Id")).containsExactly("7");
    }

    @Test
    void protectedRequestStillRequiresJwt() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/user/profile")
                .header("X-User-Id", "999"));
        forward(exchange);
        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void importCallbackIsNeverExposedThroughTheGateway() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/contracts/process")
                .header("Authorization", "Bearer valid").header("X-Contract-Import-Token", "internal-token"));
        forward(exchange);
        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void forward(MockServerWebExchange exchange) {
        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();
    }
}
