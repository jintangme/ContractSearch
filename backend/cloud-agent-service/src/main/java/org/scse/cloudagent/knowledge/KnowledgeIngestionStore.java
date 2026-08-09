package org.scse.cloudagent.knowledge;

import java.util.List;
import java.util.UUID;

public interface KnowledgeIngestionStore {
    KnowledgeSourceBatch loadContracts(long afterId, int limit);

    long resumeAfter(UUID jobId, long fallback);

    KnowledgeWriteResult persist(UUID jobId, List<PlannedKnowledgeContract> plans,
                                 long checkpoint, boolean hasMore);

    KnowledgeValidationResult validateCorpus();
}
