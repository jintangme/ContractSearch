package org.scse.cloudagent.controller;

import jakarta.validation.Valid;
import org.scse.cloudagent.knowledge.ReferenceKnowledgeIngestionRequest;
import org.scse.cloudagent.knowledge.ReferenceKnowledgeIngestionResponse;
import org.scse.cloudagent.knowledge.ReferenceKnowledgeIngestionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent/v1/knowledge/references")
@ConditionalOnProperty(prefix = "agent.knowledge", name = "enabled", havingValue = "true")
public class ReferenceKnowledgeIngestionController {
    private final ReferenceKnowledgeIngestionService ingestionService;

    public ReferenceKnowledgeIngestionController(ReferenceKnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ReferenceKnowledgeIngestionResponse ingest(
            @Valid @RequestBody ReferenceKnowledgeIngestionRequest request) {
        return ingestionService.ingest(request);
    }
}
