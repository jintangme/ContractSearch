package org.scse.cloudagent.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public record KnowledgeIngestionRequest(
        KnowledgeIngestionMode mode,
        Long afterId,
        @Min(1) @Max(200) Integer limit,
        Boolean useAst,
        UUID jobId
) {
    public KnowledgeIngestionRequest {
        mode = mode == null ? KnowledgeIngestionMode.DRY_RUN : mode;
        if (afterId != null && afterId < 0) {
            afterId = 0L;
        }
        limit = limit == null ? 10 : limit;
        useAst = useAst == null || useAst;
    }
}
