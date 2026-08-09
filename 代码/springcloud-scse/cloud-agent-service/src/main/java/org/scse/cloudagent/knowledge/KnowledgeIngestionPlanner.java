package org.scse.cloudagent.knowledge;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeIngestionPlanner {
    private final SoliditySourceRouter sourceRouter;
    private final SolidityChunker chunker;

    public KnowledgeIngestionPlanner(SoliditySourceRouter sourceRouter, SolidityChunker chunker) {
        this.sourceRouter = sourceRouter;
        this.chunker = chunker;
    }

    public KnowledgeIngestionPlan dryRun(String sourceRef, Long existingContractId, String sourceCode) {
        SourceManifest manifest = sourceRouter.route(sourceRef, existingContractId, sourceCode);
        List<KnowledgeChunk> chunks = new ArrayList<>();
        if (manifest.parserStatus() == ParserStatus.PARSED) {
            for (NormalizedSourceFile file : manifest.files()) {
                chunks.addAll(chunker.chunk(file));
            }
        }
        return new KnowledgeIngestionPlan(manifest, chunks);
    }
}
