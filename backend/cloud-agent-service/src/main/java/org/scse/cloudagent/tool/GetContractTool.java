package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.scse.cloudagent.domain.SideEffect;
import org.scse.cloudagent.domain.TrustLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GetContractTool implements AgentTool {
    private static final int MAX_MODEL_SOURCE_CHARS = 120_000;
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "get_contract.v1",
            "Read one contract and its Solidity source from the existing ContractSearch catalog by numeric ID.",
            """
                    {"type":"object","additionalProperties":false,"required":["contractId"],"properties":{"contractId":{"type":"integer","minimum":1}}}
                    """.trim(),
            SideEffect.READ,
            Duration.ofSeconds(15)
    );
    private final ContractCatalogClient client;

    public GetContractTool(ContractCatalogClient client) {
        this.client = client;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(JsonNode arguments, ToolExecutionContext context) {
        ToolArgumentRules.rejectUnknown(arguments, "contractId");
        long contractId = ToolArgumentRules.requiredPositiveLong(arguments, "contractId");
        Map<String, Object> contract = client.getContract(contractId, context.userId());
        String name = String.valueOf(contract.getOrDefault("contractName", "Contract " + contractId));
        String description = String.valueOf(contract.getOrDefault("description", ""));
        String source = String.valueOf(contract.getOrDefault("sourceCode", ""));
        String excerpt = truncate((description.isBlank() ? "" : description + "\n") + source, 6000);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contractId", contractId);
        String sourceHash = context.workspace().observeSource(contractId, source, source.length() <= MAX_MODEL_SOURCE_CHARS).sourceHash();
        metadata.put("sourceHash", sourceHash);
        metadata.put("contentBoundary", "UNTRUSTED");
        if (contract.containsKey("compilerVersion")) metadata.put("compilerVersion", contract.get("compilerVersion"));
        Map<String, Object> boundedOutput = new LinkedHashMap<>(contract);
        boundedOutput.put("sourceHash", sourceHash);
        if (source.length() > MAX_MODEL_SOURCE_CHARS) {
            boundedOutput.put("sourceCode", source.substring(0, MAX_MODEL_SOURCE_CHARS));
            boundedOutput.put("sourceTruncated", true);
            metadata.put("sourceTruncated", true);
        } else {
            boundedOutput.put("sourceTruncated", false);
        }
        EvidenceDraft evidence = new EvidenceDraft("CONTRACT_SOURCE", "contract:" + contractId, name, excerpt,
                TrustLevel.COMMUNITY, metadata);
        return new ToolExecutionResult(boundedOutput, java.util.List.of(evidence));
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
