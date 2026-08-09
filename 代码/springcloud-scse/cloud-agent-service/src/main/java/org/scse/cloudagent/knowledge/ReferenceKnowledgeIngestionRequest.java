package org.scse.cloudagent.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReferenceKnowledgeIngestionRequest(
        @NotEmpty @Size(max = 25) List<@Valid ReferenceKnowledgeDocument> documents
) {
    public ReferenceKnowledgeIngestionRequest {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}
