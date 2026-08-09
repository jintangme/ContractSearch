package org.scse.cloudagent.knowledge;

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
        String contentHash
) {
}
