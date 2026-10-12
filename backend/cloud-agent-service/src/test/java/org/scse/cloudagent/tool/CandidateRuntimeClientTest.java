package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.scse.cloudagent.config.AgentProperties;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class CandidateRuntimeClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void lostSandboxResponseGetsOneFreshTransportAttempt() throws Exception {
        var count = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/runtime/validation-plan", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (count.incrementAndGet() == 1) { exchange.close(); return; }
            byte[] data = "{\"planHash\":\"test\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data); exchange.close();
        }); server.start();
        try {
            var result = client(server.getAddress().getPort()).validatePlan(mapper.createObjectNode());
            assertThat(result.path("planHash").asText()).isEqualTo("test");
            assertThat(result.path("transportRetries").asInt()).isOne();
            assertThat(count.get()).isEqualTo(2);
        } finally { server.stop(0); }
    }
    @Test void declaredInputErrorsAreNotTransportRetried() throws Exception {
        var count = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/runtime/validation-plan", exchange -> {
            exchange.getRequestBody().readAllBytes(); count.incrementAndGet();
            byte[] data = "{\"message\":\"bad plan\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, data.length);
            exchange.getResponseBody().write(data); exchange.close();
        }); server.start();
        try {
            assertThatThrownBy(() -> client(server.getAddress().getPort()).validatePlan(mapper.createObjectNode()))
                    .isInstanceOf(InvalidToolArgumentsException.class).hasMessageContaining("bad plan");
            assertThat(count.get()).isOne();
        } finally { server.stop(0); }
    }
    private CandidateRuntimeClient client(int port) {
        var properties = new AgentProperties(true, "local-test", "scripted", null, null, null,
                new AgentProperties.Dependencies(null, "http://127.0.0.1:" + port, 1000, 3000));
        return new CandidateRuntimeClient(mapper, properties);
    }
}
