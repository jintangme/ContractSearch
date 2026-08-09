package org.scse.cloudagent.knowledge;

import java.util.UUID;

public record KnowledgeChunkMatch(
        UUID chunkId,
        long contractId,
        String contractName,
        String chunkKind,
        String symbolName,
        String signature,
        String filePath,
        int lineStart,
        int lineEnd,
        String excerpt,
        String contentHash,
        String trustLevel,
        double textScore
) {
}
