package org.scse.cloudagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.state.AgentStateMachine;
import org.scse.cloudagent.tool.*;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real HTTP + compiler + isolated EVM, scripted model, in-memory catalog. No model API. */
@EnabledIfEnvironmentVariable(named = "AGENT_LOCAL_RUNTIME_TEST", matches = "true")
class CandidateReuseIntegrationTest {
    private static Process runtime;
    private static String runtimeUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll static void startRuntime() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        runtimeUrl = "http://127.0.0.1:" + port;
        var log = Files.createTempFile("candidate-runtime-test-", ".log");
        var builder = new ProcessBuilder(System.getenv().getOrDefault("AGENT_TEST_NODE", "node"), "src/index.js")
                .directory(CandidateFixture.repository().resolve("contract-runtime-service").toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("RUNTIME_PORT", String.valueOf(port));
        builder.environment().put("RUNTIME_HOST", "127.0.0.1");
        runtime = builder.start();
        var client = HttpClient.newHttpClient();
        for (int i = 0; i < 100; i++) {
            try {
                if (client.send(HttpRequest.newBuilder(URI.create(runtimeUrl + "/health")).timeout(Duration.ofSeconds(1)).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode() == 200) return;
            } catch (Exception ignored) { }
            if (!runtime.isAlive()) break;
            Thread.sleep(100);
        }
        stopRuntime();
        throw new IllegalStateException("Local runtime did not start; log: " + log);
    }

    @AfterAll static void stopRuntime() throws Exception {
        if (runtime != null) {
            runtime.destroy();
            if (!runtime.waitFor(3, TimeUnit.SECONDS)) { runtime.destroyForcibly(); runtime.waitFor(3, TimeUnit.SECONDS); }
        }
    }

    @Test void failedBehaviorCanBeRepairedAndFinalSourceEqualsTestedBytes() throws Exception {
        List<AgentDecisionRequest> requests = new ArrayList<>();
        var plan = CandidateFixture.plan();
        String bad = CandidateFixture.SOURCE.replace("count += 1", "count = 1");
        StructuredActionProvider provider = request -> {
            requests.add(request);
            if (request.step() == 1) return planDecision(plan);
            if (request.step() == 2) return candidateDecision(bad, request);
            if (request.step() == 3) {
                var first = mapper.valueToTree(request.verificationState()).at("/candidates/0");
                assertThat(first.path("checksPassed").asBoolean()).isFalse();
                assertThat(first.path("counterexamples").size()).isEqualTo(1);
                assertThat(request.verificationState().get("phase")).isEqualTo("CHECK_DISCREPANCY");
                assertThat(request.tools()).extracting(ToolDefinition::name).contains("validate_candidate.v1", "get_contract.v1").doesNotContain("plan_reuse.v1");
                return candidateDecision(CandidateFixture.SOURCE, request);
            }
            return finalDecision(request, CandidateFixture.SOURCE);
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Repair counter and preserve permissions", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.toolCalls()).isEqualTo(4); // hydrate, plan, two candidate validations
        assertThat(result.answer()).contains("```solidity\n" + CandidateFixture.SOURCE + "\n```").doesNotContain("UNTESTED REWRITE");
        assertThat(result.events()).anyMatch(e -> e.type().equals("candidate.selected") && e.data().toString().contains(SourceHashes.sha256(CandidateFixture.SOURCE)));
        assertThat(requests.get(3).tools()).extracting(ToolDefinition::name).contains("validate_candidate.v1").doesNotContain("plan_reuse.v1");
        Files.writeString(java.nio.file.Path.of("target/candidate-reuse-local-trace.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "scope", "SCRIPTED_DECISIONS_REAL_COMPILER_AND_EVM_NOT_A_MODEL_BENCHMARK",
                        "modelApiCalls", 0, "toolCalls", result.toolCalls(), "result", result.state().name(),
                        "selectedSourceHash", SourceHashes.sha256(CandidateFixture.SOURCE),
                        "verificationState", requests.get(3).verificationState())));
    }

    @Test void failedCheckCanBeReportedWithoutForcingAnotherModelRound() throws Exception {
        var plan = CandidateFixture.plan(); String bad = CandidateFixture.SOURCE.replace("count += 1", "count = 1");
        StructuredActionProvider provider = request -> switch (request.step()) {
            case 1 -> planDecision(plan);
            case 2 -> candidateDecision(bad, request);
            case 3 -> finalDecision(request, bad);
            default -> {
                assertThat(request.observations()).anyMatch(s -> s.contains("VALIDATION_GAP"));
                yield AgentDecision.abstain("Repeated increment is unresolved", List.of(), "failure reported", "increment requirement", "");
            }
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Fix counter", Map.of("selectedContractId", 1));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(3);
        assertThat(result.answer()).contains("UNRESOLVED CANDIDATE", bad);
        assertThat(result.answer()).doesNotContain("all declared visible checks passed");
        assertThat(result.events()).anyMatch(e -> e.type().equals("candidate.selected"));
        assertThat(result.events()).noneMatch(e -> e.type().equals("answer.validation_gap"));
        assertThat(result.evidence()).anyMatch(e -> e.sourceType().equals("CANDIDATE_VALIDATION")
                && Boolean.FALSE.equals(e.metadata().get("checksPassed")));
    }

    @Test void invalidPlanArgumentsBecomeFeedbackAndCanBeCorrectedWithinBudget() throws Exception {
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> switch (request.step()) {
            case 1 -> AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1, "baseSourceHash", "wrong", "plan", plan)), "wrong hash");
            case 2 -> {
                assertThat(request.observations()).anyMatch(s -> s.contains("INVALID_ARGUMENTS"));
                assertThat(mapper.valueToTree(request.verificationState()).at("/lastRejectedAction/arguments/baseSourceHash").asText()).isEqualTo("wrong");
                yield planDecision(plan);
            }
            case 3 -> candidateDecision(CandidateFixture.SOURCE, request);
            default -> finalDecision(request, CandidateFixture.SOURCE);
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Fix counter", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.toolCalls()).isEqualTo(4);
        assertThat(result.events()).anyMatch(e -> e.type().equals("tool.rejected"));
    }

