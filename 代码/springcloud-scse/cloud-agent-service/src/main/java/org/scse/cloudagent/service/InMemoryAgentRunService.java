package org.scse.cloudagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.AgentEvent;
import org.scse.cloudagent.domain.AgentRun;
import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.domain.AgentSignal;
import org.scse.cloudagent.domain.EvidenceItem;
import org.scse.cloudagent.domain.Proposal;
import org.scse.cloudagent.domain.TrustLevel;
import org.scse.cloudagent.dto.CreateRunRequest;
import org.scse.cloudagent.dto.CreateSessionRequest;
import org.scse.cloudagent.dto.SessionResponse;
import org.scse.cloudagent.state.AgentStateMachine;
import org.scse.cloudagent.tool.AgentTool;
import org.scse.cloudagent.tool.ToolInvocationValidator;
import org.scse.cloudagent.tool.ToolRegistry;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Counter createdCounter;
    private final Counter completedCounter;

    public InMemoryAgentRunService(AgentStateMachine stateMachine,
                                   ToolRegistry toolRegistry,
                                   ToolInvocationValidator validator,
                                   ObjectMapper objectMapper,
                                   AgentProperties properties,
                                   MeterRegistry meterRegistry) {
        this.stateMachine = stateMachine;
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.createdCounter = Counter.builder("agent.runs.created").register(meterRegistry);
        this.completedCounter = Counter.builder("agent.runs.completed").register(meterRegistry);
    }

    public SessionResponse createSession(long userId, CreateSessionRequest request) {
        UUID id = UUID.randomUUID();
        String title = request.title() == null || request.title().isBlank() ? "New agent session" : request.title().trim();
        SessionResponse session = new SessionResponse(id, userId, title,
                request.workspaceContext() == null ? Map.of() : Map.copyOf(request.workspaceContext()), Instant.now());
        sessions.put(id, session);
        return session;
    }

    public SessionResponse getSession(long userId, UUID sessionId) {
        SessionResponse session = sessions.get(sessionId);
        if (session == null || session.userId() != userId) {
            throw new AgentResourceNotFoundException("Session not found");
        }
        return session;
    }

    public AgentRun createMockRun(long userId, CreateRunRequest request) {
        UUID sessionId = request.sessionId();
        if (sessionId != null) {
            getSession(userId, sessionId);
        }
        String traceId = MDC.get("traceId") == null ? UUID.randomUUID().toString() : MDC.get("traceId");
        MutableRun run = new MutableRun(UUID.randomUUID(), sessionId, userId, request.task(), properties.model(), traceId);
        runs.put(run.id, run);
        createdCounter.increment();
        log.info("event=agent_run_created run_id={} user_id={} model={} mode=MOCK", run.id, userId, run.model);
        executeMockLoop(run);
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

    private String toArguments(String task) {
        try {
            return objectMapper.writeValueAsString(Map.of("message", task));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize mock tool arguments", exception);
        }
    }

    private void move(MutableRun run, AgentSignal signal, String type, Map<String, Object> data) {
        run.state = stateMachine.transition(run.state, signal);
        run.updatedAt = Instant.now();
        run.events.add(new AgentEvent(run.events.size() + 1L, type, run.updatedAt,
                Map.of("state", run.state.name(), "payload", data)));
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

        private synchronized AgentRun snapshot() {
            return new AgentRun(id, sessionId, userId, task, model, traceId, state, answer, createdAt, updatedAt,
                    List.copyOf(events), List.copyOf(evidence), List.copyOf(proposals));
        }
    }
}
