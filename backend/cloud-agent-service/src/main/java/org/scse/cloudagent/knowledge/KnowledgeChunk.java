package org.scse.cloudagent.knowledge;

import java.util.Map;

public record KnowledgeChunk(
        int ordinal,
        ChunkKind kind,
        String symbolName,
        String signature,
        String filePath,
        int lineStart,
        int lineEnd,
        int charStart,
        int charEnd,
        String content,
        String contentHash,
        Map<String, Object> metadata
) {
    public KnowledgeChunk {
        metadata = Map.copyOf(metadata);
    }
}
