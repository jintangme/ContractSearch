package org.scse.cloudagent.tool;

import java.util.List;
import java.util.Map;

public record ToolExecutionResult(Map<String, Object> output, List<EvidenceDraft> evidence) {
    public ToolExecutionResult {
        output = output == null ? Map.of() : Map.copyOf(output);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
