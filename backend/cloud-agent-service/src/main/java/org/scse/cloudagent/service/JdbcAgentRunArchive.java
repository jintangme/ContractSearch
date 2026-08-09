package org.scse.cloudagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scse.cloudagent.config.AgentPersistenceProperties;
import org.scse.cloudagent.domain.AgentEvent;
import org.scse.cloudagent.domain.AgentErrorCode;
import org.scse.cloudagent.domain.AgentRun;
import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.domain.EvidenceItem;
import org.scse.cloudagent.domain.Proposal;
import org.scse.cloudagent.domain.TrustLevel;
import org.scse.cloudagent.dto.SessionResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "agent.persistence", name = "enabled", havingValue = "true")
public class JdbcAgentRunArchive implements AgentRunArchive {
    private final AgentPersistenceProperties properties;
    private final ObjectMapper objectMapper;

    public JdbcAgentRunArchive(AgentPersistenceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SessionResponse> findSessions(long userId) {
        List<SessionResponse> result = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT id, user_id, title, workspace_context, created_at
                FROM agent_session WHERE user_id = ? ORDER BY updated_at DESC
                """)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new SessionResponse(rows.getObject("id", UUID.class), rows.getLong("user_id"),
                            rows.getString("title"), jsonMap(rows.getString("workspace_context")),
                            rows.getObject("created_at", java.time.OffsetDateTime.class).toInstant()));
                }
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load Agent sessions", exception);
        }
    }

    @Override
    public List<AgentRun> findRuns(long userId, UUID sessionId) {
        List<AgentRun> result = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM agent_run
                WHERE user_id = ? AND session_id = ? ORDER BY created_at ASC
                """)) {
            statement.setLong(1, userId);
            statement.setObject(2, sessionId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(readRun(connection, rows));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load Agent runs", exception);
        }
    }

    private AgentRun readRun(Connection connection, ResultSet row) throws Exception {
        UUID runId = row.getObject("id", UUID.class);
        String terminalCode = row.getString("terminal_code");
        return new AgentRun(runId, row.getObject("session_id", UUID.class), row.getLong("user_id"),
                row.getString("task"), row.getString("model_profile"), row.getString("trace_id"),
                AgentRunState.valueOf(row.getString("state")), row.getString("answer"),
                terminalCode == null ? null : AgentErrorCode.valueOf(terminalCode), row.getString("failure_message"),
                row.getInt("step_count"), row.getInt("tool_call_count"), row.getInt("retrieval_round_count"),
                row.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", java.time.OffsetDateTime.class).toInstant(),
                readEvents(connection, runId), readEvidence(connection, runId), readProposals(connection, runId));
    }

