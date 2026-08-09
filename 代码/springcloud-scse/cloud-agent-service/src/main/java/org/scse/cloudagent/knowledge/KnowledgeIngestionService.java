package org.scse.cloudagent.knowledge;

import org.scse.cloudagent.config.AgentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "agent.knowledge", name = "enabled", havingValue = "true")
public class KnowledgeIngestionService {
    private final AgentProperties properties;
    private final KnowledgeIngestionStore store;
    private final KnowledgeIngestionPlanner planner;

    public KnowledgeIngestionService(AgentProperties properties, KnowledgeIngestionStore store,
                                     KnowledgeIngestionPlanner planner) {
        this.properties = properties;
        this.store = store;
        this.planner = planner;
    }

    public KnowledgeIngestionResponse execute(KnowledgeIngestionRequest request) {
        AgentProperties.Knowledge knowledge = properties.knowledge();
        if (request.mode() == KnowledgeIngestionMode.VALIDATE) {
            KnowledgeValidationResult validation = store.validateCorpus();
            return new KnowledgeIngestionResponse(request.mode(), request.jobId(),
                    knowledge.corpusName(), knowledge.corpusVersion(), 0, 0, false,
                    0, 0, 0, 0, List.of(), validation);
        }

        int limit = Math.min(request.limit(), knowledge.maxBatchSize());
        long requestedAfter = request.afterId() == null ? 0L : request.afterId();
        long afterId = request.mode() == KnowledgeIngestionMode.APPLY
                ? store.resumeAfter(request.jobId(), requestedAfter) : requestedAfter;
        KnowledgeSourceBatch batch = store.loadContracts(afterId, limit);
        List<PlannedKnowledgeContract> plans = new ArrayList<>();
        List<KnowledgeContractOutcome> outcomes = new ArrayList<>();
        int documents = 0;
        int chunks = 0;
        int errors = 0;
        long nextAfter = afterId;
        for (ContractSourceRecord source : batch.sources()) {
            KnowledgeIngestionPlan plan = request.useAst()
                    ? planner.planWithAst("contract:" + source.id(), source.id(), source.sourceCode())
                    : planner.dryRun("contract:" + source.id(), source.id(), source.sourceCode());
            plans.add(new PlannedKnowledgeContract(source, plan));
            documents += plan.manifest().files().size();
            chunks += plan.chunks().size();
            if (plan.manifest().parserStatus() != ParserStatus.PARSED
                    || plan.chunker().contains("fallback")) errors++;
            outcomes.add(new KnowledgeContractOutcome(source.id(), source.contractName(),
                    plan.manifest().parserStatus(), plan.chunker(), plan.manifest().files().size(),
                    plan.chunks().size(), plan.manifest().normalizedHash()));
            nextAfter = source.id();
        }

        UUID jobId = request.jobId();
        int persistedSources = plans.size();
        if (request.mode() == KnowledgeIngestionMode.APPLY) {
            KnowledgeWriteResult write = store.persist(jobId, plans, nextAfter, batch.hasMore());
            jobId = write.jobId();
            persistedSources = write.sourceCount();
            documents = write.documentCount();
            chunks = write.chunkCount();
            errors = write.errorCount();
        }
        return new KnowledgeIngestionResponse(request.mode(), jobId,
                knowledge.corpusName(), knowledge.corpusVersion(), afterId, nextAfter,
                batch.hasMore(), persistedSources, documents, chunks, errors, outcomes, null);
    }
}
