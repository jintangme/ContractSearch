package org.scse.cloudagent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ToolInvocationValidator {
    private final ObjectMapper objectMapper;

    public ToolInvocationValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parseObject(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new InvalidToolArgumentsException("Tool arguments must be a JSON object");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw new InvalidToolArgumentsException("Tool arguments are not valid JSON");
        }
    }
}
