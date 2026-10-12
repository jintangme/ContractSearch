package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.scse.cloudagent.service.GlmStructuredActionClient;
import org.scse.cloudagent.service.ProviderHttpException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewCandidateToolTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private AgentWorkspace workspace() throws Exception {
        var workspace = new AgentWorkspace();
        workspace.installPlan(workspace.observeSource(1, CandidateFixture.SOURCE, true), CandidateFixture.plan());
        var receipt = CandidateFixture.receipt(CandidateFixture.SOURCE, workspace.plan());
        receipt.put("allChecksPassed", false);
        workspace.recordCandidate(CandidateFixture.SOURCE, receipt);
        return workspace;
    }

    @Test void freshReviewUsesServerOwnedInputsAndCannotPromoteAReceipt() throws Exception {
        var client = mock(GlmStructuredActionClient.class);
        when(client.complete(anyString(), anyString(), anyList())).thenReturn(mapper.valueToTree(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "{\"recommendation\":\"RETAIN_SOURCE\"}"))))));
        var workspace = workspace(); var budget = new ModelCallBudget(1);
        String task = "Preserve the counter's exact behavior";
        var result = new ReviewCandidateTool(client, mapper).execute(mapper.readTree("{}"),
                new ToolExecutionContext(1, UUID.randomUUID(), workspace, task, budget));
        var prompt = ArgumentCaptor.forClass(String.class);
        verify(client).complete(eq(ReviewCandidateTool.REVIEW_PROMPT), prompt.capture(), eq(List.of()));
        var input = mapper.readTree(prompt.getValue());
        assertThat(input.path("originalTask").asText()).isEqualTo(task);
        assertThat(input.path("candidateSource").asText()).isEqualTo(CandidateFixture.SOURCE);
        assertThat(input.path("baseSource").asText()).isEqualTo(CandidateFixture.SOURCE);
        assertThat(SourceHashes.planHash(input.path("authoredPlan"))).isEqualTo(workspace.planHash());
        assertThat(input.has("previousReasoning")).isFalse();
        assertThat(input.has("hiddenOracle")).isFalse();
        assertThat(result.evidence()).isEmpty();
        assertThat(result.output()).containsEntry("scope", "MODEL_INFERENCE_NOT_EXECUTION_EVIDENCE");
        assertThat(workspace.latestCandidate().passed()).isFalse();
        assertThat(budget.used()).isEqualTo(1);
    }

    @Test void exhaustedAllowanceDoesNotMakeAProviderRequest() throws Exception {
        var client = mock(GlmStructuredActionClient.class);
        var context = new ToolExecutionContext(1, UUID.randomUUID(), workspace(), "Original task", new ModelCallBudget(0));
        assertThatThrownBy(() -> new ReviewCandidateTool(client, mapper).execute(mapper.readTree("{}"), context))
                .isInstanceOf(AgentToolException.class).hasMessageContaining("No model-call allowance");
        verifyNoInteractions(client);
    }

    @Test void providerFailureStillConsumesItsAttemptWithoutRetry() throws Exception {
        var client = mock(GlmStructuredActionClient.class);
        when(client.complete(anyString(), anyString(), anyList())).thenThrow(new IOException("provider unavailable"));
        var budget = new ModelCallBudget(1);
        var context = new ToolExecutionContext(1, UUID.randomUUID(), workspace(), "Original task", budget);
        var result = new ReviewCandidateTool(client, mapper).execute(mapper.readTree("{}"), context);
        assertThat(result.output()).containsEntry("status", "ADVISORY_UNAVAILABLE");
        assertThat(result.evidence()).isEmpty();
        assertThat(budget.used()).isEqualTo(1);
        verify(client, times(1)).complete(anyString(), anyString(), anyList());
    }

    @Test void providerBalanceFailurePreservesItsCauseAndChargesTheActualRequest() throws Exception {
        var client = mock(GlmStructuredActionClient.class);
        var failure = new ProviderHttpException(402, 1);
        when(client.complete(anyString(), anyString(), anyList())).thenThrow(failure);
        var budget = new ModelCallBudget(1);
        var context = new ToolExecutionContext(1, UUID.randomUUID(), workspace(), "Original task", budget);
        assertThatThrownBy(() -> new ReviewCandidateTool(client, mapper).execute(mapper.readTree("{}"), context))
                .isInstanceOf(AgentToolException.class).hasCause(failure).hasMessageContaining("balance unavailable");
        assertThat(budget.used()).isOne();
        verify(client, times(1)).complete(anyString(), anyString(), anyList());
    }

    @Test void localBalanceStopPreservesItsCauseAndRefundsAnUnsentRequest() throws Exception {
        var client = mock(GlmStructuredActionClient.class);
        var failure = new ProviderHttpException(402, 0);
        when(client.complete(anyString(), anyString(), anyList())).thenThrow(failure);
        var budget = new ModelCallBudget(1);
        var context = new ToolExecutionContext(1, UUID.randomUUID(), workspace(), "Original task", budget);
        assertThatThrownBy(() -> new ReviewCandidateTool(client, mapper).execute(mapper.readTree("{}"), context))
                .isInstanceOf(AgentToolException.class).hasCause(failure).hasMessageContaining("balance unavailable");
        assertThat(budget.used()).isZero();
        verify(client, times(1)).complete(anyString(), anyString(), anyList());
    }

    @Test void advisoryFocusAboveTheOldLimitIsAcceptedWithoutAnotherModelDecision() throws Exception {
        var client = mock(GlmStructuredActionClient.class);
        when(client.complete(anyString(), anyString(), anyList())).thenReturn(mapper.valueToTree(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "No discrepancy identified"))))));
        String focus = "f".repeat(589);
        var budget = new ModelCallBudget(1);
        var result = new ReviewCandidateTool(client, mapper).execute(mapper.valueToTree(Map.of("focus", focus)),
                new ToolExecutionContext(1, UUID.randomUUID(), workspace(), "Original task", budget));
        var prompt = ArgumentCaptor.forClass(String.class);
        verify(client, times(1)).complete(anyString(), prompt.capture(), eq(List.of()));
        var input = mapper.readTree(prompt.getValue());
        assertThat(input.path("focus").asText()).isEqualTo(focus);
        assertThat(input.path("focusTruncated").asBoolean()).isFalse();
        assertThat(result.output()).containsEntry("focusTruncated", false);
        assertThat(budget.used()).isOne();
        assertThat(result.evidence()).isEmpty();
    }

    @Test void verboseAdvisoryFocusIsTransparentlyClippedWithoutTruncatingTheTaskOrSource() throws Exception {
        var client = mock(GlmStructuredActionClient.class);
        when(client.complete(anyString(), anyString(), anyList())).thenReturn(mapper.valueToTree(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "Review completed"))))));
        String focus = "f".repeat(1200) + "o".repeat(10800);
        assertThat(focus.length()).isEqualTo(12000);
        String task = "Original complete task ".repeat(100);
        var budget = new ModelCallBudget(1);
        var result = new ReviewCandidateTool(client, mapper).execute(mapper.valueToTree(Map.of("focus", focus)),
                new ToolExecutionContext(1, UUID.randomUUID(), workspace(), task, budget));
        var prompt = ArgumentCaptor.forClass(String.class);
        verify(client, times(1)).complete(anyString(), prompt.capture(), eq(List.of()));
        var input = mapper.readTree(prompt.getValue());
        assertThat(input.path("focus").asText()).isEqualTo("f".repeat(1200));
        assertThat(input.path("focusTruncated").asBoolean()).isTrue();
        assertThat(input.path("focusOriginalLength").asInt()).isEqualTo(12000);
        assertThat(input.path("originalTask").asText()).isEqualTo(task);
        assertThat(input.path("baseSource").asText()).isEqualTo(CandidateFixture.SOURCE);
        assertThat(input.path("candidateSource").asText()).isEqualTo(CandidateFixture.SOURCE);
        assertThat(result.output()).containsEntry("focusTruncated", true);
        assertThat(budget.used()).isOne();
    }

    @Test void focusStillHasABoundedTextOnlyInputContractBeforeSpending() throws Exception {
        var client = mock(GlmStructuredActionClient.class);
        var tool = new ReviewCandidateTool(client, mapper);
        var budget = new ModelCallBudget(1);
        var context = new ToolExecutionContext(1, UUID.randomUUID(), workspace(), "Original task", budget);
        assertThatThrownBy(() -> tool.execute(mapper.valueToTree(Map.of("focus", "f".repeat(12001))), context))
                .isInstanceOf(InvalidToolArgumentsException.class).hasMessageContaining("12000");
        assertThatThrownBy(() -> tool.execute(mapper.readTree("{\"focus\":12}"), context))
                .isInstanceOf(InvalidToolArgumentsException.class).hasMessageContaining("string");
        assertThat(budget.used()).isZero();
        verifyNoInteractions(client);
    }

    @Test void unavailableReviewStillReportsAdvisoryFocusTruncationWithoutRetry() throws Exception {
        var client = mock(GlmStructuredActionClient.class);
        when(client.complete(anyString(), anyString(), anyList())).thenThrow(new IOException("provider unavailable"));
        var budget = new ModelCallBudget(1);
        var result = new ReviewCandidateTool(client, mapper).execute(mapper.valueToTree(Map.of("focus", "f".repeat(1201))),
                new ToolExecutionContext(1, UUID.randomUUID(), workspace(), "Original task", budget));
        assertThat(result.output()).containsEntry("status", "ADVISORY_UNAVAILABLE").containsEntry("focusTruncated", true);
        assertThat(budget.used()).isOne();
        verify(client, times(1)).complete(anyString(), anyString(), anyList());
    }

    @Test void missingOrStaleCandidateIsRejectedBeforeSpending() throws Exception {
        var client = mock(GlmStructuredActionClient.class); var tool = new ReviewCandidateTool(client, mapper);
        var budget = new ModelCallBudget(1);
        var empty = new ToolExecutionContext(1, UUID.randomUUID(), new AgentWorkspace(), "Task", budget);
        assertThatThrownBy(() -> tool.execute(mapper.readTree("{}"), empty)).hasMessageContaining("checked candidate");
        var workspace = workspace(); workspace.observeSource(1, CandidateFixture.SOURCE + "\n// changed", true);
        var stale = new ToolExecutionContext(1, UUID.randomUUID(), workspace, "Task", budget);
        assertThatThrownBy(() -> tool.execute(mapper.readTree("{}"), stale)).hasMessageContaining("current sourceHash");
        assertThat(budget.used()).isZero(); verifyNoInteractions(client);
    }

    @Test void planScenarioLimitReportsThePublicFieldBeforeRuntimeExecution() throws Exception {
        var runtime = mock(CandidateRuntimeClient.class);
        var workspace = new AgentWorkspace(); workspace.observeSource(1, CandidateFixture.SOURCE, true);
        var plan = (com.fasterxml.jackson.databind.node.ObjectNode) CandidateFixture.plan(); plan.remove("requirements");
        var scenarios = plan.putArray("scenarios");
        for (int index = 0; index < 13; index++) scenarios.addObject().put("id", "s" + index).putArray("steps");
        var args = mapper.valueToTree(Map.of("baseContractId", 1, "candidateSource", CandidateFixture.SOURCE, "plan", plan));
        assertThatThrownBy(() -> new PlanReuseTool(runtime).execute(args,
                new ToolExecutionContext(1, UUID.randomUUID(), workspace)))
                .hasMessageContaining("plan.scenarios").hasMessageContaining("13");
        verifyNoInteractions(runtime);
        assertThat(workspace.latestDraft().sourceCode()).isEqualTo(CandidateFixture.SOURCE);
    }
}
