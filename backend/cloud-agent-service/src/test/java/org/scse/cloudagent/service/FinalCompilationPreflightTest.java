package org.scse.cloudagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.*;
import org.scse.cloudagent.state.AgentStateMachine;
import org.scse.cloudagent.tool.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** No HTTP server, model calls, or Solidity runtime. Source/receipt boundary fixtures. */
class FinalCompilationPreflightTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String BASE = "contract Base {}";
    private static final String GOOD = "contract Good {}";
    private static final String BAD = "contract Broken { BROKEN }";

    private AgentProperties properties(int steps, int tools) {
        return new AgentProperties(true, "local-test", "test", new AgentProperties.Provider("http://invalid", "none", 1000, false),
                null, new AgentProperties.MiniLoop(true, steps, tools, 3),
                new AgentProperties.Dependencies("http://invalid", "http://invalid", 1000, 1000));
    }

    private final class Compiler extends CompileRuntimeClient {
        int calls;
        boolean unavailable;
        Compiler() { super(mapper, properties(6, 8)); }
        @Override public Map<String, Object> compile(String source, String filename, String version, long remaining) {
            calls++;
            if (unavailable) throw new AgentToolException(AgentErrorCode.TOOL_TIMEOUT, "fixture unavailable");
            boolean ok = !source.contains("BROKEN");
            return Map.of("success", ok, "selectedCompilerVersion", version, "errors",
                    ok ? List.of() : List.of(Map.of("severity", "error", "message", "Unexpected BROKEN token")), "warnings", List.of());
        }
    }

    private final class ReadTool implements AgentTool {
        @Override public ToolDefinition definition() {
            return new ToolDefinition("get_contract.v1", "fixture", "{\"type\":\"object\"}", SideEffect.READ, Duration.ofSeconds(1));
        }
        @Override public ToolExecutionResult execute(JsonNode args, ToolExecutionContext context) {
            var snapshot = context.workspace().observeSource(1, BASE, true);
            return new ToolExecutionResult(Map.of("id", 1, "sourceCode", BASE), List.of(new EvidenceDraft(
                    "CONTRACT_SOURCE", "contract:1", "Base", BASE, TrustLevel.TOOL_GENERATED,
                    Map.of("contractId", 1L, "sourceHash", snapshot.sourceHash()))));
        }
    }

    private final class CandidateTool implements AgentTool {
        @Override public ToolDefinition definition() {
            return new ToolDefinition("validate_candidate.v1", "fixture", "{\"type\":\"object\"}", SideEffect.EXECUTE_SANDBOXED, Duration.ofSeconds(1));
        }
        @Override public ToolExecutionResult execute(JsonNode args, ToolExecutionContext context) {
            var workspace = context.workspace();
            workspace.installPlan(workspace.requireReadSource(1), mapper.valueToTree(Map.of("scenarios", List.of())));
            JsonNode receipt = mapper.valueToTree(Map.of("sourceHash", SourceHashes.sha256(GOOD), "planHash", workspace.planHash(),
                    "compile", Map.of("success", true, "selectedCompilerVersion", "0.8.35", "errors", List.of()),
                    "scenarios", List.of(), "allChecksPassed", false));
            var candidate = workspace.recordCandidate(GOOD, receipt);
            return new ToolExecutionResult(Map.of("candidateId", candidate.id()), List.of(new EvidenceDraft(
                    "CANDIDATE_VALIDATION", candidate.id(), "Fixture checked source", "Behavior unresolved",
                    TrustLevel.TOOL_GENERATED, Map.of("candidateId", candidate.id(), "checksPassed", false))));
        }
    }

    private MiniAgentLoopEngine engine(Compiler compiler, StructuredActionProvider provider, int steps, int tools,
                                       boolean registered, boolean candidateTool) {
        List<AgentTool> registeredTools = new ArrayList<>(List.of(new ReadTool()));
        if (registered) registeredTools.add(new CompileSourceTool(compiler, mapper));
        if (candidateTool) registeredTools.add(new CandidateTool());
        return new MiniAgentLoopEngine(new AgentStateMachine(), new ToolRegistry(registeredTools), new ToolInvocationValidator(mapper),
                new MiniLoopToolPolicy(), provider, new UntrustedContentBoundary(), mapper, properties(steps, tools));
    }

    private AgentDecision deliver(AgentDecisionRequest request, String source) {
        return AgentDecision.finalAnswer("Selected source", request.evidence().stream().map(e -> e.id()).toList(), "fixture")
                .withProviderMetadata(Map.of("sourceCode", source));
    }

    private MiniLoopResult run(MiniAgentLoopEngine engine) {
        return engine.execute(UUID.randomUUID(), 1, "Modify the contract and return complete source", Map.of("selectedContractId", 1));
    }

    @Test void successfulFinalSourceNeedsOnlyOneModelCallAndOneCompilationTool() {
        Compiler compiler = new Compiler(); AtomicInteger decisions = new AtomicInteger();
        var result = run(engine(compiler, request -> { decisions.incrementAndGet(); return deliver(request, GOOD); }, 6, 8, true, false));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(decisions.get()).isOne(); assertThat(compiler.calls).isOne(); assertThat(result.toolCalls()).isEqualTo(2);
        assertThat(result.answer()).contains("Compilation succeeded", "behavior and task correctness remain unverified", GOOD);
        assertThat(result.evidence()).anyMatch(e -> e.sourceType().equals("SOLIDITY_SOURCE_COMPILE"));
        assertThat(result.evidence()).noneMatch(e -> e.sourceType().equals("CANDIDATE_VALIDATION"));
        assertThat(result.events()).anyMatch(e -> e.type().equals("tool.requested") && e.data().toString().contains("FINAL_SOURCE_PREFLIGHT"));
    }

    @Test void failedNewSourceReturnsFeedbackThenAgentMayRepair() {
        Compiler compiler = new Compiler(); AtomicInteger decisions = new AtomicInteger();
        var result = run(engine(compiler, request -> {
            if (decisions.incrementAndGet() == 1) return deliver(request, BAD);
            assertThat(request.observations().toString()).contains("Unexpected BROKEN token");
            return deliver(request, GOOD);
        }, 6, 8, true, false));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(decisions.get()).isEqualTo(2); assertThat(compiler.calls).isEqualTo(2);
        assertThat(result.toolCalls()).isEqualTo(3); assertThat(result.answer()).contains(GOOD).doesNotContain(BAD);
    }

    @Test void agentCanExplicitlyDeliverTheSameFailedSourceWithoutForcedRepairLoop() {
        Compiler compiler = new Compiler(); AtomicInteger decisions = new AtomicInteger();
        var result = run(engine(compiler, request -> { decisions.incrementAndGet(); return deliver(request, BAD); }, 6, 8, true, false));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(decisions.get()).isEqualTo(2); assertThat(compiler.calls).isOne();
        assertThat(result.answer()).contains("COMPILATION FAILED", BAD); assertThat(result.toolCalls()).isEqualTo(2);
    }

    @Test void exhaustedModelBudgetRetainsFailedNewDraftWithoutAdditionalDecision() {
        Compiler compiler = new Compiler(); AtomicInteger decisions = new AtomicInteger();
        var result = run(engine(compiler, request -> { decisions.incrementAndGet(); return deliver(request, BAD); }, 1, 8, true, false));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(decisions.get()).isOne(); assertThat(result.answer()).contains("COMPILATION FAILED", BAD);
    }

    @Test void exhaustedToolBudgetSkipsCompilationWithoutPretendingToPass() {
        Compiler compiler = new Compiler();
        var result = run(engine(compiler, request -> deliver(request, GOOD), 6, 1, true, false));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(compiler.calls).isZero(); assertThat(result.toolCalls()).isOne();
        assertThat(result.answer()).contains("NOT_CHECKED_BUDGET", GOOD).doesNotContain("Compilation succeeded");
    }

    @Test void absentOrUnavailableCompilerDoesNotDestroyTheFinalDraft() {
        for (boolean registered : List.of(false, true)) {
            Compiler compiler = new Compiler(); compiler.unavailable = true;
            var result = run(engine(compiler, request -> deliver(request, GOOD), 6, 8, registered, false));
            assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
            assertThat(result.answer()).contains("UNAVAILABLE", GOOD).doesNotContain("COMPILATION FAILED");
        }
    }

    @Test void knownCandidateCompileReceiptIsReusedWithoutBehaviorPromotion() {
        Compiler compiler = new Compiler();
        var result = run(engine(compiler, request -> request.step() == 1
                ? AgentDecision.toolCall("validate_candidate.v1", "{}", "fixture") : deliver(request, GOOD), 6, 8, true, true));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(compiler.calls).isZero(); assertThat(result.toolCalls()).isEqualTo(2);
        assertThat(result.answer()).contains("did not pass every declared check", GOOD);
    }

    @Test void failedNewDraftListsOlderCompilableCandidateWithoutSubstitutionOrSecondCodeBlock() {
        Compiler compiler = new Compiler();
        var result = run(engine(compiler, request -> request.step() == 1
                ? AgentDecision.toolCall("validate_candidate.v1", "{}", "fixture") : deliver(request, BAD), 2, 8, true, true));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.answer()).contains(BAD, "candidate:" + SourceHashes.sha256(GOOD), "no alternative was substituted").doesNotContain(GOOD);
        assertThat(result.answer().split("```solidity", -1)).hasSize(2);
        assertThat(result.events()).anyMatch(e -> e.type().equals("candidate.compilable_alternatives"));
    }

    @Test void sharedToolCacheUsesExactBytesActualVersionAndDoesNotClobberNewerDraft() {
        Compiler compiler = new Compiler(); var tool = new CompileSourceTool(compiler, mapper); var workspace = new AgentWorkspace();
        var context = new ToolExecutionContext(1, UUID.randomUUID(), workspace);
        JsonNode args = mapper.valueToTree(Map.of("sourceCode", GOOD));
        var first = tool.execute(args, context); workspace.recordDraft(BAD);
        var cached = tool.execute(args, context);
        assertThat(compiler.calls).isOne(); assertThat(cached.output().get("receiptReused")).isEqualTo(true);
        assertThat(cached.evidence()).isEmpty(); assertThat(workspace.latestDraft().sourceCode()).isEqualTo(BAD);
        tool.execute(mapper.valueToTree(Map.of("sourceCode", GOOD + "\n")), context);
        tool.execute(mapper.valueToTree(Map.of("sourceCode", GOOD, "compilerVersion", "0.8.20")), context);
        assertThat(compiler.calls).isEqualTo(3);
        assertThat(first.output().get("validationScope")).isEqualTo("COMPILATION_ONLY");
        assertThat(workspace.hasPlan()).isFalse(); assertThat(workspace.latestCandidate()).isNull();
    }

    @Test void failedCompilationCacheRemainsFailedAndSourceIsRetained() {
        Compiler compiler = new Compiler(); var tool = new CompileSourceTool(compiler, mapper); var context = new ToolExecutionContext(1, UUID.randomUUID());
        var args = mapper.valueToTree(Map.of("sourceCode", BAD));
        tool.execute(args, context); var repeated = tool.execute(args, context);
        assertThat(compiler.calls).isOne(); assertThat(repeated.output().get("status")).isEqualTo("COMPILATION_FAILED");
        assertThat(context.workspace().latestDraft().sourceCode()).isEqualTo(BAD);
    }
}
