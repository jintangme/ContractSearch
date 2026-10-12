package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.scse.cloudagent.service.GlmStructuredActionClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewCandidateDomainTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void reviewReceivesPassingFailingAndUnexecutedRevertExpectationsWithoutCertifyingThem() throws Exception {
        var workspace = workspace();
        ObjectNode receipt = CandidateFixture.receipt(CandidateFixture.SOURCE, workspace.plan());
        receipt.put("allChecksPassed", false);
        var scenario = (ObjectNode) receipt.at("/scenarios/0");
        scenario.put("passed", false);
        var steps = scenario.putArray("steps");
        steps.addObject().put("index", 0).put("passed", true).put("reason", "EXPECTED_REVERT");
        steps.addObject().put("index", 1).put("passed", false).put("reason", "EXPECTED_REVERT_NOT_OBSERVED");
        var candidate = workspace.recordCandidate(CandidateFixture.SOURCE, receipt);
        var client = client();
        var budget = new ModelCallBudget(1);

        var result = new ReviewCandidateTool(client, mapper).execute(mapper.createObjectNode(),
                new ToolExecutionContext(1, UUID.randomUUID(), workspace,
                        "Reject unauthorized callers, and preserve authorized behavior.", budget));

        var serialized = ArgumentCaptor.forClass(String.class);
        verify(client, times(1)).complete(eq(ReviewCandidateTool.REVIEW_PROMPT), serialized.capture(), eq(List.of()));
        JsonNode input = mapper.readTree(serialized.getValue());
        assertThat(SourceHashes.planHash(input.path("authoredPlan"))).isEqualTo(workspace.planHash());
        assertThat(input.path("candidateSource").asText()).isEqualTo(candidate.sourceCode());
        JsonNode observations = input.path("revertCheckObservations");
        assertThat(observations).hasSize(3);
        assertThat(input.path("totalRevertChecks").asInt()).isEqualTo(3);
        assertThat(input.path("omittedRevertCheckObservations").asInt()).isZero();
        assertThat(observations.at("/0/scenarioId").asText()).isEqualTo("domain_checks");
        assertThat(observations.at("/0/status").asText()).isEqualTo("OBSERVATION_RECORDED");
        assertThat(observations.at("/0/passed").asBoolean()).isTrue();
        assertThat(observations.at("/1/status").asText()).isEqualTo("OBSERVATION_RECORDED");
        assertThat(observations.at("/1/passed").asBoolean()).isFalse();
        assertThat(observations.at("/1/reason").asText()).isEqualTo("EXPECTED_REVERT_NOT_OBSERVED");
        assertThat(observations.at("/2/status").asText()).isEqualTo("NOT_EXECUTED");
        assertThat(observations.get(2).has("passed")).isFalse();
        assertThat(ReviewCandidateTool.REVIEW_PROMPT).contains("expect=revert checks that passed", "report the uncertainty");
        assertThat(result.evidence()).isEmpty();
        assertThat(result.output()).containsEntry("scope", "MODEL_INFERENCE_NOT_EXECUTION_EVIDENCE");
        assertThat(workspace.requireCandidate(candidate.id()).receipt()).isEqualTo(receipt);
        assertThat(workspace.requireCandidate(candidate.id()).passed()).isFalse();
        assertThat(budget.used()).isOne();
    }

    @Test void compileFailureDoesNotTurnAbsentCheckObservationsIntoPassingReverts() throws Exception {
        var workspace = workspace();
        ObjectNode receipt = mapper.createObjectNode().put("sourceHash", SourceHashes.sha256(CandidateFixture.SOURCE))
                .put("planHash", workspace.planHash()).put("allChecksPassed", false);
        receipt.putObject("compile").put("success", false);
        receipt.putArray("scenarios");
        workspace.recordCandidate(CandidateFixture.SOURCE, receipt);
        var client = client();
        new ReviewCandidateTool(client, mapper).execute(mapper.createObjectNode(),
                new ToolExecutionContext(1, UUID.randomUUID(), workspace, "Preserve requested behavior", new ModelCallBudget(1)));

        var serialized = ArgumentCaptor.forClass(String.class);
        verify(client).complete(anyString(), serialized.capture(), eq(List.of()));
        JsonNode observations = mapper.readTree(serialized.getValue()).path("revertCheckObservations");
        assertThat(observations).hasSize(3);
        for (JsonNode observation : observations) {
            assertThat(observation.path("status").asText()).isEqualTo("NOT_EXECUTED");
            assertThat(observation.has("passed")).isFalse();
        }
    }

    private AgentWorkspace workspace() {
        ObjectNode plan = mapper.createObjectNode().put("contractName", "Counter");
        plan.putArray("constructorArgs").add("$account0");
        plan.putArray("requirements").addObject().put("id", "domain_checks").put("description", "Preserve caller rules");
        var scenario = plan.putArray("scenarios").addObject().put("id", "domain_checks").put("requirementId", "domain_checks");
        var steps = scenario.putArray("steps");
        for (int caller = 0; caller < 3; caller++) {
            var step = steps.addObject().put("kind", "write").put("method", "increment").put("from", caller).put("expect", "revert");
            step.putArray("args");
        }
        var workspace = new AgentWorkspace();
        workspace.installPlan(workspace.observeSource(1, CandidateFixture.SOURCE, true), plan);
        return workspace;
    }
    private GlmStructuredActionClient client() throws Exception {
        var client = mock(GlmStructuredActionClient.class);
        when(client.complete(anyString(), anyString(), anyList())).thenReturn(mapper.valueToTree(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "{\"recommendation\":\"UNCERTAIN\"}"))))));
        return client;
    }
}
