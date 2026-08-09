package org.scse.cloudagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.AgentEvent;
import org.scse.cloudagent.domain.AgentErrorCode;
import org.scse.cloudagent.domain.AgentRun;
import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.domain.AgentSignal;
import org.scse.cloudagent.domain.EvidenceItem;
import org.scse.cloudagent.domain.Proposal;
import org.scse.cloudagent.domain.TrustLevel;
import org.scse.cloudagent.dto.CreateRunRequest;
import org.scse.cloudagent.dto.CreateSessionRequest;
import org.scse.cloudagent.dto.AgentSessionSummary;
import org.scse.cloudagent.dto.SessionResponse;
import org.scse.cloudagent.state.AgentStateMachine;
import org.scse.cloudagent.tool.AgentTool;
import org.scse.cloudagent.tool.ToolInvocationValidator;
import org.scse.cloudagent.tool.ToolRegistry;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;

@Service
public class InMemoryAgentRunService {
    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentRunService.class);
    private final Map<UUID, MutableRun> runs = new ConcurrentHashMap<>();
    private final Map<UUID, SessionResponse> sessions = new ConcurrentHashMap<>();
    private final AgentStateMachine stateMachine;
    private final ToolRegistry toolRegistry;
    private final ToolInvocationValidator validator;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final MiniAgentLoopEngine miniAgentLoopEngine;
    private final AgentRunArchive archive;
    private final TaskExecutor taskExecutor;
    private final Counter createdCounter;
    private final Counter completedCounter;

    public InMemoryAgentRunService(AgentStateMachine stateMachine,
                                   ToolRegistry toolRegistry,
                                   ToolInvocationValidator validator,
                                   ObjectMapper objectMapper,
                                   AgentProperties properties,
                                   MiniAgentLoopEngine miniAgentLoopEngine,
                                   AgentRunArchive archive,
                                   @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor,
                                   MeterRegistry meterRegistry) {
        this.stateMachine = stateMachine;
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.miniAgentLoopEngine = miniAgentLoopEngine;
        this.archive = archive;
        this.taskExecutor = taskExecutor;
        this.createdCounter = Counter.builder("agent.runs.created").register(meterRegistry);
        this.completedCounter = Counter.builder("agent.runs.completed").register(meterRegistry);
    }

    public SessionResponse createSession(long userId, CreateSessionRequest request) {
        UUID id = UUID.randomUUID();
        String title = request.title() == null || request.title().isBlank() ? "New agent session" : request.title().trim();
        SessionResponse session = new SessionResponse(id, userId, title,
                sanitizeWorkspaceContext(request.workspaceContext()), Instant.now());
        sessions.put(id, session);
        archive.saveSession(session);
        return session;
    }

    public SessionResponse getSession(long userId, UUID sessionId) {
        SessionResponse session = sessions.get(sessionId);
        if (session == null) {
            archive.findSessions(userId).stream().filter(item -> item.id().equals(sessionId)).findFirst()
                    .ifPresent(item -> sessions.put(item.id(), item));
            session = sessions.get(sessionId);
        }
        if (session == null || session.userId() != userId) {
            throw new AgentResourceNotFoundException("Session not found");
        }
        return session;
    }

    public List<AgentSessionSummary> listSessions(long userId) {
        archive.findSessions(userId).forEach(session -> sessions.putIfAbsent(session.id(), session));
        return sessions.values().stream()
                .filter(session -> session.userId() == userId)
                .map(session -> {
                    List<AgentRun> sessionRuns = listRuns(userId, session.id());
                    Instant updatedAt = sessionRuns.stream().map(AgentRun::updatedAt).max(Instant::compareTo)
                            .orElse(session.createdAt());
                    return new AgentSessionSummary(session.id(), session.title(), session.createdAt(), updatedAt,
                            sessionRuns.size());
                })
                .sorted(Comparator.comparing(AgentSessionSummary::updatedAt).reversed())
                .toList();
    }

    public List<AgentRun> listRuns(long userId, UUID sessionId) {
        getSession(userId, sessionId);
        Map<UUID, AgentRun> merged = new LinkedHashMap<>();
        archive.findRuns(userId, sessionId).forEach(run -> merged.put(run.id(), run));
        runs.values().stream()
                .filter(run -> run.userId == userId && sessionId.equals(run.sessionId))
                .map(MutableRun::snapshot)
                .forEach(run -> merged.put(run.id(), run));
        return merged.values().stream().sorted(Comparator.comparing(AgentRun::createdAt)).toList();
    }

    public void deleteSession(long userId, UUID sessionId) {
        getSession(userId, sessionId);
        runs.entrySet().removeIf(entry -> entry.getValue().userId == userId
                && sessionId.equals(entry.getValue().sessionId));
        sessions.remove(sessionId);
        archive.deleteSession(userId, sessionId);
    }

    public AgentRun createMockRun(long userId, CreateRunRequest request) {
        return createRun(userId, request);
    }

    public AgentRun createRun(long userId, CreateRunRequest request) {
        UUID sessionId = request.sessionId();
        if (sessionId != null) {
            getSession(userId, sessionId);
        }
        String traceId = MDC.get("traceId") == null ? UUID.randomUUID().toString() : MDC.get("traceId");
        MutableRun run = new MutableRun(UUID.randomUUID(), sessionId, userId, request.task(), properties.model(), traceId);
        runs.put(run.id, run);
        createdCounter.increment();
        String mode = properties.miniLoop().enabled() ? "MINI_AGENTIC_RAG" : "MOCK";
        log.info("event=agent_run_created run_id={} user_id={} model={} mode={}", run.id, userId, run.model, mode);
        Map<String, Object> workspaceContext = sanitizeWorkspaceContext(request.workspaceContext());
        taskExecutor.execute(() -> executeRun(run, workspaceContext));
        return run.snapshot();
    }

    public AgentRun getOwned(long userId, UUID runId) {
        MutableRun run = runs.get(runId);
        if (run == null || run.userId != userId) {
            throw new AgentResourceNotFoundException("Run not found");
        }
        return run.snapshot();
    }

    private void executeMockLoop(MutableRun run) {
        move(run, AgentSignal.START_PLAN, "run.plan", Map.of("mode", "MOCK"));
        move(run, AgentSignal.REQUEST_TOOL, "tool.requested", Map.of("tool", "mock_echo.v1"));
        AgentTool tool = toolRegistry.require("mock_echo.v1");
        Map<String, Object> output = tool.invoke(validator.parseObject(toArguments(run.task)));
        run.evidence.add(new EvidenceItem(UUID.randomUUID(), "MOCK_TOOL", "mock://echo", "Mock tool observation",
                String.valueOf(output.get("echo")), TrustLevel.TOOL_GENERATED, output));
        move(run, AgentSignal.RECORD_OBSERVATION, "tool.completed", Map.of("tool", tool.definition().name(), "output", output));
        move(run, AgentSignal.START_SYNTHESIS, "run.synthesizing", Map.of());
        run.answer = "Mock agent completed: " + run.task;
        run.proposals.add(new Proposal(UUID.randomUUID(), "NO_OP", "PENDING", "Mock reviewable proposal",
                Map.of("sideEffect", "NONE"), Instant.now()));
        move(run, AgentSignal.COMPLETE, "run.completed", Map.of("answer", run.answer));
        completedCounter.increment();
        log.info("event=agent_run_completed run_id={} user_id={} state={} event_count={} evidence_count={}",
                run.id, run.userId, run.state, run.events.size(), run.evidence.size());
    }

    private void executeRun(MutableRun run, Map<String, Object> workspaceContext) {
        MDC.put("traceId", run.traceId);
        try {
            if (properties.miniLoop().enabled()) {
                executeMiniLoop(run, workspaceContext);
            } else {
                executeMockLoop(run);
            }
        } catch (RuntimeException exception) {
            log.error("event=agent_run_crashed run_id={} user_id={}", run.id, run.userId, exception);
            run.fail(AgentErrorCode.INTERNAL_ERROR, "Agent execution failed: " + exception.getClass().getSimpleName());
        } finally {
            try {
                archive.saveRun(run.snapshot());
            } catch (RuntimeException exception) {
                log.error("event=agent_run_archive_failed run_id={}", run.id, exception);
            }
            MDC.remove("traceId");
        }
    }

    private void executeMiniLoop(MutableRun run, Map<String, Object> workspaceContext) {
        MiniLoopResult result = miniAgentLoopEngine.execute(
                run.id, run.userId, run.task, workspaceContext, run::recordEvent);
        run.finish(result);
        if (run.state == AgentRunState.COMPLETE) completedCounter.increment();
        log.info("event=agent_run_finished run_id={} user_id={} state={} error_code={} steps={} tool_calls={} retrieval_rounds={}",
                run.id, run.userId, run.state, run.errorCode, run.steps, run.toolCalls, run.retrievalRounds);
    }

    private Map<String, Object> sanitizeWorkspaceContext(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        Map<String, Object> sanitized = new LinkedHashMap<>();
        copyPositiveLong(raw, sanitized, "selectedContractId");
        copyPositiveLong(raw, sanitized, "draftId");
        copyPositiveLong(raw, sanitized, "conversationSessionId");
        copyBoundedText(raw, sanitized, "activeLabFile", 500);
        copyBoundedText(raw, sanitized, "origin", 64);
        Object attachmentIds = raw.get("attachmentIds");
        if (attachmentIds instanceof List<?> values) {
            List<Long> ids = values.stream().limit(20).map(this::positiveLong).filter(java.util.Objects::nonNull).toList();
            if (!ids.isEmpty()) sanitized.put("attachmentIds", ids);
        }
        return Map.copyOf(sanitized);
    }

    private void copyPositiveLong(Map<String, Object> source, Map<String, Object> target, String key) {
        Long value = positiveLong(source.get(key));
        if (value != null) target.put(key, value);
    }

    private Long positiveLong(Object value) {
        try {
            long parsed = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void copyBoundedText(Map<String, Object> source, Map<String, Object> target, String key, int maxLength) {
        Object value = source.get(key);
        if (value == null) return;
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) target.put(key, text.substring(0, Math.min(text.length(), maxLength)));
    }

    private String toArguments(String task) {
        try {
            return objectMapper.writeValueAsString(Map.of("message", task));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize mock tool arguments", exception);
        }
    }

    private void move(MutableRun run, AgentSignal signal, String type, Map<String, Object> data) {
        synchronized (run) {
            run.state = stateMachine.transition(run.state, signal);
            run.updatedAt = Instant.now();
            run.events.add(new AgentEvent(run.events.size() + 1L, type, run.updatedAt,
                    Map.of("state", run.state.name(), "payload", data)));
        }
    }

    private static final class MutableRun {
        private final UUID id;
        private final UUID sessionId;
        private final long userId;
        private final String task;
        private final String model;
        private final String traceId;
        private AgentRunState state = AgentRunState.RECEIVED;
        private String answer;
        private org.scse.cloudagent.domain.AgentErrorCode errorCode;
        private String failureMessage;
        private int steps;
        private int toolCalls;
        private int retrievalRounds;
        private final Instant createdAt = Instant.now();
        private Instant updatedAt = createdAt;
        private final List<AgentEvent> events = new ArrayList<>();
        private final List<EvidenceItem> evidence = new ArrayList<>();
        private final List<Proposal> proposals = new ArrayList<>();

        private MutableRun(UUID id, UUID sessionId, long userId, String task, String model, String traceId) {
            this.id = id;
            this.sessionId = sessionId;
            this.userId = userId;
            this.task = task;
            this.model = model;
            this.traceId = traceId;
        }

        private synchronized void recordEvent(AgentEvent event) {
            events.add(event);
            Object stateValue = event.data().get("state");
            if (stateValue != null) state = AgentRunState.valueOf(String.valueOf(stateValue));
            Map<?, ?> payload = event.data().get("payload") instanceof Map<?, ?> map ? map : Map.of();
            if ("model.action".equals(event.type())) {
                steps = Math.max(steps, integerValue(payload.get("step")));
            } else if ("tool.requested".equals(event.type())) {
                toolCalls++;
                String tool = String.valueOf(payload.get("tool"));
                if ("search_contracts.v1".equals(tool) || "retrieve_knowledge.v1".equals(tool)) retrievalRounds++;
            }
            updatedAt = event.createdAt();
        }

        private synchronized void finish(MiniLoopResult result) {
            state = result.state();
            answer = result.answer();
            errorCode = result.errorCode();
            failureMessage = result.failureMessage();
            steps = result.steps();
            toolCalls = result.toolCalls();
            retrievalRounds = result.retrievalRounds();
            evidence.clear();
            evidence.addAll(result.evidence());
            updatedAt = Instant.now();
        }

        private synchronized void fail(AgentErrorCode code, String message) {
            if (!state.isTerminal()) {
                state = AgentRunState.FAILED;
                errorCode = code;
                failureMessage = message;
                updatedAt = Instant.now();
                events.add(new AgentEvent(events.size() + 1L, "run.failed", updatedAt,
                        Map.of("state", state.name(), "payload", Map.of(
                                "errorCode", code.name(), "message", message))));
            }
        }

        private int integerValue(Object value) {
            if (value instanceof Number number) return number.intValue();
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (RuntimeException ignored) {
                return 0;
            }
        }

        private synchronized AgentRun snapshot() {
            return new AgentRun(id, sessionId, userId, task, model, traceId, state, answer,
                    errorCode, failureMessage, steps, toolCalls, retrievalRounds, createdAt, updatedAt,
                    List.copyOf(events), List.copyOf(evidence), List.copyOf(proposals));
        }
    }
}
