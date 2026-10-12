package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.scse.cloudagent.domain.SideEffect;
import org.scse.cloudagent.domain.TrustLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CompileContractTool implements AgentTool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "compile_contract.v1",
            "Compile one catalog contract in the isolated Solidity runtime. This cannot deploy or call a contract.",
            """
                    {"type":"object","additionalProperties":false,"required":["contractId"],"properties":{"contractId":{"type":"integer","minimum":1},"compilerVersion":{"type":"string","maxLength":40}}}
                    """.trim(),
            SideEffect.EXECUTE_SANDBOXED,
            Duration.ofSeconds(60)
    );
    private final ContractCatalogClient catalogClient;
    private final CompileRuntimeClient runtimeClient;

    public CompileContractTool(ContractCatalogClient catalogClient, CompileRuntimeClient runtimeClient) {
        this.catalogClient = catalogClient;
        this.runtimeClient = runtimeClient;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(JsonNode arguments, ToolExecutionContext context) {
        ToolArgumentRules.rejectUnknown(arguments, "contractId", "compilerVersion");
        long contractId = ToolArgumentRules.requiredPositiveLong(arguments, "contractId");
        JsonNode versionNode = arguments.get("compilerVersion");
        if (versionNode != null && !versionNode.isNull() && !versionNode.isTextual()) {
            throw new InvalidToolArgumentsException("compilerVersion must be a string");
        }
        String requestedVersion = versionNode != null && !versionNode.isNull()
                ? versionNode.asText().trim() : "auto";
        if (requestedVersion.length() > 40 || !requestedVersion.matches("auto|v?[0-9]+\\.[0-9]+(\\.[0-9]+)?([+.-][A-Za-z0-9.-]+)?")) {
            throw new InvalidToolArgumentsException("compilerVersion is invalid");
        }
        Map<String, Object> contract = catalogClient.getContract(contractId, context.userId());
        String name = String.valueOf(contract.getOrDefault("contractName", "Contract"));
        String source = String.valueOf(contract.getOrDefault("sourceCode", ""));
        String sourceHash = context.workspace().observeSource(contractId, source, false).sourceHash();
        Map<String, Object> compile = runtimeClient.compile(source, safeFileName(name), requestedVersion);
        Map<String, Object> output = new LinkedHashMap<>(compile);
        output.put("contractId", contractId);
        output.put("sourceHash", sourceHash);
        String summary = compileSummary(compile);
        EvidenceDraft evidence = new EvidenceDraft("SOLIDITY_COMPILE", "compile:contract:" + contractId,
                "Compile result for " + name, summary, TrustLevel.TOOL_GENERATED,
                Map.of("contractId", contractId, "sourceHash", sourceHash, "sandboxed", true, "contentBoundary", "UNTRUSTED"));
        return new ToolExecutionResult(output, List.of(evidence));
    }

    private String compileSummary(Map<String, Object> result) {
        boolean success = Boolean.TRUE.equals(result.get("success"));
        StringBuilder summary = new StringBuilder(success ? "Compilation succeeded." : "Compilation failed.");
        Object version = result.get("selectedCompilerVersion");
        if (version != null) summary.append(" Compiler: ").append(version).append('.');
        appendDiagnostics(summary, " Errors: ", result.get("errors"));
        appendDiagnostics(summary, " Warnings: ", result.get("warnings"));
        return summary.length() <= 6000 ? summary.toString() : summary.substring(0, 6000);
    }

    private void appendDiagnostics(StringBuilder target, String prefix, Object value) {
        if (!(value instanceof List<?> diagnostics) || diagnostics.isEmpty()) return;
        target.append(prefix);
        for (Object diagnostic : diagnostics) {
            if (diagnostic instanceof Map<?, ?> map && map.get("message") != null) {
                target.append(' ').append(map.get("message"));
            }
        }
    }

    private String safeFileName(String contractName) {
        String safe = contractName.replaceAll("[^A-Za-z0-9_]", "_");
        return (safe.isBlank() ? "Contract" : safe) + ".sol";
    }
}
