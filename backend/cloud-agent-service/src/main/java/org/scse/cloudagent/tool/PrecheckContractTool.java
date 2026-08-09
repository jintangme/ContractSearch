package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.SideEffect;
import org.scse.cloudagent.domain.TrustLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PrecheckContractTool implements AgentTool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "precheck_contract.v1",
            "Run the existing ContractSearch security precheck for one catalog contract without saving a user record.",
            """
                    {"type":"object","additionalProperties":false,"required":["contractId"],"properties":{"contractId":{"type":"integer","minimum":1}}}
                    """.trim(),
            SideEffect.EXECUTE_SANDBOXED,
            Duration.ofSeconds(60)
    );
    private final ContractCatalogClient catalogClient;
    private final PrecheckClient precheckClient;
    private final AgentProperties properties;

    public PrecheckContractTool(ContractCatalogClient catalogClient, PrecheckClient precheckClient,
                                AgentProperties properties) {
        this.catalogClient = catalogClient;
        this.precheckClient = precheckClient;
        this.properties = properties;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(JsonNode arguments, ToolExecutionContext context) {
        ToolArgumentRules.rejectUnknown(arguments, "contractId");
        long contractId = ToolArgumentRules.requiredPositiveLong(arguments, "contractId");
        Map<String, Object> contract = catalogClient.getContract(contractId, context.userId());
        String name = String.valueOf(contract.getOrDefault("contractName", "Contract " + contractId));
        String source = String.valueOf(contract.getOrDefault("sourceCode", ""));
        Map<String, Object> precheck = precheckClient.precheck(name, source, properties.model(), context.userId());
        Map<String, Object> output = new LinkedHashMap<>(precheck);
        output.put("contractId", contractId);
        String excerpt = summary(precheck);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contractId", contractId);
        metadata.put("saved", false);
        metadata.put("contentBoundary", "UNTRUSTED");
        if (precheck.get("riskLevel") != null) metadata.put("riskLevel", precheck.get("riskLevel"));
        if (precheck.get("modelUsed") != null) metadata.put("modelUsed", precheck.get("modelUsed"));
        EvidenceDraft evidence = new EvidenceDraft("CONTRACT_PRECHECK", "precheck:contract:" + contractId,
                "Precheck result for " + name, excerpt, TrustLevel.TOOL_GENERATED, metadata);
        return new ToolExecutionResult(output, List.of(evidence));
    }

    private String summary(Map<String, Object> result) {
        StringBuilder value = new StringBuilder();
        append(value, result.get("conclusion"));
        append(value, result.get("summary"));
        Object issues = result.get("issues");
        if (issues instanceof List<?> list && !list.isEmpty()) value.append(" Issues: ").append(list);
        return value.length() <= 6000 ? value.toString() : value.substring(0, 6000);
    }

    private void append(StringBuilder target, Object value) {
        if (value == null || value.toString().isBlank()) return;
        if (!target.isEmpty()) target.append(' ');
        target.append(value);
    }
}
