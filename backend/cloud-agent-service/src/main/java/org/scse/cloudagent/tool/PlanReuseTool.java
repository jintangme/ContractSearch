package org.scse.cloudagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.scse.cloudagent.domain.SideEffect;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class PlanReuseTool implements AgentTool {
    private final CandidateRuntimeClient runtime;
    public PlanReuseTool(CandidateRuntimeClient runtime) { this.runtime = runtime; }
    @Override public ToolDefinition definition() {
        return new ToolDefinition("plan_reuse.v1", "Freeze EXECUTABLE requirements and visible ABI behavior scenarios before executing a candidate derived from a fully read catalog source. Prefer omitting baseSourceHash: the server pins the current fully read source version; if supplied, the hash must match. Prefer supplying optional candidateSource: propose complete code and its task-derived checks together; the server checks ABI compatibility, freezes the plan before any behavioral test, then validates this first candidate in the same tool call. This saves a model round; it does not bypass tests or the remaining tool/model budgets. If candidateSource is omitted, a separate validate_candidate call is required. Put non-executable explanation requests, callback limitations and proof caveats in optional analysisObligations (up to 8 strings), NOT requirements. Analysis obligations are never executed or counted as passed; address them separately from source evidence in the final answer. Executable target and preservation behaviors must still have scenarios. Each scenario starts with a fresh deployment by account 0. Optional scenario.constructorArgs overrides plan.constructorArgs for that scenario only; omit it to inherit the plan default. An explicit [] means a no-argument constructor. The same override applies to unchanged setup-prefix preflight and candidate execution. Use overrides only when useful to check distinct legal deployment states, such as ordinary and boundary values; they do not require extra scenarios. ABI args/expected values may use $account0, $account1, $account2 and $contract. All three accounts are distinct NONZERO funded signers; $account0 is the deployer, NEVER address(0). To test address(0), use literal 0x0000000000000000000000000000000000000000. Choose the smallest meaningful set of task-derived checks; one scenario is allowed. There is no required scenario or step count beyond at least one executable check. Hard limit: 12 scenarios, 10 steps per scenario and 64 total steps. Use the compact schema: each scenario id and optional description become a visible check group. Do not provide redundant requirements or requirementId fields. Optional setupSteps counts a leading prefix of unchanged setup operations per scenario. The server executes those prefixes on the original source BEFORE freezing and rejects impossible fixtures. Mark pre-existing state preparation (deposit/reserve/grant) this way, but never a call whose behavior the task asks to change. Verify constructor limits against these fixtures. Before freezing, count steps and check that each success/revert expectation follows the requested behavior and earlier steps. Do not invent a rejection rule for a valid user-required behavior. Integer values above 2^53-1 must be decimal strings; integer ABI values accept type-aware $typeMax/$typeMin, explicit $uint256max, $int256max and $int256min. Never use $uint256max for an int256 parameter. For exact integer inputs/expectations, use {integer: expression} or an arithmetic expression string such as $int256max - 1 instead of hand-calculating large decimals. Arithmetic strings are evaluated only for integer ABI leaves and checked against their signed width; string ABI fields retain literal text, while address/bool/bytes fields reject arithmetic. Plain decimal/hex literals and existing standalone aliases retain their meaning. Expressions support decimal and 0x hexadecimal numbers, integer constants above, parentheses, + - * / // % ** << >> & | ^; / truncates toward zero and // floors. Example: {\"integer\":\"($int256min + 1) / 2\"}. They support at most 256 characters, 96 tokens, 2048-bit intermediate values and shifts/exponents 0..512. They perform arithmetic only; no code, function calls or access to hidden test answers. Pure/view methods always require kind=read and expected, including array returns. balanceChanges measures ETH only, never ERC20 balances; assert token balances by read balanceOf. Optional expectedReturn on successful write steps checks ABI return data via pre-transaction simulation; actual state and events are checked separately. Use write steps for non-view functions, even when they return values. Optional write events assert the exact ordered own-contract event sequence (name and args), including zero events with []. Optional balanceChanges [{account:$account0/1/2 or $contract,deltaWei:signed decimal string}] checks actual ETH deltas for non-sender accounts (sender gas excluded by rejecting sender assertions). Optional revertReason checks the exact Error(string) reason; omit it unless that exact text is part of the requested or preserved API, otherwise expect=revert is sufficient. Plans cannot be weakened later; checks are agent-defined, not independent ground truth.", """
                {"type":"object","additionalProperties":false,"required":["baseContractId","plan"],"properties":{
                  "baseContractId":{"type":"integer","minimum":1},"baseSourceHash":{"type":"string"},"candidateSource":{"type":"string","minLength":1,"maxLength":24000},
                  "plan":{"type":"object","additionalProperties":false,"required":["contractName","constructorArgs","scenarios"],"properties":{
                    "analysisObligations":{"type":"array","maxItems":8,"items":{"type":"string","maxLength":500}},
                    "contractName":{"type":"string"},"constructorArgs":{"type":"array","items":{}},
                    "scenarios":{"type":"array","minItems":1,"maxItems":12,"items":{"type":"object","required":["id","steps"],"properties":{
                      "id":{"type":"string","minLength":1,"maxLength":96,"pattern":"^[A-Za-z0-9_-]+$"},"description":{"type":"string","maxLength":500},"constructorArgs":{"type":"array","items":{}},"setupSteps":{"type":"integer","minimum":0,"maximum":10},"steps":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"object","required":["kind","method","args","from","expect"],"properties":{
                        "kind":{"type":"string","enum":["read","write"]},"method":{"type":"string"},"args":{"type":"array","items":{}},"from":{"type":"integer","minimum":0,"maximum":2},
                        "expect":{"type":"string","enum":["equal","revert","success"]},"expected":{},"expectedReturn":{},"valueWei":{"type":"string"},"revertReason":{"type":"string"},"balanceChanges":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"object","additionalProperties":false,"required":["account","deltaWei"],"properties":{"account":{"type":"string"},"deltaWei":{"type":"string"}}}},"events":{"type":"array","maxItems":8,"items":{"type":"object","additionalProperties":false,"required":["name","args"],"properties":{"name":{"type":"string"},"args":{"type":"array","items":{}}}}}
                      }}}}}}
                  }}
                }}
                """, SideEffect.EXECUTE_SANDBOXED, Duration.ofSeconds(65));
    }
    @Override public ToolExecutionResult execute(JsonNode args, ToolExecutionContext context) {
        ToolArgumentRules.rejectUnknown(args, "baseContractId", "baseSourceHash", "plan", "candidateSource");
        long id = ToolArgumentRules.requiredPositiveLong(args, "baseContractId");
        AgentWorkspace workspace = context.workspace();
        if (workspace.hasPlan()) throw new InvalidToolArgumentsException("Reuse plan is already frozen");
        AgentWorkspace.Snapshot base = args.has("baseSourceHash") ? workspace.requireReadSource(id, args.path("baseSourceHash").asText()) : workspace.requireReadSource(id);
        String candidateSource = args.has("candidateSource") ? args.path("candidateSource").asText() : null;
        if (candidateSource != null && (candidateSource.isBlank() || candidateSource.length() > 24000)) throw new InvalidToolArgumentsException("Candidate source must contain 1 to 24000 characters");
        // A generated artifact exists before preflight/execution. Preserve it
        // separately from receipts so transport failures cannot erase it or
        // accidentally promote it to tested code.
        if (candidateSource != null) workspace.recordDraft(candidateSource);
        JsonNode plan = args.path("plan").deepCopy();
        if (!plan.isObject() || plan.toString().length() > 24000) throw new InvalidToolArgumentsException("Invalid bounded reuse plan");
        JsonNode proposedScenarios = plan.path("scenarios");
        if (!proposedScenarios.isArray() || proposedScenarios.isEmpty() || proposedScenarios.size() > 12)
            throw new InvalidToolArgumentsException("plan.scenarios must be an array of 1 to 12 scenarios; received "
                    + (proposedScenarios.isArray() ? proposedScenarios.size() : "a non-array")
                    + ". Group related assertions within scenarios; do not add a requirements field to fix this limit.");
        if (!plan.has("requirements")) {
            ObjectNode normalized = plan.deepCopy();
            ArrayNode derived = normalized.putArray("requirements");
            for (JsonNode row : normalized.path("scenarios")) {
                if (!(row instanceof ObjectNode scenario)) throw new InvalidToolArgumentsException("Scenario must be an object");
                String key = scenario.path("id").asText();
                String description = scenario.path("description").asText(key);
                derived.addObject().put("id", key).put("description", description.isBlank() ? key : description);
                scenario.put("requirementId", key); scenario.remove("description");
            }
            plan = normalized;
        }
        for (JsonNode row : plan.path("scenarios")) if (row instanceof ObjectNode scenario) scenario.remove("description");
        JsonNode checked = runtime.validatePlan(plan, candidateSource == null ? workspace.sourceText(base) : candidateSource, candidateSource != null);
        if (!SourceHashes.planHash(plan).equals(checked.path("planHash").asText())) throw new InvalidToolArgumentsException("Runtime plan hash mismatch");
        // Only explicitly marked unchanged setup prefixes run against the base.
        // Fail before freezing so impossible fixtures cannot force wrong repairs.
        ObjectNode fixture = plan.deepCopy();
        ArrayNode scenarios = fixture.putArray("scenarios");
        java.util.Set<String> fixtureRequirements = new java.util.HashSet<>();
        for (JsonNode scenario : plan.path("scenarios")) {
            int count = scenario.path("setupSteps").asInt(0);
            if (count == 0) continue;
            ObjectNode prefix = scenario.deepCopy();
            prefix.remove("setupSteps");
            ArrayNode steps = prefix.putArray("steps");
            for (int i = 0; i < count; i++) steps.add(scenario.path("steps").get(i));
            scenarios.add(prefix); fixtureRequirements.add(scenario.path("requirementId").asText());
        }
        if (!scenarios.isEmpty()) {
            ArrayNode requirements = fixture.putArray("requirements");
            for (JsonNode requirement : plan.path("requirements"))
                if (fixtureRequirements.contains(requirement.path("id").asText())) requirements.add(requirement);
            JsonNode preview = runtime.validateCandidate(workspace.sourceText(base), fixture);
            if (!preview.path("allChecksPassed").asBoolean(false))
                throw new InvalidToolArgumentsException("FIXTURE_REJECTED before plan freeze. Correct the constructor/setup against unchanged source; do not alter requested behavior. Receipt: " + preview.toString());
        }
        workspace.installPlan(base, plan);
        Map<String, Object> output = new java.util.LinkedHashMap<>(Map.of("planHash", workspace.planHash(), "requirements", plan.path("requirements"), "status", "PLAN_FROZEN", "validationScope", "DECLARED_VISIBLE_CHECKS", "abiPreflight", checked.path("abiPreflight")));
        if (candidateSource != null) {
            ObjectNode validationArgs = ((ObjectNode) args).objectNode().put("sourceCode", candidateSource).put("planHash", workspace.planHash());
            ToolExecutionResult validated = new ValidateCandidateTool(runtime).execute(validationArgs, context);
            output.putAll(validated.output()); output.put("status", "PLAN_FROZEN_AND_CANDIDATE_CHECKED");
            return new ToolExecutionResult(output, validated.evidence());
        }
        return new ToolExecutionResult(output, List.of());
    }
}
