package org.scse.cloudagent.knowledge;

import java.util.List;

public record KnowledgeSourceBatch(List<ContractSourceRecord> sources, boolean hasMore) {
    public KnowledgeSourceBatch {
        sources = List.copyOf(sources);
    }
}
