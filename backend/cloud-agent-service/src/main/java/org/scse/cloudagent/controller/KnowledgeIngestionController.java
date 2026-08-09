package org.scse.cloudagent.controller;

import jakarta.validation.Valid;
import org.scse.cloudagent.knowledge.KnowledgeIngestionRequest;
import org.scse.cloudagent.knowledge.KnowledgeIngestionResponse;
import org.scse.cloudagent.knowledge.KnowledgeIngestionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent/v1/knowledge/ingestions")
@ConditionalOnProperty(prefix = "agent.knowledge", name = "enabled", havingValue = "true")
public class KnowledgeIngestionController {
    private final KnowledgeIngestionService ingestionService;

    public KnowledgeIngestionController(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public KnowledgeIngestionResponse execute(@Valid @RequestBody KnowledgeIngestionRequest request) {
        return ingestionService.execute(request);
    }
}
