package org.scse.cloudagent.knowledge;

import java.util.List;

public record KnowledgeIngestionPlan(
        SourceManifest manifest,
        List<KnowledgeChunk> chunks,
        String chunker,
        String compilerVersion,
        List<String> diagnostics
) {
    public KnowledgeIngestionPlan {
        chunks = List.copyOf(chunks);
        diagnostics = List.copyOf(diagnostics);
    }
}
