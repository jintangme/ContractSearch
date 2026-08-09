package org.scse.cloudagent.knowledge;

import java.util.UUID;

public record KnowledgeWriteResult(
        UUID jobId,
        int sourceCount,
        int documentCount,
        int chunkCount,
        int errorCount
) {
}
