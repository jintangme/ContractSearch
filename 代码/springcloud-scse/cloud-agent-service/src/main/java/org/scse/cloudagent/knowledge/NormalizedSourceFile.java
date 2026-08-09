package org.scse.cloudagent.knowledge;

public record NormalizedSourceFile(
        String path,
        String rawContent,
        String normalizedContent,
        String rawHash,
        String normalizedHash
) {
}
