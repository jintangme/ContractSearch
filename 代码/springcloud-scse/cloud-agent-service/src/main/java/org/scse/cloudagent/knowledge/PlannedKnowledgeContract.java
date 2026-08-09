package org.scse.cloudagent.knowledge;

public record PlannedKnowledgeContract(
        ContractSourceRecord source,
        KnowledgeIngestionPlan plan
) {
}
