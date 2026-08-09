package org.scse.cloudagent.knowledge;

import java.util.List;

public interface KnowledgeRetrievalRepository {
    List<KnowledgeChunkMatch> searchChunks(String query, int limit);
}
