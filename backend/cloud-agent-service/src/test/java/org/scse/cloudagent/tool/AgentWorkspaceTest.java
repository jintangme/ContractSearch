package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AgentWorkspaceTest {
    @Test void canonicalHashMatchesNodeWithUnicodeAndNestedArrays() throws Exception {
        assertThat(SourceHashes.planHash(CandidateFixture.plan()))
                .isEqualTo("2a709a92f4e51b67cea651106e23f406caaaee3e301021afe52fa79689909d6c");
        var mapper = new ObjectMapper();
        assertThat(SourceHashes.planHash(mapper.readTree("{\"args\":[0.0,1.0,-0.0]}")))
                .isEqualTo(SourceHashes.planHash(mapper.readTree("{\"args\":[0,1,0]}")));
    }

    @Test void requiresCompleteSourceAndFreezesChecksAgainstMutation() throws Exception {
        var w = new AgentWorkspace(3); var p = CandidateFixture.plan();
        var truncated = w.observeSource(1, CandidateFixture.SOURCE, false);
        assertThatThrownBy(() -> w.installPlan(truncated, p)).isInstanceOf(InvalidToolArgumentsException.class);
        var read = w.observeSource(1, CandidateFixture.SOURCE, true);
        w.installPlan(read, p);
        String hash = w.planHash();
        ((ObjectNode) p).put("contractName", "Mutated");
        ((ObjectNode) w.plan()).put("contractName", "AlsoMutated");
        assertThat(w.planHash()).isEqualTo(hash);
        assertThat(w.plan().path("contractName").asText()).isEqualTo("Counter");
        assertThatThrownBy(() -> w.installPlan(read, p)).hasMessageContaining("frozen");
        assertThatThrownBy(() -> w.requireCurrentPlan("other")).hasMessageContaining("stale");
    }

    @Test void sourceChangesInvalidateEvenPreviouslyPassingCandidates() throws Exception {
        var w = workspace();
        var c = w.recordCandidate(CandidateFixture.SOURCE, CandidateFixture.receipt(CandidateFixture.SOURCE, w.plan()));
        assertThat(w.requirePassingCandidate(c.id()).sourceCode()).isEqualTo(CandidateFixture.SOURCE);
        // Reading a new source version invalidates old plan and evidence.
        w.observeSource(1, CandidateFixture.SOURCE + "\n// changed", true);
        assertThatThrownBy(() -> w.requirePassingCandidate(c.id())).hasMessageContaining("current sourceHash");
        assertThatThrownBy(() -> w.candidateForSource(CandidateFixture.SOURCE)).hasMessageContaining("current sourceHash");
        assertThat(new AgentWorkspace().hasPlan()).isFalse();
    }

    @Test void rejectsForgedOrIncompletePassingReceipts() throws Exception {
        List<Consumer<ObjectNode>> corruptions = List.of(
                r -> r.with("compile").put("success", false),
                r -> ((ArrayNode) r.path("scenarios")).remove(0),
                r -> ((ArrayNode) r.at("/scenarios/0/steps")).remove(0),
                r -> ((ObjectNode) r.at("/scenarios/0/steps/0")).put("passed", false),
                r -> ((ObjectNode) r.at("/scenarios/0/steps/0")).put("index", 9),
                r -> ((ObjectNode) r.at("/scenarios/0")).put("requirementId", "wrong")
        );
        for (var corrupt : corruptions) {
            var w = workspace(); var r = CandidateFixture.receipt(CandidateFixture.SOURCE, w.plan()); corrupt.accept(r);
            var c = w.recordCandidate(CandidateFixture.SOURCE, r);
            assertThat(c.passed()).isFalse();
            assertThatThrownBy(() -> w.requirePassingCandidate(c.id())).hasMessageContaining("every declared check");
        }
        var w = workspace(); var mismatched = CandidateFixture.receipt("different", w.plan());
        assertThatThrownBy(() -> w.recordCandidate(CandidateFixture.SOURCE, mismatched)).hasMessageContaining("does not match");
    }

    @Test void candidateBudgetAndReceiptsAreRunOwned() throws Exception {
        var w = workspace();
        var c = w.recordCandidate("one", CandidateFixture.receipt("one", w.plan()));
        ((ObjectNode) c.receipt()).put("allChecksPassed", false);
        assertThat(c.receipt().path("allChecksPassed").asBoolean()).isTrue();
        assertThatThrownBy(() -> w.requireNewCandidate("one")).hasMessageContaining("already checked");
        w.recordCandidate("two", CandidateFixture.receipt("two", w.plan()));
        assertThat(w.canValidateCandidate()).isTrue();
        w.recordCandidate("three", CandidateFixture.receipt("three", w.plan()));
        assertThat(w.canValidateCandidate()).isFalse();
        assertThatThrownBy(() -> w.requireNewCandidate("four")).hasMessageContaining("budget exhausted");
        assertThatThrownBy(() -> workspace().requirePassingCandidate(c.id())).hasMessageContaining("Select an existing candidate");
    }

    @Test void newToolsExposeParseableSchemasAndApprovedSideEffects() throws Exception {
        var client = mock(CandidateRuntimeClient.class);
        for (AgentTool tool : List.of(new PlanReuseTool(client), new ValidateCandidateTool(client))) {
            assertThat(new ObjectMapper().readTree(tool.definition().inputSchema()).isObject()).isTrue();
            new MiniLoopToolPolicy().authorize(tool);
        }
    }
    private AgentWorkspace workspace() throws Exception {
        var w = new AgentWorkspace(3);
        w.installPlan(w.observeSource(1, CandidateFixture.SOURCE, true), CandidateFixture.plan());
        return w;
    }
}
