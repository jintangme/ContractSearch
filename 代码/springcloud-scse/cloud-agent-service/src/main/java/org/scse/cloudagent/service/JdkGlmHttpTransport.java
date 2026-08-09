package org.scse.cloudagent.service;

import org.scse.cloudagent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class JdkGlmHttpTransport implements GlmHttpTransport {
    private final HttpClient client;

    public JdkGlmHttpTransport(AgentProperties properties) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.provider().timeoutMs()))
                .build();
    }

    @Override
    public Response send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }
}
