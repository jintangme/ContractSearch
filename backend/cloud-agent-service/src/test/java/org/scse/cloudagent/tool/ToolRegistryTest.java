package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {
    private final ToolInvocationValidator validator = new ToolInvocationValidator(new ObjectMapper());

    @Test
    void rejectsUnknownTool() {
        ToolRegistry registry = new ToolRegistry(List.of(new MockEchoTool()));
        assertThatThrownBy(() -> registry.require("shell.v1"))
                .isInstanceOf(UnknownToolException.class);
    }

    @Test
    void resolvesUnversionedModelAliasToCanonicalV1Tool() {
        ToolRegistry registry = new ToolRegistry(List.of(new MockEchoTool()));

        assertThat(registry.require("mock_echo").definition().name()).isEqualTo("mock_echo.v1");
    }

    @Test
    void rejectsInvalidJsonAndNonObjectArguments() {
        assertThatThrownBy(() -> validator.parseObject("{broken"))
                .isInstanceOf(InvalidToolArgumentsException.class);
        assertThatThrownBy(() -> validator.parseObject("[]"))
                .isInstanceOf(InvalidToolArgumentsException.class);
    }

    @Test
    void validatesVersionedToolName() {
        assertThat(new MockEchoTool().definition().name()).isEqualTo("mock_echo.v1");
        assertThatThrownBy(() -> new ToolDefinition("mock_echo", "bad", "{}",
                org.scse.cloudagent.domain.SideEffect.NONE, java.time.Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
