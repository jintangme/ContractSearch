package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.scse.cloudagent.domain.SideEffect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class MockEchoTool implements AgentTool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "mock_echo.v1",
            "Phase 1 deterministic no-side-effect tool used to validate orchestration contracts.",
            """
                    {"type":"object","additionalProperties":false,"required":["message"],"properties":{"message":{"type":"string","minLength":1,"maxLength":2000}}}
                    """.trim(),
            SideEffect.NONE,
            Duration.ofSeconds(2)
    );

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public Map<String, Object> invoke(JsonNode arguments) {
        JsonNode message = arguments.get("message");
        if (message == null || !message.isTextual() || message.asText().isBlank()) {
            throw new InvalidToolArgumentsException("message is required");
        }
        return Map.of("echo", message.asText(), "mock", true);
    }
}
