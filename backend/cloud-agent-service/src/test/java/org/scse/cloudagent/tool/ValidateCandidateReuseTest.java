package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Run-owned receipt reuse only; no Node process or network I/O. */
class ValidateCandidateReuseTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CandidateRuntimeClient runtime = mock(CandidateRuntimeClient.class);
    private final ValidateCandidateTool tool = new ValidateCandidateTool(runtime);

    @Test void exactReceiptIsReusableAtTheCandidateCapWithoutNewEvidenceOrRuntimeExecution() throws Exception {
        AgentWorkspace workspace = workspace(1);
        mockPassingExecution();
        ToolExecutionResult first = check(workspace, CandidateFixture.SOURCE);
        JsonNode actualReceipt = mapper.valueToTree(first.output().get("validation"));
        AgentWorkspace.Candidate original = workspace.latestCandidate();
        String laterDraft = CandidateFixture.SOURCE + "\n// retained unexecuted revision\n";
        workspace.recordDraft(laterDraft);

        ToolExecutionResult reused = check(workspace, CandidateFixture.SOURCE);

        assertThat(workspace.canValidateCandidate()).isFalse();
        assertThat(first.evidence()).hasSize(1);
        assertThat(first.output()).containsEntry("executionPerformed", true).containsEntry("receiptReused", false);
        assertThat(reused.evidence()).isEmpty();
        assertThat(reused.output()).containsEntry("executionPerformed", false).containsEntry("receiptReused", true)
                .containsEntry("candidateId", original.id()).containsEntry("sourceHash", original.sourceHash())
                .containsEntry("planHash", workspace.planHash()).containsEntry("sourceCode", CandidateFixture.SOURCE)
                .containsEntry("checksPassed", true).containsEntry("validation", actualReceipt);
        assertThat(workspace.latestCandidate()).isSameAs(original);
        assertThat(workspace.latestDraft().sourceCode()).isEqualTo(laterDraft);
        assertThat(mapper.valueToTree(workspace.summary()).path("candidates")).hasSize(1);
        assertThatThrownBy(() -> check(workspace, laterDraft)).isInstanceOf(InvalidToolArgumentsException.class)
                .hasMessageContaining("budget exhausted");
        verify(runtime, times(1)).validateCandidate(CandidateFixture.SOURCE, workspace.plan());
        verifyNoMoreInteractions(runtime);
    }

    @Test void failedReceiptsRemainFailedAndAreIsolatedFromCallerMutation() throws Exception {
        AgentWorkspace workspace = workspace(2);
        ObjectNode failed = CandidateFixture.receipt(CandidateFixture.SOURCE, workspace.plan());
        failed.put("allChecksPassed", false);
        ((ObjectNode) failed.at("/scenarios/0")).put("passed", false).put("error", "Expected 2, received 1");
        ((ObjectNode) failed.at("/scenarios/0/steps/0")).put("passed", false);
        when(runtime.validateCandidate(anyString(), any(JsonNode.class))).thenReturn(failed);
        ToolExecutionResult first = check(workspace, CandidateFixture.SOURCE);
        JsonNode originalReceipt = ((JsonNode) first.output().get("validation")).deepCopy();
        ((ObjectNode) first.output().get("validation")).put("allChecksPassed", true);
        failed.put("allChecksPassed", true);

        ToolExecutionResult second = check(workspace, CandidateFixture.SOURCE);
        ((ObjectNode) second.output().get("validation")).put("callerOnly", true);
        ToolExecutionResult third = check(workspace, CandidateFixture.SOURCE);

        assertThat(second.evidence()).isEmpty();
        assertThat(third.evidence()).isEmpty();
        assertThat(third.output()).containsEntry("checksPassed", false).containsEntry("validation", originalReceipt)
                .containsEntry("executionPerformed", false).containsEntry("receiptReused", true);
        assertThat(workspace.latestCandidate().passed()).isFalse();
        assertThatThrownBy(() -> workspace.requirePassingCandidate(workspace.latestCandidate().id()))
                .hasMessageContaining("every declared check");
        verify(runtime, times(1)).validateCandidate(anyString(), any(JsonNode.class));
    }

    @Test void stalePlanOrBaseSourceCannotReuseAnOtherwiseMatchingReceipt() throws Exception {
        AgentWorkspace workspace = workspace(2);
        mockPassingExecution();
        check(workspace, CandidateFixture.SOURCE);
        ObjectNode stalePlan = args(CandidateFixture.SOURCE).put("planHash", "different-plan");
        assertThatThrownBy(() -> tool.execute(stalePlan, context(workspace)))
                .isInstanceOf(InvalidToolArgumentsException.class).hasMessageContaining("stale");

        workspace.observeSource(1, CandidateFixture.SOURCE + "\n// catalog changed\n", true);
        assertThatThrownBy(() -> check(workspace, CandidateFixture.SOURCE))
                .isInstanceOf(InvalidToolArgumentsException.class).hasMessageContaining("current sourceHash");
        verify(runtime, times(1)).validateCandidate(anyString(), any(JsonNode.class));
    }

    @Test void evenWhitespaceChangedSourceRequiresItsOwnExecutionReceipt() throws Exception {
        AgentWorkspace workspace = workspace(2);
        mockPassingExecution();
        ToolExecutionResult first = check(workspace, CandidateFixture.SOURCE);
        String changed = CandidateFixture.SOURCE + "\n";
        ToolExecutionResult second = check(workspace, changed);

        assertThat(second.output()).containsEntry("sourceCode", changed).containsEntry("executionPerformed", true)
                .containsEntry("receiptReused", false).containsEntry("sourceHash", SourceHashes.sha256(changed));
        assertThat(second.output().get("candidateId")).isNotEqualTo(first.output().get("candidateId"));
        assertThat(second.evidence()).hasSize(1);
        assertThat(mapper.valueToTree(workspace.summary()).path("candidates")).hasSize(2);
        verify(runtime, times(2)).validateCandidate(anyString(), any(JsonNode.class));
    }

    private AgentWorkspace workspace(int cap) throws Exception {
        AgentWorkspace workspace = new AgentWorkspace(cap);
        workspace.installPlan(workspace.observeSource(1, CandidateFixture.SOURCE, true), CandidateFixture.plan());
        return workspace;
    }
    private void mockPassingExecution() {
        when(runtime.validateCandidate(anyString(), any(JsonNode.class))).thenAnswer(invocation ->
                CandidateFixture.receipt(invocation.getArgument(0), invocation.getArgument(1)));
    }
    private ToolExecutionResult check(AgentWorkspace workspace, String source) {
        return tool.execute(args(source), context(workspace));
    }
    private ObjectNode args(String source) { return mapper.createObjectNode().put("sourceCode", source); }
    private ToolExecutionContext context(AgentWorkspace workspace) { return new ToolExecutionContext(7, UUID.randomUUID(), workspace); }
}
