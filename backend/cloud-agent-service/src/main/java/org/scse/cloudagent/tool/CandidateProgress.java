package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Descriptive differences between actual receipts, never a correctness ranking. */
public final class CandidateProgress {
    private CandidateProgress() { }
    private static final int MAX_ITEMS = 4;

    public static Map<String, Object> summary(List<AgentWorkspace.Candidate> candidates) {
        if (candidates.isEmpty()) return Map.of();
        var current = candidates.get(candidates.size() - 1);
        var previous = candidates.size() > 1 ? candidates.get(candidates.size() - 2) : null;
        boolean comparable = previous != null && current.planHash().equals(previous.planHash());
        JsonNode now = current.receipt(), before = comparable ? previous.receipt() : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentCandidateId", current.id());
        result.put("currentSourceHash", current.sourceHash());
        result.put("currentCompileSucceeded", now.path("compile").path("success").asBoolean(false));
        result.put("comparisonAvailable", comparable);
        if (comparable) result.put("previousCandidateId", previous.id());
        result.put("scope", "ACTUAL_RECEIPT_DIFFERENCES_NOT_TASK_CORRECTNESS");
        result.put("scenarioDelta", scenarioDelta(before, now));
        result.put("compilerDelta", compilerDelta(before, now));
        return result;
    }

    private static Map<String, Object> scenarioDelta(JsonNode before, JsonNode now) {
        var oldFailures = failures(before);
        var newFailures = failures(now);
        var observed = observations(now);
        List<Map<String, Object>> resolved = new ArrayList<>(), added = new ArrayList<>(),
                remaining = new ArrayList<>(), unchecked = new ArrayList<>();
        for (var entry : oldFailures.entrySet()) {
            if (newFailures.containsKey(entry.getKey())) remaining.add(newFailures.get(entry.getKey()));
            else if (observed.containsKey(entry.getKey()) && observed.get(entry.getKey())) resolved.add(entry.getValue());
            else unchecked.add(entry.getValue()); // Missing is not passing: compilation or an earlier step may have failed.
        }
        for (var entry : newFailures.entrySet()) if (!oldFailures.containsKey(entry.getKey())) added.add(entry.getValue());
        return Map.of("resolved", bounded(resolved), "newlyObserved", bounded(added),
                "stillFailing", bounded(remaining), "notRechecked", bounded(unchecked));
    }

    private static Map<String, Map<String, Object>> failures(JsonNode receipt) {
        Map<String, Map<String, Object>> failures = new LinkedHashMap<>();
        if (receipt == null || !receipt.path("compile").path("success").asBoolean(false)) return failures;
        for (JsonNode scenario : receipt.path("scenarios")) {
            String id = scenario.path("id").asText();
            boolean stepFailure = false;
            for (JsonNode step : scenario.path("steps")) {
                if (!step.path("passed").isBoolean() || step.path("passed").asBoolean()) continue;
                stepFailure = true;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("scenarioId", id); item.put("stepIndex", step.path("index").asInt(-1));
                item.put("method", clip(step.path("method").asText()));
                for (String key : List.of("reason", "expected", "observed")) {
                    if (step.has(key)) item.put(key, clip(step.get(key).isTextual() ? step.get(key).asText() : step.get(key).toString()));
                }
                failures.put(key(id, step.path("index").asInt(-1)), Map.copyOf(item));
            }
            if (!stepFailure && scenario.path("passed").isBoolean() && !scenario.path("passed").asBoolean())
                failures.put(key(id, -1), Map.of("scenarioId", id, "reason", clip(scenario.path("reason").asText(
                        scenario.path("error").asText("Scenario failed without an executed failing step")))));
        }
        return failures;
    }

    private static Map<String, Boolean> observations(JsonNode receipt) {
        Map<String, Boolean> observations = new LinkedHashMap<>();
        if (!receipt.path("compile").path("success").asBoolean(false)) return observations;
        for (JsonNode scenario : receipt.path("scenarios")) {
            String id = scenario.path("id").asText();
            if (scenario.path("passed").isBoolean()) observations.put(key(id, -1), scenario.path("passed").asBoolean());
            for (JsonNode step : scenario.path("steps")) if (step.path("passed").isBoolean())
                observations.put(key(id, step.path("index").asInt(-1)), step.path("passed").asBoolean());
        }
        return observations;
    }

    private static Map<String, Object> compilerDelta(JsonNode before, JsonNode now) {
        var previous = compilerErrors(before);
        var current = compilerErrors(now);
        List<Map<String, Object>> added = new ArrayList<>(), repeated = new ArrayList<>(),
                resolved = new ArrayList<>(), unchecked = new ArrayList<>();
        for (var entry : current.entrySet()) {
            (previous.containsKey(entry.getKey()) ? repeated : added).add(entry.getValue());
        }
        for (var entry : previous.entrySet()) if (!current.containsKey(entry.getKey())) {
            // A different parse error may mask an old one. Only successful
            // compilation establishes that an old compiler error is resolved.
            (now.path("compile").path("success").asBoolean(false) ? resolved : unchecked).add(entry.getValue());
        }
        return Map.of("newlyObserved", bounded(added), "repeated", bounded(repeated),
                "resolved", bounded(resolved), "notRechecked", bounded(unchecked));
    }

    private static Map<String, Map<String, Object>> compilerErrors(JsonNode receipt) {
        Map<String, Map<String, Object>> errors = new LinkedHashMap<>();
        if (receipt == null) return errors;
        for (JsonNode error : receipt.path("compile").path("errors")) {
            String severity = error.path("severity").asText("error"), message = error.path("message").asText();
            if (!"error".equals(severity)) continue;
            // Ignore source locations when identifying the same diagnostic;
            // formatting changes can move the compiler's reported line.
            errors.put(severity + ":" + message, Map.of("severity", severity, "message", clip(message)));
        }
        return errors;
    }

    private static Map<String, Object> bounded(List<Map<String, Object>> items) {
        int length = Math.min(MAX_ITEMS, items.size());
        return Map.of("count", items.size(), "items", List.copyOf(items.subList(0, length)), "omitted", items.size() - length);
    }
    private static String key(String scenario, int step) { return scenario + ":" + step; }
    private static String clip(String text) { return text.substring(0, Math.min(400, text.length())); }
}
