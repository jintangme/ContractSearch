package org.scse.cloudagent.knowledge;

import java.util.List;

public record KnowledgeIngestionPlan(
        SourceManifest manifest,
        List<KnowledgeChunk> chunks
) {
    public KnowledgeIngestionPlan {
        chunks = List.copyOf(chunks);
    }
}
