package org.scse.cloudagent.controller;

import jakarta.validation.Valid;
import org.scse.cloudagent.domain.AgentEvent;
import org.scse.cloudagent.domain.AgentRun;
import org.scse.cloudagent.domain.EvidenceItem;
import org.scse.cloudagent.domain.Proposal;
import org.scse.cloudagent.dto.CreateRunRequest;
import org.scse.cloudagent.dto.CreateSessionRequest;
import org.scse.cloudagent.dto.SessionResponse;
import org.scse.cloudagent.dto.AgentSessionSummary;
import org.scse.cloudagent.service.InMemoryAgentRunService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/v1")
@ConditionalOnProperty(prefix = "agent", name = "enabled", havingValue = "true")
public class AgentRunController {
    private static final String USER_HEADER = "X-User-Id";
    private final InMemoryAgentRunService runService;

    public AgentRunController(InMemoryAgentRunService runService) {
        this.runService = runService;
    }

    @PostMapping("/sessions")
    public SessionResponse createSession(@RequestHeader(USER_HEADER) long userId,
                                         @Valid @RequestBody CreateSessionRequest request) {
        return runService.createSession(userId, request);
    }

    @GetMapping("/sessions")
    public List<AgentSessionSummary> listSessions(@RequestHeader(USER_HEADER) long userId) {
        return runService.listSessions(userId);
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionResponse getSession(@RequestHeader(USER_HEADER) long userId,
                                      @PathVariable UUID sessionId) {
        return runService.getSession(userId, sessionId);
    }

    @GetMapping("/sessions/{sessionId}/runs")
    public List<AgentRun> listSessionRuns(@RequestHeader(USER_HEADER) long userId,
                                          @PathVariable UUID sessionId) {
        return runService.listRuns(userId, sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(@RequestHeader(USER_HEADER) long userId,
                              @PathVariable UUID sessionId) {
        runService.deleteSession(userId, sessionId);
    }

    @PostMapping("/runs")
    public AgentRun createRun(@RequestHeader(USER_HEADER) long userId,
                              @Valid @RequestBody CreateRunRequest request) {
        return runService.createRun(userId, request);
    }

    @GetMapping("/runs/{runId}")
    public AgentRun getRun(@RequestHeader(USER_HEADER) long userId,
                           @PathVariable UUID runId) {
        return runService.getOwned(userId, runId);
    }

    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestHeader(USER_HEADER) long userId,
                             @PathVariable UUID runId,
                             @RequestParam(defaultValue = "0") long after) {
        AgentRun run = runService.getOwned(userId, runId);
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            for (AgentEvent event : run.events()) {
                if (event.sequence() <= after) continue;
                emitter.send(SseEmitter.event()
                        .id(Long.toString(event.sequence()))
                        .name(event.type())
                        .data(event));
            }
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @GetMapping("/runs/{runId}/evidence")
    public List<EvidenceItem> evidence(@RequestHeader(USER_HEADER) long userId,
                                       @PathVariable UUID runId) {
        return runService.getOwned(userId, runId).evidence();
    }

    @GetMapping("/runs/{runId}/proposals")
    public List<Proposal> proposals(@RequestHeader(USER_HEADER) long userId,
                                    @PathVariable UUID runId) {
        return runService.getOwned(userId, runId).proposals();
    }
}
