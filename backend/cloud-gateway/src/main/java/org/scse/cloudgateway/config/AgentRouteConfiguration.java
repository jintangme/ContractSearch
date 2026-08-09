package org.scse.cloudgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentRouteConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "agent.route", name = "enabled", havingValue = "true")
    RouteLocator agentRoute(RouteLocatorBuilder builder,
                            @Value("${agent.route.internal-token:}") String internalToken) {
        if (internalToken.length() < 24) {
            throw new IllegalStateException("AGENT_INTERNAL_TOKEN must contain at least 24 characters when agent route is enabled");
        }
        return builder.routes()
                .route("cloud-agent-service", route -> route
                        .path("/api/agent/**")
                        .filters(filters -> filters
                                .removeRequestHeader("X-Agent-Internal-Token")
                                .addRequestHeader("X-Agent-Internal-Token", internalToken))
                        .uri("lb://cloud-agent-service"))
                .build();
    }
}
