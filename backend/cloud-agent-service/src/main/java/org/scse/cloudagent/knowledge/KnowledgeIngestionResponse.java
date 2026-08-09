package org.scse.cloudagent.knowledge;

import java.util.List;
import java.util.UUID;

public record KnowledgeIngestionResponse(
        KnowledgeIngestionMode mode,
        UUID jobId,
        String corpusName,
        String corpusVersion,
        long afterId,
        long nextAfterId,
        boolean hasMore,
        int sourceCount,
        int documentCount,
        int chunkCount,
        int errorCount,
        List<KnowledgeContractOutcome> outcomes,
        KnowledgeValidationResult validation
) {
    public KnowledgeIngestionResponse {
        outcomes = List.copyOf(outcomes);
    }
}
