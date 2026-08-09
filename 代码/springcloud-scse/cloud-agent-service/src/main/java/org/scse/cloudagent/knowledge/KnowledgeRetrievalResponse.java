package org.scse.cloudagent.knowledge;

import java.util.List;
import java.util.Map;

public record KnowledgeRetrievalResponse(
        String query,
        List<Map<String, Object>> results,
        List<String> degradedChannels
) {
    public KnowledgeRetrievalResponse {
        results = List.copyOf(results);
        degradedChannels = List.copyOf(degradedChannels);
    }
}
