package org.scse.cloudagent.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeRetrievalRequest(
        @NotBlank @Size(min = 2, max = 500) String query,
        @Min(1) @Max(10) Integer limit
) {
    public KnowledgeRetrievalRequest {
        query = query == null ? null : query.trim();
        limit = limit == null ? 5 : limit;
    }
}
