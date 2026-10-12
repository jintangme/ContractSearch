package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CandidateProgressTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void currentCandidateAndItsReceiptRemainExplicitWithoutChangingStoredStatus() {
        ObjectNode first = receipt(true), second = receipt(true);
        scenario(first, "check", false, false);
        scenario(second, "check", false, false);
        var earlier = candidate("earlier", first);
        var current = candidate("current", second);
        JsonNode summary = summary(earlier, current);

        assertThat(summary.path("currentCandidateId").asText()).isEqualTo(current.id());
        assertThat(summary.path("previousCandidateId").asText()).isEqualTo(earlier.id());
        assertThat(summary.path("currentSourceHash").asText()).isEqualTo(current.sourceHash());
        assertThat(summary.path("currentCompileSucceeded").asBoolean()).isTrue();
        assertThat(summary.at("/scenarioDelta/stillFailing/count").asInt()).isOne();
        assertThat(current.passed()).isFalse();
        assertThat(current.receipt()).isEqualTo(second);
        assertThat(CandidateProgress.summary(List.of())).isEmpty();
    }

    @Test void changesAreAlignedToActualStepObservationsRatherThanMissingRowsOrOverallFlags() {
        ObjectNode before = receipt(true), now = receipt(true);
        scenario(before, "fixed", false, true, false);
        scenario(now, "fixed", true, true, true);
        scenario(before, "regression", true, true);
        scenario(now, "regression", false, false);
        scenario(before, "persistent", false, false);
        scenario(now, "persistent", false, false);
        scenario(before, "blocked_later", false, true, false);
        scenario(now, "blocked_later", false, false); // The old failing step at index 1 never ran.

        JsonNode delta = summary(candidate("before", before), candidate("now", now)).path("scenarioDelta");

        assertThat(delta.at("/resolved/count").asInt()).isOne();
        assertThat(delta.at("/resolved/items/0/scenarioId").asText()).isEqualTo("fixed");
        assertThat(delta.at("/newlyObserved/count").asInt()).isEqualTo(2);
        assertThat(delta.at("/stillFailing/count").asInt()).isOne();
        assertThat(delta.at("/stillFailing/items/0/scenarioId").asText()).isEqualTo("persistent");
        assertThat(delta.at("/notRechecked/count").asInt()).isOne();
        assertThat(delta.at("/notRechecked/items/0/scenarioId").asText()).isEqualTo("blocked_later");
        assertThat(delta.at("/notRechecked/items/0/stepIndex").asInt()).isOne();
    }

    @Test void compileFailureCannotResolveEarlierBehaviorFailures() {
        ObjectNode before = receipt(true), now = receipt(false);
        scenario(before, "behavior", false, false);
        error(now, "A parser error", 10);

        JsonNode summary = summary(candidate("before", before), candidate("now", now));

        assertThat(summary.path("currentCompileSucceeded").asBoolean()).isFalse();
        assertThat(summary.at("/scenarioDelta/resolved/count").asInt()).isZero();
        assertThat(summary.at("/scenarioDelta/notRechecked/count").asInt()).isOne();
        assertThat(summary.at("/compilerDelta/newlyObserved/count").asInt()).isOne();
    }

    @Test void shiftedCompilerLocationsRepeatAndDifferentErrorsMayOnlyMaskOldDiagnostics() {
        ObjectNode before = receipt(false), shifted = receipt(false), different = receipt(false);
        error(before, "A parser error", 10);
        error(shifted, "A parser error", 40);
        error(different, "Another parser error", 2);
        var first = candidate("before", before);
        var moved = candidate("shifted", shifted);

        JsonNode same = summary(first, moved).path("compilerDelta");
        assertThat(same.at("/repeated/count").asInt()).isOne();
        assertThat(same.at("/newlyObserved/count").asInt()).isZero();
        JsonNode changed = summary(moved, candidate("different", different)).path("compilerDelta");
        assertThat(changed.at("/resolved/count").asInt()).isZero();
        assertThat(changed.at("/notRechecked/count").asInt()).isOne();
        assertThat(changed.at("/newlyObserved/count").asInt()).isOne();
        JsonNode compiled = summary(moved, candidate("compiles", receipt(true))).path("compilerDelta");
        assertThat(compiled.at("/resolved/count").asInt()).isOne();
        assertThat(compiled.at("/notRechecked/count").asInt()).isZero();
    }

    @Test void summariesAreBoundedAndDoNotCompareDifferentFrozenPlans() {
        ObjectNode many = receipt(false);
        for (int i = 0; i < 7; i++) error(many, "error " + i + "x".repeat(500), i);
        JsonNode current = summary(candidate("many", many));
        assertThat(current.path("comparisonAvailable").asBoolean()).isFalse();
        assertThat(current.has("previousCandidateId")).isFalse();
        assertThat(current.at("/compilerDelta/newlyObserved/count").asInt()).isEqualTo(7);
        assertThat(current.at("/compilerDelta/newlyObserved/items")).hasSize(4);
        assertThat(current.at("/compilerDelta/newlyObserved/omitted").asInt()).isEqualTo(3);
        assertThat(current.at("/compilerDelta/newlyObserved/items/0/message").asText()).hasSize(400);
        var changedPlan = new AgentWorkspace.Candidate("other", "other", "other", "other-plan", false, receipt(true));
        JsonNode uncomparable = summary(candidate("many", many), changedPlan);
        assertThat(uncomparable.path("comparisonAvailable").asBoolean()).isFalse();
        assertThat(uncomparable.at("/compilerDelta/resolved/count").asInt()).isZero();
    }

    private AgentWorkspace.Candidate candidate(String source, JsonNode receipt) {
        String hash = SourceHashes.sha256(source);
        return new AgentWorkspace.Candidate("candidate:" + hash, hash, source, "frozen-plan", false, receipt);
    }
    private JsonNode summary(AgentWorkspace.Candidate... candidates) {
        return mapper.valueToTree(CandidateProgress.summary(List.of(candidates)));
    }
    private ObjectNode receipt(boolean compiles) {
        ObjectNode receipt = mapper.createObjectNode();
        receipt.putObject("compile").put("success", compiles).putArray("errors");
        receipt.putArray("scenarios");
        return receipt;
    }
    private void scenario(ObjectNode receipt, String id, boolean passed, boolean... steps) {
        var row = receipt.withArray("scenarios").addObject().put("id", id).put("passed", passed);
        var output = row.putArray("steps");
        for (int i = 0; i < steps.length; i++) output.addObject().put("index", i).put("method", "call")
                .put("passed", steps[i]).put("expected", "1").put("observed", steps[i] ? "1" : "0");
    }
    private void error(ObjectNode receipt, String message, int line) {
        ((ObjectNode) receipt.path("compile")).withArray("errors").addObject()
                .put("severity", "error").put("message", message).put("line", line);
    }
}
