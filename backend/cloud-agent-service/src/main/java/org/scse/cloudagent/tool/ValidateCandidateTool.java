package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.scse.cloudagent.domain.SideEffect;
import org.scse.cloudagent.domain.TrustLevel;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ValidateCandidateTool implements AgentTool {
    private final CandidateRuntimeClient runtime;
    public ValidateCandidateTool(CandidateRuntimeClient runtime) { this.runtime = runtime; }
    @Override public ToolDefinition definition() {
        return new ToolDefinition("validate_candidate.v1", "Compile candidate Solidity and execute the frozen visible checks in a fresh isolated EVM. Returns actual per-requirement feedback. Identical source under the same current frozen plan reuses its original receipt without another execution or new evidence, including failed checks; executionPerformed and receiptReused distinguish these cases. Candidate checks share the total tool/model budget; RUN_VERIFICATION_STATE reports the remaining cap for new candidates. Omit planHash to use the current server-frozen plan; an explicitly supplied hash must match. Does not save, publish or deploy to a shared/user chain.", """
                {"type":"object","additionalProperties":false,"required":["sourceCode"],"properties":{"sourceCode":{"type":"string","minLength":1,"maxLength":24000},"planHash":{"type":"string"}}}
                """, SideEffect.EXECUTE_SANDBOXED, Duration.ofSeconds(30));
    }
    @Override public ToolExecutionResult execute(JsonNode args, ToolExecutionContext context) {
        ToolArgumentRules.rejectUnknown(args, "sourceCode", "planHash");
        AgentWorkspace workspace = context.workspace();
        workspace.requireCurrentPlan(args.has("planHash") ? args.path("planHash").asText() : workspace.planHash());
        JsonNode text = args.path("sourceCode");
        if (!text.isTextual() || text.asText().isBlank() || text.asText().length() > 24000) throw new InvalidToolArgumentsException("Candidate must contain 1 to 24000 source characters");
        AgentWorkspace.Candidate existing = workspace.candidateForSource(text.asText());
        if (existing != null) {
            if (!existing.sourceCode().equals(text.asText())) throw new InvalidToolArgumentsException("Cached candidate does not contain the same exact source");
            // Reuse only an immutable, run-owned receipt after checking the current
            // source/plan prerequisites. This does not supersede a later draft.
            return new ToolExecutionResult(output(existing, false), List.of());
        }
        if (!workspace.canValidateCandidate()) throw new InvalidToolArgumentsException("Candidate budget exhausted");
        workspace.requireNewCandidate(text.asText());
        workspace.recordDraft(text.asText());
        JsonNode receipt = runtime.validateCandidate(text.asText(), workspace.plan());
        if (java.util.Set.of("BUSY", "TIMEOUT", "WORKER_ERROR", "WORKER_EXIT", "RUNTIME_ERROR").contains(receipt.path("status").asText()))
            throw new AgentToolException(org.scse.cloudagent.domain.AgentErrorCode.INTERNAL_ERROR,
                    "Candidate checker did not return a completed execution receipt: " + receipt.path("status").asText());
        AgentWorkspace.Candidate candidate = workspace.recordCandidate(text.asText(), receipt);
        Map<String, Object> metadata = Map.of("candidateId", candidate.id(), "sourceHash", candidate.sourceHash(), "planHash", candidate.planHash(),
                "checksPassed", candidate.passed(), "validationScope", "DECLARED_VISIBLE_CHECKS", "contentBoundary", "UNTRUSTED");
        EvidenceDraft evidence = new EvidenceDraft("CANDIDATE_VALIDATION", candidate.id(), "Candidate compile and declared behavior checks",
                receipt.toString().substring(0, Math.min(6000, receipt.toString().length())), TrustLevel.TOOL_GENERATED, metadata);
        return new ToolExecutionResult(output(candidate, true), List.of(evidence));
    }
    private Map<String, Object> output(AgentWorkspace.Candidate candidate, boolean executionPerformed) {
        return Map.of("candidateId", candidate.id(), "sourceHash", candidate.sourceHash(), "planHash", candidate.planHash(),
                "sourceCode", candidate.sourceCode(), "checksPassed", candidate.passed(), "validation", candidate.receipt(),
                "executionPerformed", executionPerformed, "receiptReused", !executionPerformed);
    }
}
