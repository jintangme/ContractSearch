package org.scse.cloudagent.controller;

import jakarta.validation.Valid;
import org.scse.cloudagent.knowledge.KnowledgeRetrievalRequest;
import org.scse.cloudagent.knowledge.KnowledgeRetrievalResponse;
import org.scse.cloudagent.knowledge.KnowledgeRetrievalService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent/v1/knowledge/retrieval")
@ConditionalOnProperty(prefix = "agent.knowledge", name = "enabled", havingValue = "true")
public class KnowledgeRetrievalController {
    private final KnowledgeRetrievalService retrievalService;

    public KnowledgeRetrievalController(KnowledgeRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping
    public KnowledgeRetrievalResponse retrieve(
            @Valid @RequestBody KnowledgeRetrievalRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") long userId) {
        return retrievalService.retrieve(request.query(), request.limit(), userId);
    }
}
