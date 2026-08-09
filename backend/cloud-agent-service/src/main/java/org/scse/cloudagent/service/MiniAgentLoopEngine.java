package org.scse.cloudagent.service;

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
import org.scse.cloudagent.tool.AgentToolException;
import org.scse.cloudagent.tool.EvidenceDraft;
import org.scse.cloudagent.tool.InvalidToolArgumentsException;
import org.scse.cloudagent.tool.MiniLoopToolPolicy;
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
        Context context = new Context(eventSink);
        move(context, AgentSignal.START_PLAN, "run.plan", Map.of("mode", "MINI_AGENTIC_RAG"));
        try {
            while (context.steps < properties.miniLoop().maxSteps()) {
                context.steps++;
                int remainingToolCalls = properties.miniLoop().maxToolCalls() - context.toolCalls;
                int remainingRetrievalRounds = properties.miniLoop().maxRetrievalRounds() - context.retrievalRounds;
                AgentDecision decision = provider.decide(new AgentDecisionRequest(
                        task,
                        workspaceContext,
                        context.observations,
                        context.evidence,
                        availableTools(remainingToolCalls, remainingRetrievalRounds),
                        context.steps,
                        remainingToolCalls,
                        remainingRetrievalRounds
                ));
                Map<String, Object> modelAction = new LinkedHashMap<>();
                modelAction.put("step", context.steps);
                modelAction.put("actionType", decision.type().name());
                modelAction.put("reasonSummary", decision.reasonSummary());
                modelAction.put("evidenceGap", decision.evidenceGap());
                modelAction.put("citedEvidenceIds", decision.evidenceIds());
                modelAction.put("stopReason", decision.stopReason());
                modelAction.put("provider", decision.providerMetadata());
                addEvent(context, "model.action", Map.copyOf(modelAction));
                if (decision.type() == AgentDecision.Type.FINAL_ANSWER
                        || decision.type() == AgentDecision.Type.ABSTAIN) {
                    return complete(context, decision);
                }
                executeTool(context, runId, userId, decision);
            }
            return fail(context, AgentErrorCode.BUDGET_EXCEEDED, "Maximum Agent steps exceeded");
        } catch (AgentToolException exception) {
            return fail(context, exception.errorCode(), exception.getMessage());
        } catch (UnknownToolException exception) {
            return fail(context, AgentErrorCode.UNKNOWN_TOOL, exception.getMessage());
        } catch (InvalidToolArgumentsException exception) {
            return fail(context, AgentErrorCode.INVALID_TOOL_ARGUMENTS, exception.getMessage());
        } catch (RuntimeException exception) {
            return fail(context, AgentErrorCode.INTERNAL_ERROR, "Agent loop failed: " + exception.getClass().getSimpleName());
        }
    }

    private List<ToolDefinition> availableTools(int remainingToolCalls, int remainingRetrievalRounds) {
        if (remainingToolCalls <= 0) return List.of();
        return toolRegistry.availableDefinitions(MiniLoopToolPolicy.ALLOWED_TOOLS).stream()
                .filter(tool -> remainingRetrievalRounds > 0
                        || (!("search_contracts.v1".equals(tool.name()))
                        && !("retrieve_knowledge.v1".equals(tool.name()))))
                .toList();
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
        JsonNode arguments = validator.parseObject(decision.argumentsJson());
        String invocationKey = tool.definition().name() + ":" + arguments.toString();
        if (!context.invocations.add(invocationKey)) {
            throw new AgentToolException(AgentErrorCode.LOOP_DETECTED,
                    "Repeated identical tool invocation was blocked: " + tool.definition().name());
        }
        UUID toolCallId = UUID.randomUUID();
        AgentSignal signal = context.state == AgentRunState.PLAN ? AgentSignal.REQUEST_TOOL : AgentSignal.RETRY_TOOL;
        move(context, signal, "tool.requested", Map.of(
                "toolCallId", toolCallId,
                "tool", tool.definition().name(),
                "schemaVersion", tool.definition().name().substring(tool.definition().name().lastIndexOf('.') + 1),
                "sideEffect", tool.definition().sideEffect().name(),
                "arguments", arguments
        ));
        context.toolCalls++;
        if (retrieval) context.retrievalRounds++;
        ToolExecutionResult result = tool.execute(arguments, new ToolExecutionContext(userId, runId));
        List<UUID> evidenceIds = addEvidence(context, result.evidence(), toolCallId);
        Map<String, Object> observation = new LinkedHashMap<>(result.output());
        observation.put("toolCallId", toolCallId.toString());
        observation.put("evidenceIds", evidenceIds.stream().map(UUID::toString).toList());
        context.observations.add(contentBoundary.wrap(tool.definition().name(), toJson(observation)));
        move(context, AgentSignal.RECORD_OBSERVATION, "tool.completed", Map.of(
                "toolCallId", toolCallId,
                "tool", tool.definition().name(),
                "evidenceIds", evidenceIds,
                "outputKeys", new ArrayList<>(result.output().keySet())
        ));
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
        move(context, AgentSignal.START_SYNTHESIS, "run.synthesizing",
                Map.of("evidenceIds", decision.evidenceIds(), "abstained", abstained,
                        "stopReason", decision.stopReason()));
        context.answer = decision.answer();
        if (!decision.evidenceIds().isEmpty()) {
            context.answer += "\n\nEvidence: "
                    + decision.evidenceIds().stream().map(id -> "[" + id + "]")
                    .reduce((a, b) -> a + " " + b).orElse("");
        }
        move(context, AgentSignal.COMPLETE, "run.completed", Map.of(
                "answer", context.answer,
                "steps", context.steps,
                "toolCalls", context.toolCalls,
                "retrievalRounds", context.retrievalRounds,
                "abstained", abstained,
                "stopReason", decision.stopReason()
        ));
        return result(context, null, null);
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

    private static final class Context {
        private final Consumer<AgentEvent> eventSink;
        private AgentRunState state = AgentRunState.RECEIVED;
        private String answer;
        private int steps;
        private int toolCalls;
        private int retrievalRounds;
        private final List<AgentEvent> events = new ArrayList<>();
        private final List<EvidenceItem> evidence = new ArrayList<>();
        private final List<String> observations = new ArrayList<>();
        private final Set<String> invocations = new LinkedHashSet<>();

        private Context(Consumer<AgentEvent> eventSink) {
            this.eventSink = eventSink == null ? event -> { } : eventSink;
        }
    }
}
