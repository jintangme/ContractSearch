package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ephemeral, run-owned state. Models propose arguments; only tools record receipts. */
public final class AgentWorkspace {
    public record Snapshot(long contractId, String sourceHash, boolean fullyRead) { }
    public record Draft(String sourceHash, String sourceCode) { }
    public record Compilation(String sourceHash, String sourceCode, String compilerVersion, JsonNode receipt) {
        public Compilation { receipt = receipt.deepCopy(); }
        @Override public JsonNode receipt() { return receipt.deepCopy(); }
        public boolean succeeded() { return receipt.path("success").asBoolean(false); }
    }
    public record Candidate(String id, String sourceHash, String sourceCode, String planHash, boolean passed, JsonNode receipt) {
        public Candidate { receipt = receipt.deepCopy(); }
        @Override public JsonNode receipt() { return receipt.deepCopy(); }
    }
    private final Map<Long, Snapshot> sources = new LinkedHashMap<>();
    private final Map<Long, String> sourceTexts = new LinkedHashMap<>();
    private final Map<String, Candidate> candidates = new LinkedHashMap<>();
    private final Map<String, Compilation> compilations = new LinkedHashMap<>();
    private Draft latestDraft;
    private final int candidateLimit;
    public AgentWorkspace() { this(5); }
    public AgentWorkspace(int candidateLimit) {
        if (candidateLimit < 1 || candidateLimit > 16) throw new IllegalArgumentException("Invalid bounded candidate budget");
        this.candidateLimit = candidateLimit;
    }
    private JsonNode plan;
    private Snapshot base;
    private String planHash;