    private AgentDecision planDecision(com.fasterxml.jackson.databind.JsonNode plan) {
        return AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1,
                "baseSourceHash", SourceHashes.sha256(CandidateFixture.SOURCE), "plan", plan)), "declare behaviors");
    }

    @Test void uniqueCitedPassingReceiptSelectsTestedSourceWithoutRedundantCandidateField() throws Exception {
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> switch (request.step()) {
            case 1 -> planDecision(plan);
            case 2 -> candidateDecision(CandidateFixture.SOURCE, request);
            default -> finalDecision(request, CandidateFixture.SOURCE).withProviderMetadata(Map.of());
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Fix counter", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(3);
        assertThat(result.answer()).contains(CandidateFixture.SOURCE).doesNotContain("UNTESTED REWRITE");
        assertThat(result.events()).anyMatch(e -> e.type().equals("candidate.selected")
                && e.data().toString().contains("UNAMBIGUOUS_VALIDATION_CITATION"));
    }

    @Test void twoCitedPassingCandidatesStillRequireExplicitSelection() throws Exception {
        var plan = CandidateFixture.plan(); String second = CandidateFixture.SOURCE + "\n// second candidate\n";
        StructuredActionProvider provider = request -> switch (request.step()) {
            case 1 -> planDecision(plan);
            case 2 -> candidateDecision(CandidateFixture.SOURCE, request);
            case 3 -> candidateDecision(second, request);
            case 4 -> AgentDecision.finalAnswer("Choose a candidate", request.evidence().stream()
                    .filter(e -> e.sourceType().equals("CANDIDATE_VALIDATION")).map(e -> e.id()).toList(), "ambiguous");
            default -> {
                assertThat(request.observations()).anyMatch(s -> s.contains("Missing candidateId"));
                yield AgentDecision.abstain("Selection unresolved", List.of(), "stop", "ambiguous selection", "");
            }
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Fix counter", Map.of("selectedContractId", 1));
        assertThat(result.answer()).contains("Selection unresolved", second);
        assertThat(result.events()).anyMatch(e -> e.type().equals("candidate.selected"));
    }

    @Test void simpleModifiedSourceCanFinishOnFirstDecisionWithoutForcedPlan() {
        StructuredActionProvider provider = request -> {
            assertThat(request.step()).isOne();
            assertThat(request.verificationState()).containsEntry("modifiedSourceRequested", true);
            assertThat(request.tools()).extracting(ToolDefinition::name).contains("plan_reuse.v1");
            return AgentDecision.finalAnswer("A source-level draft, not executed.\n```solidity\n" + CandidateFixture.SOURCE + "\n```",
                    request.evidence().stream().map(e -> e.id()).toList(), "No additional execution needed for the requested draft");
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "修复计数器，交付完整单文件源码", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isOne();
        assertThat(result.toolCalls()).isOne(); // source hydration only
        assertThat(result.answer()).contains(CandidateFixture.SOURCE, "UNEXECUTED DRAFT");
        assertThat(result.events()).noneMatch(e -> e.type().equals("answer.validation_gap"));
        assertThat(result.events()).anyMatch(e -> e.type().equals("candidate.draft_preserved"));
    }

    @Test void agentCanChooseBundledExecutionAndStopAfterOneCheck() throws Exception {
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> {
            if (request.step() == 1) return AgentDecision.toolCall("plan_reuse.v1", json(Map.of(
                    "baseContractId", 1, "plan", plan, "candidateSource", CandidateFixture.SOURCE)), "Test meaningful behavior");
            assertThat(request.step()).isEqualTo(2);
            assertThat(request.verificationState()).containsEntry("phase", "CHECKED_CANDIDATE_AVAILABLE");
            assertThat(request.tools()).extracting(ToolDefinition::name).contains("validate_candidate.v1", "get_contract.v1");
            return finalDecision(request, CandidateFixture.SOURCE);
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "修复计数器，交付完整源码并运行测试", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(2);
        assertThat(result.toolCalls()).isEqualTo(2); // hydrate + bundled plan/candidate
        assertThat(result.answer()).contains("all declared visible checks passed", CandidateFixture.SOURCE);
    }

    @Test void abiMismatchIsRejectedBeforeFreezeSoAgentCanCorrectTheCheck() throws Exception {
        var plan = CandidateFixture.plan(); var invalid = plan.deepCopy();
        var step = (com.fasterxml.jackson.databind.node.ObjectNode) invalid.at("/scenarios/0/steps/0");
        step.put("kind", "write").put("expect", "success"); step.remove("expected");
        StructuredActionProvider provider = request -> switch (request.step()) {
            case 1 -> planDecision(invalid);
            case 2 -> {
                assertThat(request.observations()).anyMatch(s -> s.contains("ABI_PREFLIGHT_REJECTED"));
                assertThat(request.verificationState().get("reusePlanDeclared")).isEqualTo(false);
                yield AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1,
                        "baseSourceHash", SourceHashes.sha256(CandidateFixture.SOURCE), "plan", plan,
                        "candidateSource", CandidateFixture.SOURCE)), "Correct the test operation kind");
            }
            default -> finalDecision(request, CandidateFixture.SOURCE);
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Fix counter", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(3);
        assertThat(result.answer()).contains("all declared visible checks passed");
    }

    @Test void impossibleSetupIsRejectedBeforeFreezeAndCanBeCorrected() throws Exception {
        var plan = CandidateFixture.plan();
        var invalid = plan.deepCopy();
        var scenario = (com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("scenarios").get(1);
        scenario.put("setupSteps", 1);
        var step = (com.fasterxml.jackson.databind.node.ObjectNode) scenario.path("steps").get(0);
        step.put("from", 1); // non-owner increment is an impossible successful fixture
        StructuredActionProvider provider = request -> switch (request.step()) {
            case 1 -> planDecision(invalid);
            case 2 -> {
                assertThat(request.observations()).anyMatch(x -> x.contains("FIXTURE_REJECTED"));
                assertThat(request.verificationState().get("reusePlanDeclared")).isEqualTo(false);
                yield planDecision(plan);
            }
            case 3 -> candidateDecision(CandidateFixture.SOURCE, request);
            default -> finalDecision(request, CandidateFixture.SOURCE);
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Fix counter", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.answer()).contains(CandidateFixture.SOURCE);
        assertThat(result.events()).anyMatch(e -> e.type().equals("tool.rejected"));
    }


    @Test void compactPlanDerivesGroupsWithoutDuplicatingRequirementIds() throws Exception {
        var plan = (com.fasterxml.jackson.databind.node.ObjectNode) CandidateFixture.plan();
        plan.remove("requirements");
        for (var row : plan.path("scenarios")) ((com.fasterxml.jackson.databind.node.ObjectNode) row).remove("requirementId");
        StructuredActionProvider provider = request -> switch (request.step()) {
            case 1 -> planDecision(plan);
            case 2 -> candidateDecision(CandidateFixture.SOURCE, request);
            default -> finalDecision(request, CandidateFixture.SOURCE);
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Fix counter", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.answer()).contains(CandidateFixture.SOURCE);
    }
    @Test void failedCheckedArtifactIsDeliveredHonestlyWithoutUsingUpBudget() throws Exception {
        var plan = CandidateFixture.plan(); String bad = CandidateFixture.SOURCE.replace("count += 1", "count = 1");
        StructuredActionProvider provider = request -> switch (request.step()) {
            case 1 -> planDecision(plan);
            case 2 -> candidateDecision(bad, request);
            default -> finalDecision(request, bad);
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Fix counter", Map.of("selectedContractId", 1));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(3);
        assertThat(result.answer()).contains("UNRESOLVED CANDIDATE", bad).doesNotContain("all declared visible checks passed");
        assertThat(result.events()).noneMatch(e -> e.type().equals("candidate.delivery_fallback"));
    }

    @Test void runtimeFailureBeforeAReceiptPreservesOnlyAnUnexecutedDraft() throws Exception {
        var client = mock(CandidateRuntimeClient.class);
        when(client.validatePlan(any(), anyString(), eq(true))).thenThrow(new AgentToolException(
                org.scse.cloudagent.domain.AgentErrorCode.INTERNAL_ERROR, "Simulated dropped response"));
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> AgentDecision.toolCall("plan_reuse.v1", json(Map.of(
                "baseContractId", 1, "baseSourceHash", SourceHashes.sha256(CandidateFixture.SOURCE),
                "plan", plan, "candidateSource", CandidateFixture.SOURCE)), "Check draft");
        var result = engine(provider, client).execute(UUID.randomUUID(), 7, "Repair counter", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isOne();
        assertThat(result.answer()).contains("UNEXECUTED DRAFT", CandidateFixture.SOURCE).doesNotContain("all declared visible checks passed");
        assertThat(result.evidence()).noneMatch(e -> e.sourceType().equals("CANDIDATE_VALIDATION"));
        assertThat(result.events()).anyMatch(e -> e.type().equals("candidate.draft_preserved"));
    }

    @Test void boundedLoopStopStillPreservesAnAlreadyGeneratedDraft() throws Exception {
        var client = mock(CandidateRuntimeClient.class);
        when(client.validatePlan(any(), anyString(), eq(true))).thenThrow(new InvalidToolArgumentsException("bad test shape"));
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> request.step() == 1
                ? AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1, "plan", plan,
                    "candidateSource", CandidateFixture.SOURCE)), "Propose source")
                : AgentDecision.toolCall("get_contract.v1", "{\"contractId\":1}", "repeat read");
        var result = engine(provider, client).execute(UUID.randomUUID(), 7, "Repair counter", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(3);
        assertThat(result.answer()).contains("LOOP_DETECTED", "UNEXECUTED DRAFT", CandidateFixture.SOURCE);
        assertThat(result.evidence()).noneMatch(e -> e.sourceType().equals("CANDIDATE_VALIDATION"));
    }

    @Test void malformedToolJsonBecomesFeedbackWithoutAbortingTheRemainingBudget() throws Exception {
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> {
            if (request.step() == 1) return AgentDecision.toolCall("plan_reuse.v1", "{\"plan\": [}", "malformed syntax");
            if (request.step() == 2) {
                assertThat(request.remainingModelCalls()).isEqualTo(5);
                assertThat(request.observations()).anyMatch(s -> s.contains("ARGUMENT_JSON_REJECTED"));
                assertThat(request.verificationState()).containsKey("lastRejectedAction");
                return AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1,
                        "plan", plan, "candidateSource", CandidateFixture.SOURCE)), "Correct syntax and check");
            }
            return finalDecision(request, CandidateFixture.SOURCE);
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Fix counter", Map.of("selectedContractId", 1));
        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(3);
        assertThat(result.toolCalls()).isEqualTo(2); // malformed proposal was never executed
        assertThat(result.answer()).contains("all declared visible checks passed");
    }

    @Test void malformedModelActionChargesActualCallsAndCanRecover() {
        StructuredActionProvider provider = request -> {
            if (request.step() == 1) throw new RecoverableDecisionException("bad JSON after local repair", 2, "broken", null);
            assertThat(request.remainingModelCalls()).isEqualTo(4);
            assertThat(request.observations()).anyMatch(s -> s.contains("INVALID_MODEL_ACTION"));
            return AgentDecision.finalAnswer("A source-only draft", request.evidence().stream().map(e -> e.id()).toList(), "stop")
                    .withProviderMetadata(Map.of("sourceCode", CandidateFixture.SOURCE));
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Deliver counter source", Map.of("selectedContractId", 1));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(2);
        assertThat(result.answer()).contains("UNEXECUTED DRAFT", CandidateFixture.SOURCE);
    }

    @Test void repeatedMalformedActionsCannotResetTheModelBudget() {
        List<Integer> remaining = new ArrayList<>();
        StructuredActionProvider provider = request -> {
            remaining.add(request.remainingModelCalls());
            throw new RecoverableDecisionException("bad JSON", 2, "broken", null);
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Deliver counter source", Map.of("selectedContractId", 1));
        assertThat(result.state()).isEqualTo(AgentRunState.FAILED);
        assertThat(remaining).containsExactly(6, 4, 2);
    }

    @Test void explicitFinalRevisionCanStopWithoutInheritingAnEarlierReceipt() throws Exception {
        String revised = CandidateFixture.SOURCE + "\n// final source-only revision\n";
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> {
            if (request.step() == 1) return AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1,
                    "plan", plan, "candidateSource", CandidateFixture.SOURCE)), "Check initial candidate");
            return AgentDecision.finalAnswer("Final revision, not executed", request.evidence().stream().map(e -> e.id()).toList(), "deliver revision")
                    .withProviderMetadata(Map.of("sourceCode", revised));
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Deliver counter source", Map.of("selectedContractId", 1));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(2);
        assertThat(result.answer()).contains("UNEXECUTED DRAFT", revised).doesNotContain("all declared visible checks passed");
        assertThat(result.events()).noneMatch(e -> e.type().equals("candidate.selected"));
    }

    @Test void explicitSourceMatchingAnActualReceiptKeepsItsRealStatus() throws Exception {
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> {
            if (request.step() == 1) return AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1,
                    "plan", plan, "candidateSource", CandidateFixture.SOURCE)), "Check candidate");
            return AgentDecision.finalAnswer("Deliver these exact bytes", request.evidence().stream().map(e -> e.id()).toList(), "deliver")
                    .withProviderMetadata(Map.of("sourceCode", CandidateFixture.SOURCE));
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "Deliver counter source", Map.of("selectedContractId", 1));
        assertThat(result.answer()).contains("all declared visible checks passed").doesNotContain("UNEXECUTED DRAFT");
    }

    @Test void legacyFinalRevisionCannotBeReplacedByTheOldCitedReceipt() throws Exception {
        String bad = CandidateFixture.SOURCE.replace("count += 1", "count = 1");
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> {
            if (request.step() == 1) return AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1,
                    "plan", plan, "candidateSource", bad)), "Check initial candidate");
            String answer = "Revised source, not executed.\n" + "\u0060\u0060\u0060solidity\n" + CandidateFixture.SOURCE + "\u0060\u0060\u0060";
            return AgentDecision.finalAnswer(answer, request.evidence().stream().map(e -> e.id()).toList(), "deliver revision");
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "修复计数器，交付源码", Map.of("selectedContractId", 1));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(2);
        assertThat(result.answer()).contains("UNEXECUTED DRAFT", CandidateFixture.SOURCE).doesNotContain(bad, "all declared visible checks passed");
        assertThat(result.events()).anyMatch(e -> e.type().equals("candidate.legacy_source_received"));
        assertThat(result.events()).noneMatch(e -> e.type().equals("candidate.selected"));
    }

    @Test void exactLegacySourceCanBindAnActualReceipt() throws Exception {
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> {
            if (request.step() == 1) return AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1,
                    "plan", plan, "candidateSource", CandidateFixture.SOURCE)), "Check candidate");
            return AgentDecision.finalAnswer("\u0060\u0060\u0060solidity\n" + CandidateFixture.SOURCE + "\u0060\u0060\u0060",
                    request.evidence().stream().map(e -> e.id()).toList(), "deliver exact source");
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "修复计数器，交付源码", Map.of("selectedContractId", 1));
        assertThat(result.answer()).contains("all declared visible checks passed").doesNotContain("UNEXECUTED DRAFT");
    }

    @Test void ambiguousLegacySourceGetsFeedbackInsteadOfSilentCandidateSubstitution() throws Exception {
        var plan = CandidateFixture.plan();
        StructuredActionProvider provider = request -> {
            if (request.step() == 1) return AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1,
                    "plan", plan, "candidateSource", CandidateFixture.SOURCE)), "Check candidate");
            if (request.step() == 2) return AgentDecision.finalAnswer(
                    "\u0060\u0060\u0060solidity\ncontract A {}\n\u0060\u0060\u0060\n\u0060\u0060\u0060solidity\ncontract B {}\n\u0060\u0060\u0060",
                    request.evidence().stream().map(e -> e.id()).toList(), "ambiguous");
            assertThat(request.observations()).anyMatch(s -> s.contains("Ambiguous source delivery"));
            return finalDecision(request, CandidateFixture.SOURCE);
        };
        var result = engine(provider).execute(UUID.randomUUID(), 7, "修复计数器，交付源码", Map.of("selectedContractId", 1));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(3);
        assertThat(result.answer()).contains(CandidateFixture.SOURCE);
    }

    private AgentDecision candidateDecision(String source, AgentDecisionRequest request) {
        return AgentDecision.toolCall("validate_candidate.v1", json(Map.of("sourceCode", source)), "check candidate");
    }
    private AgentDecision finalDecision(AgentDecisionRequest request, String source) {
        String id = "candidate:" + SourceHashes.sha256(source);
        return AgentDecision.finalAnswer("Checked candidate.\n```solidity\nUNTESTED REWRITE\n```",
                request.evidence().stream().filter(e -> id.equals(e.sourceRef())).map(e -> e.id()).toList(), "final")
                .withProviderMetadata(Map.of("candidateId", id));
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException(e); }
    }
    private MiniAgentLoopEngine engine(StructuredActionProvider provider) { return engine(provider, null); }
    private MiniAgentLoopEngine engine(StructuredActionProvider provider, CandidateRuntimeClient override) {
        var catalog = mock(ContractCatalogClient.class);
        when(catalog.getContract(1, 7)).thenReturn(Map.of("contractName", "Counter", "sourceCode", CandidateFixture.SOURCE));
        var properties = new AgentProperties(true, "local-test-token", "scripted-model",
                new AgentProperties.Provider("http://provider.invalid", "unused", 2000, false), null,
                new AgentProperties.MiniLoop(true, 6, 5, 2), new AgentProperties.Dependencies(null, runtimeUrl, 2000, 30000));
        var client = override == null ? new CandidateRuntimeClient(mapper, properties) : override;
        var registry = new ToolRegistry(List.of(new GetContractTool(catalog), new PlanReuseTool(client), new ValidateCandidateTool(client)));
        return new MiniAgentLoopEngine(new AgentStateMachine(), registry, new ToolInvocationValidator(mapper),
                new MiniLoopToolPolicy(), provider, new UntrustedContentBoundary(), mapper, properties);
    }
}
