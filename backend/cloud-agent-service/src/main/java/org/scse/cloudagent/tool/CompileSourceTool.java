package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scse.cloudagent.domain.SideEffect;
import org.scse.cloudagent.domain.TrustLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact-source compilation only: never installs a plan or executes ABI checks. */
@Component
public class CompileSourceTool implements AgentTool {
    public static final String DEFAULT_COMPILER_VERSION = "0.8.35";
    private final CompileRuntimeClient runtime;
    private final ObjectMapper mapper;

    public CompileSourceTool(CompileRuntimeClient runtime, ObjectMapper mapper) {
        this.runtime = runtime; this.mapper = mapper;
    }

    @Override public ToolDefinition definition() {
        return new ToolDefinition("compile_source.v1",
                "Compile a complete source without a behavior plan. Defaults to Solidity 0.8.35; supply the task's exact compiler version when different. No deployment or behavior tests. Same exact bytes and actual compiler version reuse an immutable receipt. A compilation pass does not establish task correctness. Returns compiler diagnostics without repairing or replacing source.",
                """
                {"type":"object","additionalProperties":false,"required":["sourceCode"],"properties":{"sourceCode":{"type":"string","minLength":1,"maxLength":24000},"compilerVersion":{"type":"string","pattern":"^[0-9]+[.][0-9]+[.][0-9]+$","maxLength":40}}}
                """, SideEffect.EXECUTE_SANDBOXED, Duration.ofSeconds(30));
    }

    @Override public ToolExecutionResult execute(JsonNode args, ToolExecutionContext context) {
        ToolArgumentRules.rejectUnknown(args, "sourceCode", "compilerVersion");
        JsonNode node = args.path("sourceCode");
        if (!node.isTextual() || node.asText().isBlank() || node.asText().length() > 24000)
            throw new InvalidToolArgumentsException("Source must contain 1 to 24000 characters");
        String source = node.asText();
        String version = args.has("compilerVersion") ? args.path("compilerVersion").asText("") : DEFAULT_COMPILER_VERSION;
        if (version.length() > 40 || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
            throw new InvalidToolArgumentsException("compilerVersion must be an exact numeric major.minor.patch version");
        AgentWorkspace workspace = context.workspace();
        var cached = workspace.compilationForSource(source, version);
        if (cached != null) return new ToolExecutionResult(output(cached, false), List.of());
        workspace.recordDraft(source);
        Map<String, Object> compile;
        try { compile = runtime.compile(source, "Contract.sol", version, context.remainingMillis()); }
        catch (AgentToolException exception) {
            if (Thread.currentThread().isInterrupted()) throw exception;
            return unavailable(source, version, exception.errorCode() + ": " + exception.getMessage());
        }
        if (!version.equals(compile.get("selectedCompilerVersion")))
            return unavailable(source, version, "Runtime did not use the requested compiler version");
        var receipt = workspace.recordCompilation(source, version, mapper.valueToTree(compile));
        Map<String, Object> metadata = Map.of("sourceHash", receipt.sourceHash(), "compilerVersion", version,
                "validationScope", "COMPILATION_ONLY", "compilationSucceeded", receipt.succeeded(),
                "contentBoundary", "UNTRUSTED");
        String excerpt = mapper.valueToTree(compile).toString();
        EvidenceDraft evidence = new EvidenceDraft("SOLIDITY_SOURCE_COMPILE", "compile:source:" + receipt.sourceHash(),
                "Exact-source compilation only", excerpt.substring(0, Math.min(6000, excerpt.length())),
                TrustLevel.TOOL_GENERATED, metadata);
        return new ToolExecutionResult(output(receipt, true), List.of(evidence));
    }

    public static Map<String, Object> output(AgentWorkspace.Compilation receipt, boolean performed) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("sourceHash", receipt.sourceHash()); output.put("compilerVersion", receipt.compilerVersion());
        output.put("status", receipt.succeeded() ? "COMPILED" : "COMPILATION_FAILED");
        output.put("compile", receipt.receipt()); output.put("validationScope", "COMPILATION_ONLY");
        output.put("compilationPerformed", performed); output.put("receiptReused", !performed);
        output.put("behaviorChecksPerformed", false);
        return Map.copyOf(output);
    }

    private ToolExecutionResult unavailable(String source, String version, String message) {
        return new ToolExecutionResult(Map.of("sourceHash", SourceHashes.sha256(source), "compilerVersion", version,
                "status", "UNAVAILABLE", "message", message, "validationScope", "COMPILATION_ONLY",
                "compilationPerformed", false, "receiptReused", false, "behaviorChecksPerformed", false), List.of());
    }
}
