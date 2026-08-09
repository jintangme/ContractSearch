package org.scse.cloudagent.knowledge;

import java.util.List;
import java.util.Map;

public record ReferenceKnowledgeIngestionResponse(
        int received,
        int insertedSources,
        int insertedDocuments,
        int insertedChunks,
        int skippedSources,
        List<Map<String, Object>> outcomes
) {
    public ReferenceKnowledgeIngestionResponse {
        outcomes = List.copyOf(outcomes);
    }
}
