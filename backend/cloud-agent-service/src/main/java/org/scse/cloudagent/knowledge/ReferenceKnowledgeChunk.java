package org.scse.cloudagent.knowledge;

public record ReferenceKnowledgeChunk(
        int ordinal,
        String heading,
        int lineStart,
        int lineEnd,
        int charStart,
        int charEnd,
        String content,
        String contentHash
) {
}