    public Snapshot observeSource(long id, String source, boolean fullyRead) {
        String hash = SourceHashes.sha256(source);
        Snapshot previous = sources.get(id);
        Snapshot snapshot = new Snapshot(id, hash, fullyRead || (previous != null && previous.sourceHash().equals(hash) && previous.fullyRead()));
        sources.put(id, snapshot);
        if (fullyRead) sourceTexts.put(id, source);
        return snapshot;
    }
    public Snapshot requireReadSource(long id) {
        Snapshot snapshot = sources.get(id);
        if (snapshot == null || !snapshot.fullyRead()) throw new InvalidToolArgumentsException("Read the complete base source first");
        return snapshot;
    }
    public Snapshot requireReadSource(long id, String hash) {
        Snapshot snapshot = sources.get(id);
        if (snapshot == null || !snapshot.fullyRead() || !snapshot.sourceHash().equals(hash))
            throw new InvalidToolArgumentsException("Read the complete base source and use its current sourceHash before planning reuse");
        return snapshot;
    }
    public void installPlan(Snapshot snapshot, JsonNode newPlan) {
        if (plan != null) throw new InvalidToolArgumentsException("The reuse plan is frozen for this run; do not remove or weaken checks");
        requireReadSource(snapshot.contractId(), snapshot.sourceHash());
        base = snapshot; plan = SourceHashes.canonical(newPlan); planHash = SourceHashes.planHash(plan);
    }
    public boolean hasPlan() { return plan != null; }
    public String baseSourceText() {
        requireCurrentPlan(planHash);
        return sourceText(base);
    }
    public String sourceText(Snapshot snapshot) {
        requireReadSource(snapshot.contractId(), snapshot.sourceHash());
        return sourceTexts.get(snapshot.contractId());
    }
    public Candidate latestCandidate() {
        Candidate latest = null;
        for (Candidate candidate : candidates.values()) latest = candidate;
        return latest;
    }
    public Candidate candidateForSource(String source) {
        Candidate candidate = candidates.get("candidate:" + SourceHashes.sha256(source));
        if (candidate != null) requireCurrentPlan(candidate.planHash());
        return candidate;
    }
    public void recordDraft(String source) {
        if (source == null || source.isBlank() || source.length() > 24000)
            throw new InvalidToolArgumentsException("Draft source must contain 1 to 24000 characters");
        latestDraft = new Draft(SourceHashes.sha256(source), source);
    }
    public Draft latestDraft() { return latestDraft; }
    public Compilation compilationForSource(String source, String compilerVersion) {
        Compilation found = compilations.get(compilerVersion + ":" + SourceHashes.sha256(source));
        return found != null && found.sourceCode().equals(source) ? found : null;
    }
    public Compilation recordCompilation(String source, String compilerVersion, JsonNode receipt) {
        if (source == null || source.isBlank() || source.length() > 24000
                || !receipt.path("success").isBoolean() || !receipt.path("errors").isArray()
                || !compilerVersion.equals(receipt.path("selectedCompilerVersion").asText())
                || (receipt.path("success").asBoolean() && !receipt.path("errors").isEmpty()))
            throw new IllegalArgumentException("Compilation receipt must match actual compiler version and contain diagnostics");
        String hash = SourceHashes.sha256(source);
        Compilation compilation = new Compilation(hash, source, compilerVersion, receipt);
        // Completed receipts are immutable. Rechecking a source cannot upgrade it.
        return compilations.computeIfAbsent(compilerVersion + ":" + hash, ignored -> compilation);
    }
    public java.util.List<Map<String, Object>> compilableCandidates(String compilerVersion, String excludedHash) {
        return candidates.values().stream().filter(c -> !c.sourceHash().equals(excludedHash))
                .filter(c -> { var compile = compilationForSource(c.sourceCode(), compilerVersion);
                    return compile != null && compile.succeeded(); })
                .map(c -> Map.<String, Object>of("candidateId", c.id(), "sourceHash", c.sourceHash(),
                        "compilerVersion", compilerVersion, "compilationSucceeded", true,
                        "checksPassed", c.passed(), "validationScope", "DECLARED_VISIBLE_CHECKS"))
                .toList();
    }
    public boolean hasReadSource() { return sources.values().stream().anyMatch(Snapshot::fullyRead); }
    public boolean hasPassingCandidate() { return candidates.values().stream().anyMatch(Candidate::passed); }
    public boolean isCurrentSource(long id, String hash) { return sources.containsKey(id) && sources.get(id).sourceHash().equals(hash); }
    public String planHash() { return planHash; }
    public JsonNode plan() {
        if (plan == null) throw new InvalidToolArgumentsException("Declare a reuse plan before validating a candidate");
        return plan.deepCopy();
    }
    public void requireCurrentPlan(String requestedHash) {
        if (!hasPlan() || !planHash.equals(requestedHash)) throw new InvalidToolArgumentsException("Unknown or stale reuse plan hash");
        requireReadSource(base.contractId(), base.sourceHash());
    }
    public boolean canValidateCandidate() { return hasPlan() && candidates.size() < candidateLimit; }
    public void requireNewCandidate(String source) {
        if (!canValidateCandidate() || candidates.containsKey("candidate:" + SourceHashes.sha256(source)))
            throw new InvalidToolArgumentsException("Candidate was already checked or candidate budget exhausted");
    }
    public Candidate recordCandidate(String source, JsonNode result) {
        requireCurrentPlan(planHash);
        String hash = SourceHashes.sha256(source);
        if (!hash.equals(result.path("sourceHash").asText()) || !planHash.equals(result.path("planHash").asText()))
            throw new IllegalArgumentException("Candidate receipt does not match the source and frozen plan");
        String id = "candidate:" + hash;
        if (candidates.containsKey(id) || candidates.size() >= candidateLimit) throw new InvalidToolArgumentsException("Candidate check budget exhausted");
        // Independently require every declared scenario, including its planned
        // number of steps, rather than trusting a bare allChecksPassed flag.
        Map<String, JsonNode> rows = new LinkedHashMap<>();
        for (JsonNode row : result.path("scenarios")) {
            if (rows.put(row.path("id").asText(), row) != null) throw new IllegalArgumentException("Duplicate scenario receipt");
        }
        boolean passed = result.path("compile").path("success").asBoolean(false)
                && result.path("allChecksPassed").asBoolean(false) && rows.size() == plan.path("scenarios").size();
        for (JsonNode scenario : plan.path("scenarios")) {
            JsonNode row = rows.get(scenario.path("id").asText());
            passed &= row != null && row.path("passed").asBoolean(false)
                    && row.path("requirementId").asText().equals(scenario.path("requirementId").asText())
                    && row.path("steps").isArray() && row.path("steps").size() == scenario.path("steps").size();
            if (row != null) {
                int index = 0;
                for (JsonNode step : row.path("steps")) {
                    passed &= step.path("passed").asBoolean(false) && step.path("index").isIntegralNumber()
                            && step.path("index").asInt(-1) == index++;
                }
            }
        }
        Candidate candidate = new Candidate(id, hash, source, planHash, passed, result);
        candidates.put(id, candidate);
        JsonNode compile = result.path("compile");
        String version = compile.path("selectedCompilerVersion").asText("");
        if (!version.isBlank() && compile.path("success").isBoolean() && compile.path("errors").isArray()
                && (!compile.path("success").asBoolean() || compile.path("errors").isEmpty()))
            recordCompilation(source, version, compile);
        return candidate;
    }
    public Candidate requireCandidate(String id) {
        requireCurrentPlan(planHash);
        Candidate candidate = candidates.get(id);
        if (candidate == null) throw new InvalidToolArgumentsException("Select an existing candidate with its actual validation receipt");
        return candidate;
    }
    public Candidate requirePassingCandidate(String id) {
        Candidate candidate = requireCandidate(id);
        if (!candidate.passed()) throw new InvalidToolArgumentsException("Candidate did not pass every declared check");
        return candidate;
    }
    public Map<String, Object> summary() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("readSources", sources.values().stream().filter(Snapshot::fullyRead).toList());
        state.put("reusePlanDeclared", hasPlan());
        state.put("compilations", compilations.values().stream().map(c -> Map.of(
                "sourceHash", c.sourceHash(), "compilerVersion", c.compilerVersion(),
                "compilationSucceeded", c.succeeded(), "errors", c.receipt().path("errors"),
                "validationScope", "COMPILATION_ONLY")).toList());
        if (latestDraft != null) state.put("latestDraftSourceHash", latestDraft.sourceHash());
        state.put("phase", !hasPlan() ? "EVIDENCE_AVAILABLE" : hasPassingCandidate() ? "CHECKED_CANDIDATE_AVAILABLE" : candidates.isEmpty() ? "BUILD_AND_CHECK" : canValidateCandidate() ? "CHECK_DISCREPANCY" : "REPORT_UNRESOLVED");
        if (!hasPlan()) return state;
        state.put("candidateProgress", CandidateProgress.summary(java.util.List.copyOf(candidates.values())));
        state.put("planHash", planHash);
        state.put("requirements", plan.path("requirements").deepCopy());
        state.put("unexecutedAnalysisObligations", plan.path("analysisObligations").deepCopy());
        state.put("candidateLimit", candidateLimit);
        state.put("validationScope", "DECLARED_VISIBLE_CHECKS");
        // Full receipts remain in tool observations. Carry compact, actionable
        // failures here instead of repeating every passing step each model turn.
        state.put("candidates", candidates.values().stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("candidateId", c.id()); item.put("sourceHash", c.sourceHash()); item.put("checksPassed", c.passed());
            item.put("status", c.receipt().path("status").asText());
            item.put("requirements", c.receipt().path("requirements"));
            java.util.List<JsonNode> failures = new java.util.ArrayList<>();
            for (JsonNode scenario : c.receipt().path("scenarios")) if (!scenario.path("passed").asBoolean()) failures.add(scenario);
            item.put("counterexamples", failures);
            item.put("compileErrors", c.receipt().path("compile").path("errors"));
            item.put("boundaryProbes", c.receipt().path("boundaryProbes"));
            return item;
        }).toList());
        return state;
    }
}
