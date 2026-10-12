package org.scse.cloudagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.state.AgentStateMachine;
import org.scse.cloudagent.tool.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Engine budgets and audit semantics, with every external dependency mocked. */
class CandidateValidationReuseTest {
    private static final String VALIDATE = "validate_candidate.v1";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractCatalogClient catalog = mock(ContractCatalogClient.class);
    private final CandidateRuntimeClient runtime = mock(CandidateRuntimeClient.class);

    @Test void repeatedChecksReuseOneActualReceiptAndLeaveTheSixthModelDecisionForFinalDelivery() {
        List<Integer> modelBudget = new ArrayList<>();
        List<Integer> toolBudget = new ArrayList<>();
        StructuredActionProvider provider = request -> {
            modelBudget.add(request.remainingModelCalls());
            toolBudget.add(request.remainingToolCalls());
            if (request.step() == 1) return bundledCheck();
            assertThat(request.evidence()).hasSize(2); // source plus the sole real validation
            if (request.step() < 6) {
                assertThat(request.tools()).extracting(ToolDefinition::name).contains(VALIDATE);
                return check();
            }
            assertThat(request.tools()).isEmpty();
            assertThat(request.observations()).filteredOn(text -> text.contains("\"receiptReused\":true")).hasSize(4);
            return finalSource(request);
        };

        MiniLoopResult result = execute(provider, 8);

        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.answer()).contains(CandidateFixture.SOURCE);
        assertThat(result.steps()).isEqualTo(6);
        assertThat(result.toolCalls()).isEqualTo(6); // hydration + bundled execution + four reused receipts
        assertThat(modelBudget).containsExactly(6, 5, 4, 3, 2, 1);
        assertThat(toolBudget).containsExactly(7, 6, 5, 4, 3, 2);
        assertThat(completedPayload(result).path("modelCalls").asInt()).isEqualTo(6);
        assertThat(result.evidence()).hasSize(2);
        assertThat(result.events()).noneMatch(event -> event.type().equals("tool.skipped")
                || event.type().equals("candidate.delivery_fallback"));
        List<JsonNode> checks = completedTools(result, VALIDATE);
        assertThat(checks).hasSize(4).allSatisfy(event -> {
            assertThat(event.path("executionPerformed").asBoolean(true)).isFalse();
            assertThat(event.path("receiptReused").asBoolean()).isTrue();
            assertThat(event.path("evidenceIds")).isEmpty();
        });
        assertThat(completedTools(result, "plan_reuse.v1")).singleElement().satisfies(event -> {
            assertThat(event.path("executionPerformed").asBoolean()).isTrue();
            assertThat(event.path("receiptReused").asBoolean(true)).isFalse();
        });
        verify(runtime, times(1)).validateCandidate(anyString(), any(JsonNode.class));
    }

    @Test void cachedChecksConsumeToolBudgetAndCannotExtendTheRun() {
        StructuredActionProvider provider = request -> {
            if (request.step() == 1) return bundledCheck();
            if (request.step() < 4) return check();
            assertThat(request.step()).isEqualTo(4);
            assertThat(request.remainingToolCalls()).isZero();
            assertThat(request.remainingModelCalls()).isEqualTo(3);
            assertThat(request.tools()).isEmpty();
            return finalSource(request);
        };

        MiniLoopResult result = execute(provider, 4);

        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.toolCalls()).isEqualTo(4);
        assertThat(result.steps()).isEqualTo(4);
        assertThat(completedPayload(result).path("modelCalls").asInt()).isEqualTo(4);
        assertThat(completedTools(result, VALIDATE)).hasSize(2);
        assertThat(result.events()).noneMatch(event -> event.type().equals("candidate.delivery_fallback"));
        verify(runtime, times(1)).validateCandidate(anyString(), any(JsonNode.class));
    }

    @Test void repeatingPastTheAdvertisedModelLimitStopsAtSixCallsWithoutLoopDetected() {
        StructuredActionProvider provider = request -> request.step() == 1 ? bundledCheck() : check();

        MiniLoopResult result = execute(provider, 8);

        assertThat(result.state()).as(result.failureMessage()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(6);
        assertThat(completedPayload(result).path("modelCalls").asInt()).isEqualTo(6);
        assertThat(result.toolCalls()).isEqualTo(7);
        assertThat(result.events()).anyMatch(event -> event.type().equals("candidate.delivery_fallback")
                && payload(event.data()).path("reason").asText().contains("Decision budget exhausted"));
        assertThat(result.events()).noneMatch(event -> event.data().toString().contains("LOOP_DETECTED"));
        verify(runtime, times(1)).validateCandidate(anyString(), any(JsonNode.class));
    }

    private MiniLoopResult execute(StructuredActionProvider provider, int maxTools) {
        when(catalog.getContract(1, 7)).thenReturn(Map.of("contractName", "Counter", "sourceCode", CandidateFixture.SOURCE));
        when(runtime.validatePlan(any(JsonNode.class), anyString(), anyBoolean())).thenAnswer(invocation ->
                mapper.createObjectNode().put("planHash", SourceHashes.planHash(invocation.getArgument(0))));
        when(runtime.validateCandidate(anyString(), any(JsonNode.class))).thenAnswer(invocation ->
                CandidateFixture.receipt(invocation.getArgument(0), invocation.getArgument(1)));
        var properties = new AgentProperties(true, "local-test-token", "scripted-model",
                new AgentProperties.Provider("http://provider.invalid", "unused", 2000, false), null,
                new AgentProperties.MiniLoop(true, 6, maxTools, 2), null);
        var registry = new ToolRegistry(List.of(new GetContractTool(catalog), new PlanReuseTool(runtime), new ValidateCandidateTool(runtime)));
        var engine = new MiniAgentLoopEngine(new AgentStateMachine(), registry, new ToolInvocationValidator(mapper),
                new MiniLoopToolPolicy(), provider, new UntrustedContentBoundary(), mapper, properties);
        return engine.execute(UUID.randomUUID(), 7, "Repair Counter and deliver complete source", Map.of("selectedContractId", 1));
    }
    private AgentDecision bundledCheck() {
        try {
            return AgentDecision.toolCall("plan_reuse.v1", mapper.writeValueAsString(Map.of("baseContractId", 1,
                    "plan", CandidateFixture.plan(), "candidateSource", CandidateFixture.SOURCE)), "Check the source");
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private AgentDecision check() {
        try {
            return AgentDecision.toolCall(VALIDATE, mapper.writeValueAsString(Map.of("sourceCode", CandidateFixture.SOURCE)), "Inspect the same source receipt");
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private AgentDecision finalSource(AgentDecisionRequest request) {
        return AgentDecision.finalAnswer("Deliver the source with its actual check status",
                request.evidence().stream().map(item -> item.id()).toList(), "No further action needed")
                .withProviderMetadata(Map.of("sourceCode", CandidateFixture.SOURCE));
    }
    private List<JsonNode> completedTools(MiniLoopResult result, String name) {
        return result.events().stream().filter(event -> event.type().equals("tool.completed"))
                .map(event -> payload(event.data())).filter(event -> event.path("tool").asText().equals(name)).toList();
    }
    private JsonNode completedPayload(MiniLoopResult result) {
        return result.events().stream().filter(event -> event.type().equals("run.completed"))
                .map(event -> payload(event.data())).findFirst().orElseThrow();
    }
    private JsonNode payload(Object data) { return mapper.valueToTree(data).path("payload"); }
}
