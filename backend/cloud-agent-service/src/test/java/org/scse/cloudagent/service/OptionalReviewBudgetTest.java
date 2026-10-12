package org.scse.cloudagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.state.AgentStateMachine;
import org.scse.cloudagent.tool.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Engine-level accounting only: all catalog, execution and reviewer I/O is mocked. */
class OptionalReviewBudgetTest {
    private static final String TASK = "Repair Counter and deliver its complete Solidity source";
    private static final String REVIEW = "review_candidate.v1";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractCatalogClient catalog = mock(ContractCatalogClient.class);
    private final CandidateRuntimeClient runtime = mock(CandidateRuntimeClient.class);
    private final GlmStructuredActionClient reviewer = mock(GlmStructuredActionClient.class);

    @Test void optionalReviewConsumesOneOfTheSameSixModelCallsAndPreservesFinalCapacity() throws Exception {
        List<Integer> remaining = new ArrayList<>();
        String second = CandidateFixture.SOURCE + "\n// second candidate\n";
        String third = CandidateFixture.SOURCE + "\n// third candidate\n";
        when(reviewer.complete(anyString(), anyString(), anyList())).thenAnswer(invocation -> {
            JsonNode input = mapper.readTree(invocation.getArgument(1, String.class));
            assertThat(input.path("originalTask").asText()).isEqualTo(TASK);
            assertThat(input.path("candidateSource").asText()).isEqualTo(CandidateFixture.SOURCE);
            assertThat(input.path("authoredPlan").path("scenarios").size()).isPositive();
            assertThat(invocation.getArgument(2, List.class)).isEmpty();
            return reviewResponse();
        });
        StructuredActionProvider provider = request -> {
            remaining.add(request.remainingModelCalls());
            return switch (request.step()) {
                case 1 -> {
                    assertThat(request.tools()).extracting(ToolDefinition::name).doesNotContain(REVIEW);
                    yield bundledCheck();
                }
                case 2 -> {
                    assertThat(request.tools()).extracting(ToolDefinition::name).contains(REVIEW);
                    yield AgentDecision.toolCall(REVIEW, "{}", "Check the interpretation before revising");
                }
                case 3 -> {
                    assertThat(request.remainingModelCalls()).isEqualTo(3);
                    assertThat(request.observations()).anyMatch(text -> text.contains("ADVISORY_REVIEW_ONLY"));
                    assertThat(request.evidence()).hasSize(2); // review creates no execution evidence
                    yield candidateCheck(second);
                }
                case 4 -> {
                    assertThat(request.tools()).extracting(ToolDefinition::name).doesNotContain(REVIEW);
                    yield candidateCheck(third);
                }
                case 5 -> {
                    assertThat(request.tools()).isEmpty();
                    yield finalSource(request, third);
                }
                default -> throw new AssertionError("Review must not create a seventh model-call allowance");
            };
        };

        MiniLoopResult result = execute(provider);

        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(remaining).containsExactly(6, 5, 3, 2, 1);
        assertThat(result.steps()).isEqualTo(5);
        assertThat(completedModelCalls(result)).isEqualTo(6);
        assertThat(result.answer()).contains(third);
        assertThat(result.evidence()).hasSize(4);
        verify(reviewer, times(1)).complete(anyString(), anyString(), eq(List.of()));
        verify(runtime, times(3)).validateCandidate(anyString(), any(JsonNode.class));
    }

