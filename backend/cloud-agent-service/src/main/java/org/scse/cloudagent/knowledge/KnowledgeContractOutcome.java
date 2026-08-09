package org.scse.cloudagent.knowledge;

public record KnowledgeContractOutcome(
        long contractId,
        String contractName,
        ParserStatus parserStatus,
        String chunker,
        int fileCount,
        int chunkCount,
        String normalizedHash
) {
}
