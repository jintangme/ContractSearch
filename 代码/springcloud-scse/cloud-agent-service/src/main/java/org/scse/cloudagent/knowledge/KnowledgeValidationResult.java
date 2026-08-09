package org.scse.cloudagent.knowledge;

public record KnowledgeValidationResult(
        long sources,
        long documents,
        long chunks,
        long searchRows,
        long orphanDocuments,
        long orphanChunks,
        long missingChunkSearchRows,
        long duplicateSourceIdentities
) {
    public boolean valid() {
        return orphanDocuments == 0 && orphanChunks == 0
                && missingChunkSearchRows == 0 && duplicateSourceIdentities == 0;
    }
}
