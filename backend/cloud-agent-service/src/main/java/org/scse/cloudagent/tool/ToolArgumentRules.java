package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

final class ToolArgumentRules {
    private ToolArgumentRules() {
    }

    static void rejectUnknown(JsonNode arguments, String... allowedFields) {
        Set<String> allowed = Set.of(allowedFields);
        Set<String> unknown = new HashSet<>();
        arguments.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) unknown.add(field);
        });
        if (!unknown.isEmpty()) {
            throw new InvalidToolArgumentsException("Unknown tool arguments: " + String.join(", ", unknown));
        }
    }

    static long requiredPositiveLong(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new InvalidToolArgumentsException(field + " must be a positive integer");
        }
        return value.asLong();
    }
}