    @Test void failedReviewIsChargedOnceWithoutRetryOrLosingTheCheckedSource() throws Exception {
        List<Integer> remaining = new ArrayList<>();
        when(reviewer.complete(anyString(), anyString(), anyList())).thenThrow(new IOException("mock connection failure"));
        StructuredActionProvider provider = request -> {
            remaining.add(request.remainingModelCalls());
            if (request.step() == 1) return bundledCheck();
            if (request.step() == 2) return AgentDecision.toolCall(REVIEW, "{}", "Inspect an uncertain expectation");
            assertThat(request.step()).isEqualTo(3);
            assertThat(request.remainingModelCalls()).isEqualTo(3);
            assertThat(request.observations()).anyMatch(text -> text.contains("ADVISORY_UNAVAILABLE"));
            assertThat(request.evidence()).hasSize(2);
            return finalSource(request, CandidateFixture.SOURCE);
        };

        MiniLoopResult result = execute(provider);

        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.answer()).contains(CandidateFixture.SOURCE);
        assertThat(completedModelCalls(result)).isEqualTo(4);
        assertThat(remaining).containsExactly(6, 5, 3);
        assertThat(result.steps()).isEqualTo(3);
        assertThat(result.evidence()).hasSize(2);
        assertThat(result.events()).noneMatch(event -> event.type().equals("candidate.delivery_fallback"));
        verify(reviewer, times(1)).complete(anyString(), anyString(), eq(List.of()));
        verify(runtime, times(1)).validateCandidate(anyString(), any(JsonNode.class));
    }

    @Test void reviewIsUnavailableWhenActualModelCallBudgetCannotReserveItsCallAndFinalDelivery() throws Exception {
        List<Integer> remaining = new ArrayList<>();
        StructuredActionProvider provider = request -> {
            remaining.add(request.remainingModelCalls());
            return switch (request.step()) {
                case 1 -> bundledCheck();
                case 2 -> throw new RecoverableDecisionException("malformed response and failed format repair", 2, "invalid", null);
                case 3 -> {
                    assertThat(request.tools()).extracting(ToolDefinition::name).contains(REVIEW);
                    throw new RecoverableDecisionException("malformed response", 1, "invalid", null);
                }
                case 4 -> {
                    assertThat(request.remainingModelCalls()).isEqualTo(2);
                    assertThat(request.tools()).extracting(ToolDefinition::name).doesNotContain(REVIEW);
                    yield finalSource(request, CandidateFixture.SOURCE);
                }
                default -> throw new AssertionError("Unexpected additional model decision");
            };
        };

        MiniLoopResult result = execute(provider);

        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(remaining).containsExactly(6, 5, 3, 2);
        assertThat(completedModelCalls(result)).isEqualTo(5);
        assertThat(result.answer()).contains(CandidateFixture.SOURCE);
        verifyNoInteractions(reviewer);
    }

    @Test void simpleSourceDeliveryFinishesOnTheFirstDecisionWithoutReviewOrExecution() throws Exception {
        StructuredActionProvider provider = request -> {
            assertThat(request.step()).isOne();
            assertThat(request.remainingModelCalls()).isEqualTo(6);
            assertThat(request.tools()).extracting(ToolDefinition::name).doesNotContain(REVIEW);
            return finalSource(request, CandidateFixture.SOURCE);
        };

        MiniLoopResult result = execute(provider);

        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isOne();
        assertThat(result.toolCalls()).isOne(); // selected-source hydration only
        assertThat(completedModelCalls(result)).isOne();
        assertThat(result.answer()).contains("UNEXECUTED DRAFT", CandidateFixture.SOURCE);
        verifyNoInteractions(reviewer, runtime);
    }

    @Test void abstentionKeepsAnExplicitEarlierCandidateInsteadOfTheLatestCandidateOrDraft() throws Exception {
        String first = CandidateFixture.SOURCE;
        String second = first + "\n// later candidate must not replace explicit selection\n";
        String firstId = "candidate:" + SourceHashes.sha256(first);
        for (boolean includeSourceBlock : List.of(false, true)) {
            StructuredActionProvider provider = request -> switch (request.step()) {
                case 1 -> bundledCheck();
                case 2 -> candidateCheck(second);
                case 3 -> AgentDecision.abstain(
                        "Retain the first candidate; remaining requirements are unresolved."
                                + (includeSourceBlock ? "\n```solidity\n" + first + "```" : ""),
                        request.evidence().stream().filter(item -> firstId.equals(item.sourceRef()))
                                .map(item -> item.id()).toList(),
                        "Select the earlier exact source", "Unverified requirements remain", "")
                        .withProviderMetadata(Map.of("candidateId", firstId));
                default -> throw new AssertionError("Explicit artifact selection must not need another decision");
            };
            MiniLoopResult result = execute(provider);
            assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
            assertThat(result.steps()).isEqualTo(3);
            assertThat(result.answer()).contains("```solidity\n" + first + "\n```")
                    .doesNotContain("later candidate must not replace explicit selection", "UNEXECUTED DRAFT");
            assertThat(result.events()).anyMatch(event -> event.type().equals("candidate.selected")
                    && firstId.equals(mapper.valueToTree(event.data()).at("/payload/candidateId").asText()));
            assertThat(result.events()).noneMatch(event -> event.type().equals("candidate.draft_preserved"));
        }
        verifyNoInteractions(reviewer);
    }

    private MiniLoopResult execute(StructuredActionProvider provider) {
        when(catalog.getContract(1, 7)).thenReturn(Map.of("contractName", "Counter", "sourceCode", CandidateFixture.SOURCE));
        when(runtime.validatePlan(any(JsonNode.class), anyString(), anyBoolean())).thenAnswer(invocation ->
                mapper.createObjectNode().put("planHash", SourceHashes.planHash(invocation.getArgument(0))));
        when(runtime.validateCandidate(anyString(), any(JsonNode.class))).thenAnswer(invocation ->
                CandidateFixture.receipt(invocation.getArgument(0), invocation.getArgument(1)));
        var properties = new AgentProperties(true, "local-test-token", "scripted-model",
                new AgentProperties.Provider("http://provider.invalid", "unused", 2000, false), null,
                new AgentProperties.MiniLoop(true, 6, 8, 2), null);
        var registry = new ToolRegistry(List.of(new GetContractTool(catalog), new PlanReuseTool(runtime),
                new ValidateCandidateTool(runtime), new ReviewCandidateTool(reviewer, mapper)));
        var engine = new MiniAgentLoopEngine(new AgentStateMachine(), registry, new ToolInvocationValidator(mapper),
                new MiniLoopToolPolicy(), provider, new UntrustedContentBoundary(), mapper, properties);
        return engine.execute(UUID.randomUUID(), 7, TASK, Map.of("selectedContractId", 1));
    }

    private AgentDecision bundledCheck() {
        try {
            return AgentDecision.toolCall("plan_reuse.v1", json(Map.of("baseContractId", 1,
                    "plan", CandidateFixture.plan(), "candidateSource", CandidateFixture.SOURCE)), "Check the candidate");
        } catch (Exception exception) {
            throw new AssertionError("Unable to load local test plan", exception);
        }
    }

    private AgentDecision candidateCheck(String source) {
        return AgentDecision.toolCall("validate_candidate.v1", json(Map.of("sourceCode", source)), "Check the revision");
    }

    private AgentDecision finalSource(AgentDecisionRequest request, String source) {
        return AgentDecision.finalAnswer("Deliver the retained source with its actual check status",
                request.evidence().stream().map(item -> item.id()).toList(), "The agent chooses to stop")
                .withProviderMetadata(Map.of("sourceCode", source));
    }

    private JsonNode reviewResponse() {
        return mapper.valueToTree(Map.of("choices", List.of(Map.of("message", Map.of("content",
                "{\"findingSummary\":\"No discrepancy identified\",\"codeIssues\":[],\"testIssues\":[],\"missingChecks\":[],\"recommendation\":\"RETAIN_SOURCE\"}")))));
    }

    private int completedModelCalls(MiniLoopResult result) {
        return result.events().stream().filter(event -> event.type().equals("run.completed"))
                .map(event -> mapper.valueToTree(event.data()).at("/payload/modelCalls").asInt(-1))
                .findFirst().orElseThrow(() -> new AssertionError("Missing completed-run accounting event"));
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new AssertionError("Unable to serialize local test data", exception); }
    }
}
