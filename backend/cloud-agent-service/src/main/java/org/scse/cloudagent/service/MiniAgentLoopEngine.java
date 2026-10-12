package org.scse.cloudagent.service;

import org.scse.cloudagent.tool.SourceDependencies;
import org.scse.cloudagent.tool.SourceHashes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.AgentErrorCode;
import org.scse.cloudagent.domain.AgentEvent;
import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.domain.AgentSignal;
import org.scse.cloudagent.domain.EvidenceItem;
import org.scse.cloudagent.state.AgentStateMachine;
import org.scse.cloudagent.tool.AgentTool;
import org.scse.cloudagent.tool.AgentWorkspace;
import org.scse.cloudagent.tool.AgentToolException;
import org.scse.cloudagent.tool.EvidenceDraft;
import org.scse.cloudagent.tool.InvalidToolArgumentsException;
import org.scse.cloudagent.tool.MiniLoopToolPolicy;
import org.scse.cloudagent.tool.ModelCallBudget;
import org.scse.cloudagent.tool.CompileSourceTool;
import org.scse.cloudagent.tool.ToolExecutionContext;
import org.scse.cloudagent.tool.ToolExecutionResult;
import org.scse.cloudagent.tool.ToolDefinition;
import org.scse.cloudagent.tool.ToolInvocationValidator;
import org.scse.cloudagent.tool.ToolRegistry;
import org.scse.cloudagent.tool.UnknownToolException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class MiniAgentLoopEngine {
    private final AgentStateMachine stateMachine;
    private final ToolRegistry toolRegistry;
    private final ToolInvocationValidator validator;
    private final MiniLoopToolPolicy policy;
    private final StructuredActionProvider provider;
    private final UntrustedContentBoundary contentBoundary;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public MiniAgentLoopEngine(AgentStateMachine stateMachine,
                               ToolRegistry toolRegistry,
                               ToolInvocationValidator validator,
                               MiniLoopToolPolicy policy,
                               StructuredActionProvider provider,
                               UntrustedContentBoundary contentBoundary,
                               ObjectMapper objectMapper,
                               AgentProperties properties) {
        this.stateMachine = stateMachine;
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.policy = policy;
        this.provider = provider;
        this.contentBoundary = contentBoundary;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public MiniLoopResult execute(UUID runId, long userId, String task) {
        return execute(runId, userId, task, Map.of(), event -> { });
    }

    public MiniLoopResult execute(UUID runId, long userId, String task, Map<String, Object> workspaceContext) {
        return execute(runId, userId, task, workspaceContext, event -> { });
    }

    public MiniLoopResult execute(UUID runId,
                                  long userId,
                                  String task,
                                  Map<String, Object> workspaceContext,
                                  Consumer<AgentEvent> eventSink) {
        if (workspaceContext == null) workspaceContext = Map.of();
        Context context = new Context(eventSink, Math.max(1, Math.min(properties.miniLoop().maxToolCalls(), properties.miniLoop().maxSteps() - 1)));
        context.task = task;
        context.runId = runId; context.userId = userId;
        if (workspaceContext.get("compilerVersion") instanceof String version && version.length() <= 40
                && version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
            context.compilerVersion = version;
        if (workspaceContext.get("remainingTimeMillis") instanceof Number millis)
            context.deadlineNanos = System.nanoTime() + Math.max(0, Math.min(360_000, millis.longValue())) * 1_000_000;
        context.modifiedSourceRequested = TaskIntent.requiresModifiedSource(task);
        move(context, AgentSignal.START_PLAN, "run.plan", Map.of("mode", "MINI_AGENTIC_RAG"));
        try {
            hydrateSelectedContract(context, runId, userId, workspaceContext);
            while (context.steps < properties.miniLoop().maxSteps() && context.modelCalls < properties.miniLoop().maxSteps()) {
                context.steps++;
                int remainingToolCalls = properties.miniLoop().maxToolCalls() - context.toolCalls;
                int remainingRetrievalRounds = properties.miniLoop().maxRetrievalRounds() - context.retrievalRounds;
                Map<String, Object> verificationState = new LinkedHashMap<>(context.workspace.summary());
                verificationState.put("modifiedSourceRequested", context.modifiedSourceRequested);
                verificationState.put("validationPolicy", "AGENT_SELECTED_WITH_RECEIPT_BOUND_CLAIMS");
                if (!context.lastRejectedAction.isEmpty()) verificationState.put("lastRejectedAction", context.lastRejectedAction);
                AgentDecision decision;
                try { decision = provider.decide(new AgentDecisionRequest(
                        task,
                        workspaceContext,
                        context.observations,
                        context.evidence,
                        availableTools(context, task, workspaceContext, remainingToolCalls, remainingRetrievalRounds),
                        context.steps,
                        remainingToolCalls,
                        remainingRetrievalRounds,
                        verificationState,
                        properties.miniLoop().maxSteps() - context.modelCalls
                )); }
                catch (RecoverableDecisionException exception) {
                    context.modelCalls += exception.providerCalls();
                    context.observations.add(contentBoundary.wrap("decision_guard", toJson(Map.of(
                            "status", "INVALID_MODEL_ACTION", "message", exception.getMessage(),
                            "rejectedMessage", exception.rejectedMessage(), "executed", false))));
                    addEvent(context, "model.rejected", Map.of("message", exception.getMessage(),
                            "providerCalls", exception.providerCalls(), "modelCallsUsed", context.modelCalls));
                    continue;
                }
                catch (ProviderRequestFailure exception) {
                    context.modelCalls += exception.providerCalls();
                    addEvent(context, "model.failed", Map.of("providerCalls", exception.providerCalls(), "modelCallsUsed", context.modelCalls));
                    throw exception;
                }
                int chargedCalls = decision.providerMetadata().get("providerCalls") instanceof Number calls ? Math.max(1, calls.intValue()) : 1;
                context.modelCalls += chargedCalls;
                Map<String, Object> modelAction = new LinkedHashMap<>();
                modelAction.put("step", context.steps);
                modelAction.put("actionType", decision.type().name());
                modelAction.put("reasonSummary", decision.reasonSummary());
                modelAction.put("evidenceGap", decision.evidenceGap());
                modelAction.put("citedEvidenceIds", decision.evidenceIds());
                modelAction.put("stopReason", decision.stopReason());
                modelAction.put("provider", decision.providerMetadata());
                modelAction.put("modelCallsUsed", context.modelCalls);
                addEvent(context, "model.action", Map.copyOf(modelAction));
                if (decision.type() == AgentDecision.Type.FINAL_ANSWER
                        || decision.type() == AgentDecision.Type.ABSTAIN) {
                    try { return complete(context, decision); }
                    catch (InvalidToolArgumentsException exception) {
                        context.observations.add(contentBoundary.wrap("validation_guard", toJson(Map.of("status", "VALIDATION_GAP", "message", exception.getMessage()))));
                        addEvent(context, "answer.validation_gap", Map.of("message", exception.getMessage()));
                        continue;
                    }
                }
                executeTool(context, runId, userId, decision);
            }
            if (context.workspace.latestCandidate() != null || context.workspace.latestDraft() != null)
                return unresolvedArtifact(context, "Decision budget exhausted before a valid final response");
            return fail(context, AgentErrorCode.BUDGET_EXCEEDED, "Maximum Agent steps exceeded");
        } catch (AgentToolException exception) {
            if ((context.workspace.latestCandidate() != null || context.workspace.latestDraft() != null)
                    && Set.of(AgentErrorCode.PROVIDER_ERROR, AgentErrorCode.INTERNAL_ERROR,
                              AgentErrorCode.TOOL_TIMEOUT, AgentErrorCode.BUDGET_EXCEEDED, AgentErrorCode.LOOP_DETECTED).contains(exception.errorCode()))
                return unresolvedArtifact(context, exception.errorCode() + ": " + exception.getMessage());
            return fail(context, exception.errorCode(), exception.getMessage());
        } catch (UnknownToolException exception) {
            return fail(context, AgentErrorCode.UNKNOWN_TOOL, exception.getMessage());
        } catch (InvalidToolArgumentsException exception) {
            return fail(context, AgentErrorCode.INVALID_TOOL_ARGUMENTS, exception.getMessage());
        } catch (RuntimeException exception) {
            return fail(context, AgentErrorCode.INTERNAL_ERROR, "Agent loop failed: " + exception.getClass().getSimpleName());
        }
    }

    private MiniLoopResult unresolvedArtifact(Context context, String reason) {
        // A transport/budget fallback preserves an artifact; it must not start
        // another operation or resume repair after the terminating condition.
        context.suppressPreflight = true;
        if (context.state == AgentRunState.TOOL_CALL) {
            move(context, AgentSignal.RECORD_OBSERVATION, "tool.interrupted",
                    Map.of("message", reason, "completedReceipt", false));
        }
        var latest = context.workspace.latestCandidate();
        List<UUID> citations = context.evidence.stream()
                .filter(e -> latest != null ? latest.id().equals(e.metadata().get("candidateId"))
                        : e.sourceType().equals("CONTRACT_SOURCE")).map(EvidenceItem::id).toList();
        addEvent(context, "candidate.delivery_fallback", Map.of("reason", reason,
                "candidateId", latest == null ? "UNEXECUTED_DRAFT" : latest.id()));
        return complete(context, AgentDecision.abstain(
                "Verification remains unresolved: " + reason + ". The latest retained source is included below with its actual execution status; inspect the recorded checks and outstanding requirements before use.",
                citations, "Retain checked artifact without claiming task completion", reason, "EVIDENCE_INSUFFICIENT"));
    }

    private List<ToolDefinition> availableTools(Context context,
                                                String task,
                                                Map<String, Object> workspaceContext,
                                                int remainingToolCalls,
                                                int remainingRetrievalRounds) {
        // Reserve the last decision for synthesis, even if earlier actions used
        // fewer tools than the total tool budget.
        if (remainingToolCalls <= 0 || context.steps >= properties.miniLoop().maxSteps()
                || context.modelCalls >= properties.miniLoop().maxSteps() - 1) return List.of();
        // Budgets and tool prerequisites bound the agent; they do not prescribe
        // a workflow. Even after a test failure, a targeted source lookup may
        // resolve the gap more usefully than another blind candidate rewrite.
        return toolRegistry.availableDefinitions(MiniLoopToolPolicy.ALLOWED_TOOLS).stream()
                .filter(tool -> !"plan_reuse.v1".equals(tool.name()) || (context.workspace.hasReadSource() && !context.workspace.hasPlan()
                        && remainingToolCalls >= 1 && properties.miniLoop().maxSteps() - context.modelCalls >= 2))
                .filter(tool -> !"validate_candidate.v1".equals(tool.name()) || context.workspace.hasPlan())
                .filter(tool -> !"review_candidate.v1".equals(tool.name()) || (context.workspace.latestCandidate() != null
                        && properties.miniLoop().maxSteps() - context.modelCalls >= 3))
                .filter(tool -> remainingRetrievalRounds > 0
                        || (!("search_contracts.v1".equals(tool.name()))
                        && !("retrieve_knowledge.v1".equals(tool.name()))))
                .toList();
    }

    private void hydrateSelectedContract(Context context, UUID runId, long userId,
                                         Map<String, Object> workspaceContext) {
        if (workspaceContext == null || !workspaceContext.containsKey("selectedContractId")) return;
        JsonNode id = objectMapper.valueToTree(workspaceContext.get("selectedContractId"));
        if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) {
            throw new InvalidToolArgumentsException("selectedContractId must be a positive integer");
        }
        addEvent(context, "context.hydrating", Map.of(
                "contractId", id.longValue(), "tool", "get_contract.v1"));
        // This is a real, authorized catalog read through the same tool boundary.
        // It consumes the tool budget and registers normal evidence and audit events.
        executeTool(context, runId, userId, AgentDecision.toolCall("get_contract.v1",
                toJson(Map.of("contractId", id.longValue())), "Load the selected contract source"));
    }

    private void executeTool(Context context, UUID runId, long userId, AgentDecision decision) {
        if (context.toolCalls >= properties.miniLoop().maxToolCalls()) {
            throw new AgentToolException(AgentErrorCode.BUDGET_EXCEEDED, "Maximum tool calls exceeded");
        }
        AgentTool tool = toolRegistry.require(decision.toolName());
        policy.authorize(tool);
        boolean retrieval = "search_contracts.v1".equals(tool.definition().name())
                || "retrieve_knowledge.v1".equals(tool.definition().name());
        if (retrieval && context.retrievalRounds >= properties.miniLoop().maxRetrievalRounds()) {
            throw new AgentToolException(AgentErrorCode.BUDGET_EXCEEDED, "Maximum retrieval rounds exceeded");
        }
        JsonNode arguments;
        try { arguments = validator.parseObject(decision.argumentsJson()); }
        catch (InvalidToolArgumentsException exception) {
            // No tool has run. Preserve bounded, untrusted syntax feedback and
            // let the agent choose another action within its remaining budget.
            String raw = decision.argumentsJson() == null ? "" : decision.argumentsJson();
            context.lastRejectedAction = Map.of("tool", tool.definition().name(),
                    "rawArguments", raw.substring(0, Math.min(8000, raw.length())),
                    "message", exception.getMessage(), "scope", "UNTRUSTED_PROPOSAL_AND_TOOL_DIAGNOSTIC");
            context.observations.add(contentBoundary.wrap(tool.definition().name(), toJson(Map.of(
                    "status", "ARGUMENT_JSON_REJECTED", "executed", false, "message", exception.getMessage()))));
            addEvent(context, "tool.rejected", Map.of("tool", tool.definition().name(),
                    "phase", "ARGUMENT_DECODING", "executed", false, "message", exception.getMessage()));
            return;
        }
        String invocationKey = tool.definition().name() + ":" + SourceHashes.canonical(arguments).toString();
        // Candidate validation owns exact source/plan receipt reuse and stale
        // prerequisite checks. Repeated requests still consume this tool budget.
        if (!context.invocations.add(invocationKey) && !Set.of("validate_candidate.v1", "compile_source.v1").contains(tool.definition().name())) {
            if (!context.evidence.isEmpty() && !context.duplicateRecovered) {
                context.duplicateRecovered = true;
                context.observations.add(contentBoundary.wrap("loop_guard", toJson(Map.of(
                        "status", "DUPLICATE_SKIPPED",
                        "tool", tool.definition().name(),
                        "instruction", "Use the existing observation and evidence. Choose a different useful tool or answer now."
                ))));
                addEvent(context, "tool.skipped", Map.of(
                        "tool", tool.definition().name(),
                        "reason", "DUPLICATE_INVOCATION",
                        "recovery", "REUSE_EXISTING_EVIDENCE"
                ));
                return;
            }
            throw new AgentToolException(AgentErrorCode.LOOP_DETECTED,
                    "Repeated identical tool invocation was blocked: " + tool.definition().name());
        }
        UUID toolCallId = UUID.randomUUID();
        AgentSignal signal = context.state == AgentRunState.PLAN ? AgentSignal.REQUEST_TOOL : AgentSignal.RETRY_TOOL;
        move(context, signal, "tool.requested", Map.of(
                "toolCallId", toolCallId,
                "tool", tool.definition().name(),
                "origin", context.automaticCompilation ? "FINAL_SOURCE_PREFLIGHT" : "AGENT_SELECTED",
                "schemaVersion", tool.definition().name().substring(tool.definition().name().lastIndexOf('.') + 1),
                "sideEffect", tool.definition().sideEffect().name(),
                "arguments", arguments
        ));
        context.toolCalls++;
        if (retrieval) context.retrievalRounds++;
        ToolExecutionResult result;
        ModelCallBudget toolModelBudget = new ModelCallBudget(Math.max(0, properties.miniLoop().maxSteps() - context.modelCalls - 1));
        try { result = tool.execute(arguments, new ToolExecutionContext(userId, runId, context.workspace, context.task,
                toolModelBudget, context.remainingMillis())); }
        catch (InvalidToolArgumentsException exception) {
            context.lastRejectedAction = Map.of("tool", tool.definition().name(), "arguments", arguments.deepCopy(),
                    "message", exception.getMessage(), "scope", "UNTRUSTED_PROPOSAL_AND_TOOL_DIAGNOSTIC");
            context.observations.add(contentBoundary.wrap(tool.definition().name(), toJson(Map.of("status", "INVALID_ARGUMENTS", "message", exception.getMessage()))));
            move(context, AgentSignal.RECORD_OBSERVATION, "tool.rejected", Map.of("tool", tool.definition().name(), "message", exception.getMessage()));
            return;
        }
        finally {
            context.modelCalls += toolModelBudget.used();
            if (toolModelBudget.used() > 0) addEvent(context, "tool.model_calls", Map.of(
                    "tool", tool.definition().name(), "providerCalls", toolModelBudget.used(), "modelCallsUsed", context.modelCalls));
        }
        if (tool.definition().name().equals(context.lastRejectedAction.get("tool"))
                || "validate_candidate.v1".equals(tool.definition().name())) context.lastRejectedAction = Map.of();
        List<UUID> evidenceIds = addEvidence(context, result.evidence(), toolCallId);
        Map<String, Object> observation = new LinkedHashMap<>(result.output());
        observation.put("toolCallId", toolCallId.toString());
        observation.put("evidenceIds", evidenceIds.stream().map(UUID::toString).toList());
        observation.put("origin", context.automaticCompilation ? "FINAL_SOURCE_PREFLIGHT" : "AGENT_SELECTED");
        context.observations.add(contentBoundary.wrap(tool.definition().name(), toJson(observation)));
        Map<String, Object> completion = new LinkedHashMap<>(Map.of(
                "toolCallId", toolCallId,
                "tool", tool.definition().name(),
                "origin", context.automaticCompilation ? "FINAL_SOURCE_PREFLIGHT" : "AGENT_SELECTED",
                "evidenceIds", evidenceIds,
                "outputKeys", new ArrayList<>(result.output().keySet())
        ));
        for (String key : List.of("executionPerformed", "receiptReused", "compilationPerformed", "behaviorChecksPerformed")) {
            if (result.output().get(key) instanceof Boolean value) completion.put(key, value);
        }
        move(context, AgentSignal.RECORD_OBSERVATION, "tool.completed", completion);
        context.lastToolOutput = result.output();
        context.toolOutputs.put(invocationKey, result.output());
        if ("get_contract.v1".equals(tool.definition().name()) && !context.expandingDependencies) {
            expandDependencies(context, runId, userId, result.output());
        }
    }

    private void expandDependencies(Context context, UUID runId, long userId, Map<String, Object> root) {
        if (toolRegistry.availableDefinitions(MiniLoopToolPolicy.ALLOWED_TOOLS).stream().noneMatch(t -> t.name().equals("search_contracts.v1"))) return;
        var pending = new java.util.ArrayDeque<Map<String, Object>>(); pending.add(root);
        context.expandingDependencies = true;
        try {
            while (!pending.isEmpty()) {
                Map<String, Object> source = pending.removeFirst();
                String code = String.valueOf(source.getOrDefault("sourceCode", ""));
                for (var ref : SourceDependencies.imports(code)) {
                    if (!context.dependencyPaths.add(ref.path())) continue;
                    // Preserve capacity for optional draft/check and repair.
                    int reserve = context.modifiedSourceRequested ? 2 : 0;
                    if (properties.miniLoop().maxToolCalls() - context.toolCalls < 2 + reserve
                            || context.retrievalRounds >= properties.miniLoop().maxRetrievalRounds()) return;
                    String queryArgs = SourceHashes.canonical(objectMapper.valueToTree(Map.of("query", ref.symbol(), "mode", "code", "limit", 10))).toString();
                    String queryKey = "search_contracts.v1:" + queryArgs;
                    if (!context.toolOutputs.containsKey(queryKey)) executeTool(context, runId, userId,
                            AgentDecision.toolCall("search_contracts.v1", queryArgs, "Resolve imported source candidate"));
                    JsonNode matches = objectMapper.valueToTree(context.toolOutputs.getOrDefault(queryKey, Map.of())).path("results");
                    java.util.List<JsonNode> exact = new java.util.ArrayList<>();
                    for (JsonNode row : matches) if (ref.symbol().equals(row.path("contractName").asText()) && row.path("id").isIntegralNumber() && row.path("id").canConvertToLong() && row.path("id").asLong() > 0) exact.add(row);
                    Map<String, Object> link = new LinkedHashMap<>();
                    link.put("parentSourceHash", source.getOrDefault("sourceHash", "")); link.put("importPath", ref.path());
                    link.put("scope", "CATALOG_TEMPLATE_CANDIDATE_NOT_DEPLOYED_IDENTITY");
                    link.put("status", exact.size() == 1 ? "ONE_RETRIEVED_NAME_CANDIDATE" : exact.isEmpty() ? "NOT_FOUND" : "AMBIGUOUS");
                    if (exact.size() == 1) {
                        long id = exact.get(0).path("id").asLong(); link.put("contractId", id);
                        String key = "get_contract.v1:" + toJson(Map.of("contractId", id));
                        if (!context.invocations.contains(key)) {
                            executeTool(context, runId, userId, AgentDecision.toolCall("get_contract.v1", toJson(Map.of("contractId", id)), "Read imported source candidate"));
                            pending.add(context.lastToolOutput);
                        }
                    }
                    context.observations.add(contentBoundary.wrap("dependency_resolution", toJson(link)));
                    addEvent(context, "dependency.resolved", link);
                }
            }
        } finally { context.expandingDependencies = false; }
    }

    private MiniLoopResult complete(Context context, AgentDecision decision) {
        Set<UUID> available = new LinkedHashSet<>();
        context.evidence.forEach(item -> available.add(item.id()));
        boolean abstained = decision.type() == AgentDecision.Type.ABSTAIN;
        if (!abstained && decision.evidenceIds().isEmpty()) {
            throw new AgentToolException(AgentErrorCode.PROVIDER_ERROR,
                    "Final answer must cite at least one evidence ID");
        }
        if (!available.containsAll(decision.evidenceIds())) {
            throw new AgentToolException(AgentErrorCode.PROVIDER_ERROR,
                    "Final answer cited an unknown evidence ID");
        }
        AgentWorkspace.Candidate candidate = null;
        String candidateSelection = "EXPLICIT_ID";
        String requestedCandidateId = decision.providerMetadata().get("candidateId") instanceof String id ? id : "";
        String explicitSource = decision.providerMetadata().get("sourceCode") instanceof String source ? source : null;
        if (explicitSource == null && (context.modifiedSourceRequested || context.workspace.hasPlan())) {
            String legacySource = FinalSourceArtifact.singleCompleteSource(decision.answer());
            if (legacySource != null) {
                if (decision.providerMetadata().get("candidateId") instanceof String requestedId) {
                    if (!context.workspace.requireCandidate(requestedId).sourceCode().equals(legacySource))
                        throw new InvalidToolArgumentsException("Conflicting source block and candidateId; deliver one exact source");
                } else {
                    explicitSource = legacySource;
                    addEvent(context, "candidate.legacy_source_received", Map.of("sourceHash", SourceHashes.sha256(legacySource)));
                }
            }
        }
        if (explicitSource != null) {
            if (decision.providerMetadata().containsKey("candidateId"))
                throw new InvalidToolArgumentsException("Choose sourceCode or candidateId, not both");
            context.workspace.recordDraft(explicitSource);
            candidate = context.workspace.candidateForSource(explicitSource);
            candidateSelection = "EXPLICIT_SOURCE_EXACT_RECEIPT";
        }
        if (!abstained) {
            for (EvidenceItem item : context.evidence) {
                if (!decision.evidenceIds().contains(item.id())) continue;
                Object id = item.metadata().get("contractId");
                Object hash = item.metadata().get("sourceHash");
                if (id instanceof Number contractId && hash instanceof String sourceHash
                        && !context.workspace.isCurrentSource(contractId.longValue(), sourceHash))
                    throw new InvalidToolArgumentsException("Cited source observation is stale; use evidence for the current source version");
            }
            if (explicitSource == null && context.workspace.hasPlan() && context.workspace.latestCandidate() != null) {
                String requestedCandidate = String.valueOf(decision.providerMetadata().getOrDefault("candidateId", ""));
                if (requestedCandidate.isBlank()) {
                    // A validation citation already identifies its exact source. Do not
                    // require the model to repeat a 64-character hash when that selection
                    // is unambiguous. Ending a run does not upgrade a failed check.
                    context.workspace.requireCurrentPlan(context.workspace.planHash());
                    Set<String> citedCandidates = new LinkedHashSet<>();
                    for (EvidenceItem item : context.evidence) {
                        if (decision.evidenceIds().contains(item.id()) && item.sourceType().equals("CANDIDATE_VALIDATION")
                                && item.metadata().get("candidateId") instanceof String id) citedCandidates.add(id);
                    }
                    List<AgentWorkspace.Candidate> cited = new ArrayList<>();
                    for (String id : citedCandidates) {
                        cited.add(context.workspace.requireCandidate(id));
                    }
                    if (cited.size() != 1) throw new InvalidToolArgumentsException(
                            "Missing candidateId: cite exactly one candidate validation, or set the top-level candidateId explicitly; otherwise ABSTAIN");
                    candidate = cited.get(0);
                    candidateSelection = "UNAMBIGUOUS_VALIDATION_CITATION";
                } else candidate = context.workspace.requireCandidate(requestedCandidate);
                String selectedId = candidate.id();
                boolean citedValidation = context.evidence.stream().anyMatch(item -> decision.evidenceIds().contains(item.id())
                        && item.sourceType().equals("CANDIDATE_VALIDATION") && selectedId.equals(item.metadata().get("candidateId")));
                if (!citedValidation) throw new InvalidToolArgumentsException("Cite the selected candidate's actual validation receipt");
            }
        }
        if (explicitSource == null && abstained) {
            if (!requestedCandidateId.isBlank()) {
                // ABSTAIN describes unresolved requirements, not permission to
                // replace an explicitly selected artifact with a later revision.
                candidate = context.workspace.requireCandidate(requestedCandidateId);
                candidateSelection = "EXPLICIT_ID";
            } else if (context.workspace.hasPlan()) {
                candidate = context.workspace.latestCandidate();
                candidateSelection = "LATEST_CHECKED_UNRESOLVED";
            }
        }
        AgentWorkspace.Draft draft = null;
        if (((abstained && requestedCandidateId.isBlank()) || explicitSource != null) && context.workspace.latestDraft() != null
                && (candidate == null || !candidate.sourceHash().equals(context.workspace.latestDraft().sourceHash()))) {
            draft = context.workspace.latestDraft(); candidate = null;
        }
        String deliveredSource = draft != null ? draft.sourceCode() : candidate != null ? candidate.sourceCode() : null;
        if (deliveredSource != null) preflightFinalSource(context, deliveredSource);
        Set<UUID> effectiveCitations = new LinkedHashSet<>(decision.evidenceIds());
        if (candidate != null) {
            String selectedId = candidate.id();
            context.evidence.stream().filter(e -> selectedId.equals(e.metadata().get("candidateId")))
                    .map(EvidenceItem::id).forEach(effectiveCitations::add);
        }
        move(context, AgentSignal.START_SYNTHESIS, "run.synthesizing",
                Map.of("evidenceIds", List.copyOf(effectiveCitations), "abstained", abstained,
                        "stopReason", decision.stopReason()));
        context.answer = stripEvidenceFooter(decision.answer());
        if (candidate == null && draft == null && context.answer != null
                && (context.modifiedSourceRequested && context.answer.contains("```")
                    || context.answer.matches("(?is).*```(?:solidity|sol)\\s.*"))) {
            // Completing a requested draft is distinct from verifying it. This
            // server-owned status cannot be upgraded by model prose or by a
            // compilation receipt for the unchanged catalog source.
            context.answer += "\n\nValidation status: the returned modification has not been executed by the candidate checker in this run; runtime correctness is unverified.";
            addEvent(context, "candidate.unverified_delivery", Map.of("validationScope", "NOT_EXECUTED"));
        }
        if (candidate != null) {
            // Render the tested bytes, not a model-retyped patch after validation.
            context.answer = context.answer.replaceAll("(?s)```[^\\n]*\\n.*?```", "").trim()
                    + (candidate.passed()
                        ? "\n\nCandidate validation: all declared visible checks passed. This is not an independent audit or formal proof."
                        : "\n\nUNRESOLVED CANDIDATE: the following exact source was checked but did not pass every declared check. It is provided for inspection, not as a verified solution.")
                    + "\n\n```solidity\n" + candidate.sourceCode() + "\n```";
            addEvent(context, "candidate.selected", Map.of("candidateId", candidate.id(), "sourceHash", candidate.sourceHash(),
                    "planHash", candidate.planHash(), "sourceCode", candidate.sourceCode(), "validationScope", "DECLARED_VISIBLE_CHECKS",
                    "selectionBasis", candidateSelection, "checksPassed", candidate.passed()));
        } else if (draft != null) {
            context.answer = context.answer.replaceAll("(?s)```[^\\n]*\\n.*?```", "").trim()
                    + "\n\nUNEXECUTED DRAFT: these exact bytes have no completed behavior-check receipt. "
                    + compilationDescription(context.deliveryCompilation)
                    + "\n\n```solidity\n" + draft.sourceCode() + "\n```";
            addEvent(context, "candidate.draft_preserved", Map.of("sourceHash", draft.sourceHash(),
                    "sourceCode", draft.sourceCode(), "validationScope", "NOT_EXECUTED"));
        }
        if (deliveredSource != null && "COMPILATION_FAILED".equals(context.deliveryCompilation.get("status"))) {
            var alternatives = context.workspace.compilableCandidates(context.compilerVersion, SourceHashes.sha256(deliveredSource));
            context.answer += "\n\nCOMPILATION FAILED: the explicitly selected source above is retained; it is not a runnable or verified solution.";
            if (!alternatives.isEmpty()) {
                context.answer += " Earlier compiled candidates are available for explicit selection: "
                        + alternatives.stream().map(a -> String.valueOf(a.get("candidateId"))).toList()
                        + ". Their recorded behavior results still apply; no alternative was substituted.";
                addEvent(context, "candidate.compilable_alternatives", Map.of("candidates", alternatives,
                        "primarySourceHash", SourceHashes.sha256(deliveredSource), "automaticallySelected", false));
            }
        }
        move(context, AgentSignal.COMPLETE, "run.completed", Map.of(
                "answer", context.answer,
                "steps", context.steps,
                "toolCalls", context.toolCalls,
                "retrievalRounds", context.retrievalRounds,
                "modelCalls", context.modelCalls,
                "abstained", abstained,
                "stopReason", decision.stopReason()
        ));
        return result(context, null, null);
    }

    private void preflightFinalSource(Context context, String source) {
        String hash = SourceHashes.sha256(source);
        var previous = context.workspace.compilationForSource(source, context.compilerVersion);
        boolean freshlyChecked = false;
        Map<String, Object> output;
        if (previous != null) output = CompileSourceTool.output(previous, false);
        else if (context.suppressPreflight || Thread.currentThread().isInterrupted())
            output = Map.of("status", "NOT_CHECKED_INTERRUPTED", "sourceHash", hash);
        else if (context.toolCalls >= properties.miniLoop().maxToolCalls())
            output = Map.of("status", "NOT_CHECKED_BUDGET", "sourceHash", hash);
        else if (context.remainingMillis() <= 0)
            output = Map.of("status", "NOT_CHECKED_DEADLINE", "sourceHash", hash);
        else if (toolRegistry.availableDefinitions(Set.of("compile_source.v1")).isEmpty())
            output = Map.of("status", "UNAVAILABLE", "sourceHash", hash, "message", "Source compiler is not registered");
        else {
            context.automaticCompilation = true;
            try { executeTool(context, context.runId, context.userId, AgentDecision.toolCall("compile_source.v1",
                    toJson(Map.of("sourceCode", source, "compilerVersion", context.compilerVersion)), "Compile final source")); }
            finally { context.automaticCompilation = false; }
            output = context.lastToolOutput;
            freshlyChecked = true;
        }
        context.deliveryCompilation = Map.copyOf(output);
        addEvent(context, "candidate.compilation_preflight", Map.of("origin", "FINAL_SOURCE_PREFLIGHT", "sourceHash", hash,
                "compilerVersion", context.compilerVersion, "result", output));
        if (freshlyChecked && "COMPILATION_FAILED".equals(output.get("status"))
                && context.compilationFeedbackHashes.add(context.compilerVersion + ":" + hash)
                && context.modelCalls < properties.miniLoop().maxSteps() && context.steps < properties.miniLoop().maxSteps()) {
            throw new InvalidToolArgumentsException("The exact proposed final source failed compilation. Inspect compile_source diagnostics; "
                    + "choose to repair, select an existing candidate explicitly, or deliver this source with its failure stated. "
                    + "No behavior tests are required and the same failed source will not force another round.");
        }
    }

    private String compilationDescription(Map<String, Object> compile) {
        return switch (String.valueOf(compile.get("status"))) {
            case "COMPILED" -> "Compilation succeeded for these bytes only; behavior and task correctness remain unverified.";
            case "COMPILATION_FAILED" -> "Compilation failed for these bytes; inspect the compiler diagnostics.";
            default -> "Compilation was not confirmed (" + compile.getOrDefault("status", "NOT_CHECKED")
                    + "); neither compilation nor behavior success is claimed.";
        };
    }

    private List<UUID> addEvidence(Context context, List<EvidenceDraft> drafts, UUID toolCallId) {
        List<UUID> ids = new ArrayList<>();
        for (EvidenceDraft draft : drafts) {
            UUID id = UUID.randomUUID();
            Map<String, Object> metadata = new LinkedHashMap<>(draft.metadata());
            metadata.put("toolCallId", toolCallId.toString());
            context.evidence.add(new EvidenceItem(id, draft.sourceType(), draft.sourceRef(), draft.title(),
                    draft.excerpt(), draft.trustLevel(), Map.copyOf(metadata)));
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    private MiniLoopResult fail(Context context, AgentErrorCode code, String message) {
        if (!context.state.isTerminal()) {
            move(context, AgentSignal.FAIL, "run.failed", Map.of("errorCode", code.name(), "message", message));
        }
        return result(context, code, message);
    }

    private MiniLoopResult result(Context context, AgentErrorCode code, String message) {
        return new MiniLoopResult(context.state, context.answer, code, message, context.steps, context.toolCalls,
                context.retrievalRounds, List.copyOf(context.events), List.copyOf(context.evidence));
    }

    private void move(Context context, AgentSignal signal, String type, Map<String, Object> data) {
        context.state = stateMachine.transition(context.state, signal);
        addEvent(context, type, data);
    }

    private void addEvent(Context context, String type, Map<String, Object> data) {
        AgentEvent event = new AgentEvent(context.events.size() + 1L, type, Instant.now(),
                Map.of("state", context.state.name(), "payload", data));
        context.events.add(event);
        context.eventSink.accept(event);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AgentToolException(AgentErrorCode.INTERNAL_ERROR, "Unable to serialize tool observation", exception);
        }
    }

    private String stripEvidenceFooter(String answer) {
        if (answer == null) return null;
        return answer.replaceFirst("(?is)\\s*Evidence:\\s*(?:\\[[0-9a-f-]{36}\\]\\s*)+$", "").trim();
    }

    private static final class Context {
        private final Consumer<AgentEvent> eventSink;
        private AgentRunState state = AgentRunState.RECEIVED;
        private String answer;
        private String task;
        private UUID runId;
        private long userId;
        private long deadlineNanos = Long.MAX_VALUE;
        private String compilerVersion = CompileSourceTool.DEFAULT_COMPILER_VERSION;
        private boolean automaticCompilation;
        private boolean suppressPreflight;
        private Map<String, Object> deliveryCompilation = Map.of();
        private final Set<String> compilationFeedbackHashes = new LinkedHashSet<>();
        private int steps;
        private int modelCalls;
        private int toolCalls;
        private int retrievalRounds;
        private boolean duplicateRecovered;
        private boolean modifiedSourceRequested;
        private boolean expandingDependencies;
        private Map<String, Object> lastRejectedAction = Map.of();
        private Map<String, Object> lastToolOutput = Map.of();
        private final Map<String, Map<String, Object>> toolOutputs = new LinkedHashMap<>();
        private final Set<String> dependencyPaths = new LinkedHashSet<>();
        private final List<AgentEvent> events = new ArrayList<>();
        private final List<EvidenceItem> evidence = new ArrayList<>();
        private final List<String> observations = new ArrayList<>();
        private final Set<String> invocations = new LinkedHashSet<>();
        private final AgentWorkspace workspace;

        private long remainingMillis() {
            return deadlineNanos == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, (deadlineNanos - System.nanoTime()) / 1_000_000);
        }

        private Context(Consumer<AgentEvent> eventSink, int candidateLimit) {
            this.workspace = new AgentWorkspace(candidateLimit);
            this.eventSink = eventSink == null ? event -> { } : eventSink;
        }
    }
}