    private List<AgentEvent> readEvents(Connection connection, UUID runId) throws Exception {
        List<AgentEvent> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sequence_no, event_type, payload, created_at FROM agent_event
                WHERE run_id = ? ORDER BY sequence_no ASC
                """)) {
            statement.setObject(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new AgentEvent(rows.getLong("sequence_no"), rows.getString("event_type"),
                        rows.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                        jsonMap(rows.getString("payload"))));
            }
        }
        return List.copyOf(result);
    }

    private List<EvidenceItem> readEvidence(Connection connection, UUID runId) throws Exception {
        List<EvidenceItem> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, source_type, source_ref, title, excerpt, trust_level, metadata
                FROM agent_evidence WHERE run_id = ? ORDER BY created_at ASC
                """)) {
            statement.setObject(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new EvidenceItem(rows.getObject("id", UUID.class),
                        rows.getString("source_type"), rows.getString("source_ref"), rows.getString("title"),
                        rows.getString("excerpt"), TrustLevel.valueOf(rows.getString("trust_level")),
                        jsonMap(rows.getString("metadata"))));
            }
        }
        return List.copyOf(result);
    }

    private List<Proposal> readProposals(Connection connection, UUID runId) throws Exception {
        List<Proposal> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, proposal_kind, status, summary, payload, created_at
                FROM agent_proposal WHERE run_id = ? ORDER BY created_at ASC
                """)) {
            statement.setObject(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new Proposal(rows.getObject("id", UUID.class),
                        rows.getString("proposal_kind"), rows.getString("status"), rows.getString("summary"),
                        jsonMap(rows.getString("payload")),
                        rows.getObject("created_at", java.time.OffsetDateTime.class).toInstant()));
            }
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonMap(String value) {
        try {
            return value == null ? Map.of() : objectMapper.readValue(value, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize Agent audit data", exception);
        }
    }

    @Override
    public void saveSession(SessionResponse session) {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_session(id, user_id, title, workspace_context, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title,
                  workspace_context = EXCLUDED.workspace_context, updated_at = EXCLUDED.updated_at
                """)) {
            statement.setObject(1, session.id());
            statement.setLong(2, session.userId());
            statement.setString(3, session.title());
            statement.setString(4, json(session.workspaceContext()));
            statement.setObject(5, dbTime(session.createdAt()));
            statement.setObject(6, dbTime(session.createdAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to archive Agent session", exception);
        }
    }

    @Override
    public void saveRun(AgentRun run) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                upsertRun(connection, run);
                for (AgentEvent event : run.events()) upsertEvent(connection, run, event);
                for (EvidenceItem evidence : run.evidence()) upsertEvidence(connection, run, evidence);
                for (Proposal proposal : run.proposals()) upsertProposal(connection, run, proposal);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to archive Agent run", exception);
        }
    }

    @Override
    public void deleteSession(long userId, java.util.UUID sessionId) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteRuns = connection.prepareStatement(
                    "DELETE FROM agent_run WHERE session_id = ? AND user_id = ?");
                 PreparedStatement deleteSession = connection.prepareStatement(
                         "DELETE FROM agent_session WHERE id = ? AND user_id = ?")) {
                deleteRuns.setObject(1, sessionId);
                deleteRuns.setLong(2, userId);
                deleteRuns.executeUpdate();
                deleteSession.setObject(1, sessionId);
                deleteSession.setLong(2, userId);
                deleteSession.executeUpdate();
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to delete Agent session", exception);
        }
    }

    private void upsertRun(Connection connection, AgentRun run) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_run(id, session_id, user_id, task, model_profile, state, terminal_code,
                  answer, trace_id, step_count, tool_call_count, failure_message, retrieval_round_count,
                  prompt_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET state = EXCLUDED.state, terminal_code = EXCLUDED.terminal_code,
                  answer = EXCLUDED.answer, step_count = EXCLUDED.step_count,
                  tool_call_count = EXCLUDED.tool_call_count, failure_message = EXCLUDED.failure_message,
                  retrieval_round_count = EXCLUDED.retrieval_round_count,
                  prompt_version = EXCLUDED.prompt_version, updated_at = EXCLUDED.updated_at
                """)) {
            statement.setObject(1, run.id());
            statement.setObject(2, run.sessionId());
            statement.setLong(3, run.userId());
            statement.setString(4, run.task());
            statement.setString(5, run.model());
            statement.setString(6, run.state().name());
            statement.setString(7, run.errorCode() == null ? null : run.errorCode().name());
            statement.setString(8, run.answer());
            statement.setString(9, run.traceId());
            statement.setInt(10, run.steps());
            statement.setInt(11, run.toolCalls());
            statement.setString(12, run.failureMessage());
            statement.setInt(13, run.retrievalRounds());
            statement.setString(14, GlmStructuredActionProvider.PROMPT_VERSION);
            statement.setObject(15, dbTime(run.createdAt()));
            statement.setObject(16, dbTime(run.updatedAt()));
            statement.executeUpdate();
        }
    }

    private void upsertEvent(Connection connection, AgentRun run, AgentEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_event(run_id, sequence_no, event_type, payload, created_at)
                VALUES (?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (run_id, sequence_no) DO UPDATE SET event_type = EXCLUDED.event_type,
                  payload = EXCLUDED.payload, created_at = EXCLUDED.created_at
                """)) {
            statement.setObject(1, run.id());
            statement.setLong(2, event.sequence());
            statement.setString(3, event.type());
            statement.setString(4, json(event.data()));
            statement.setObject(5, dbTime(event.createdAt()));
            statement.executeUpdate();
        }
    }

    private void upsertEvidence(Connection connection, AgentRun run, EvidenceItem evidence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_evidence(id, run_id, source_type, source_ref, title, excerpt,
                  trust_level, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, excerpt = EXCLUDED.excerpt,
                  trust_level = EXCLUDED.trust_level, metadata = EXCLUDED.metadata
                """)) {
            statement.setObject(1, evidence.id());
            statement.setObject(2, run.id());
            statement.setString(3, evidence.sourceType());
            statement.setString(4, evidence.sourceRef());
            statement.setString(5, evidence.title());
            statement.setString(6, evidence.excerpt());
            statement.setString(7, evidence.trustLevel().name());
            statement.setString(8, json(evidence.metadata()));
            statement.executeUpdate();
        }
    }

    private void upsertProposal(Connection connection, AgentRun run, Proposal proposal) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO agent_proposal(id, run_id, proposal_kind, status, summary, payload, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, summary = EXCLUDED.summary,
                  payload = EXCLUDED.payload, updated_at = EXCLUDED.updated_at
                """)) {
            statement.setObject(1, proposal.id());
            statement.setObject(2, run.id());
            statement.setString(3, proposal.kind());
            statement.setString(4, proposal.status());
            statement.setString(5, proposal.summary());
            statement.setString(6, json(proposal.payload()));
            statement.setObject(7, dbTime(proposal.createdAt()));
            statement.setObject(8, dbTime(proposal.createdAt()));
            statement.executeUpdate();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Agent audit data", exception);
        }
    }

    private Connection connection() throws SQLException {
        if (properties.url().isBlank()) throw new IllegalStateException("Agent persistence database URL is required");
        return DriverManager.getConnection(properties.url(), properties.username(), properties.password());
    }

    private java.time.OffsetDateTime dbTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
