package org.scse.cloudagent.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.scse.cloudagent.config.AgentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "agent.knowledge", name = "enabled", havingValue = "true")
public class JdbcReferenceKnowledgeStore {
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final ReferenceKnowledgeChunker chunker;

    public JdbcReferenceKnowledgeStore(AgentProperties properties, ObjectMapper objectMapper,
                                       ReferenceKnowledgeChunker chunker) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.chunker = chunker;
    }

    public ReferenceKnowledgeIngestionResponse persist(List<ReferenceKnowledgeDocument> documents) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                UUID corpusId = requireCorpus(connection);
                int sources = 0;
                int persistedDocuments = 0;
                int persistedChunks = 0;
                int skipped = 0;
                List<Map<String, Object>> outcomes = new ArrayList<>();
                for (ReferenceKnowledgeDocument input : documents) {
                    Persisted result = persistOne(connection, corpusId, input);
                    sources += result.sources();
                    persistedDocuments += result.documents();
                    persistedChunks += result.chunks();
                    skipped += result.skipped();
                    outcomes.add(Map.of(
                            "sourceKey", input.sourceKey(),
                            "status", result.skipped() == 1 ? "SKIPPED" : "SUCCEEDED",
                            "chunks", result.chunks()));
                }
                connection.commit();
                return new ReferenceKnowledgeIngestionResponse(documents.size(), sources,
                        persistedDocuments, persistedChunks, skipped, outcomes);
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof KnowledgeStoreException storeException) throw storeException;
                throw new KnowledgeStoreException("Unable to persist authoritative Knowledge batch", exception);
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Unable to open authoritative Knowledge transaction", exception);
        }
    }

    private Persisted persistOne(Connection connection, UUID corpusId,
                                 ReferenceKnowledgeDocument input) throws SQLException {
        String normalized = ReferenceKnowledgeChunker.normalize(input.content());
        String sourceRef = "reference:" + input.sourceKey() + ":" + input.version();
        UUID existing = findSource(connection, corpusId, sourceRef, input.contentHash());
        if (existing != null) {
            refreshSearchProjection(connection, existing, input);
            return new Persisted(0, 0, 0, 1);
        }
        if (sourceRefExists(connection, corpusId, sourceRef)) {
            throw new KnowledgeStoreException("Reference changed within immutable corpus: " + sourceRef);
        }

        UUID sourceId = deterministicId("reference-source", corpusId + ":" + sourceRef + ":" + input.contentHash());
        UUID documentId = deterministicId("reference-document", sourceId + ":" + input.contentHash());
        insertSource(connection, corpusId, sourceId, sourceRef, input);
        insertDocument(connection, documentId, sourceId, normalized, input);
        List<ReferenceKnowledgeChunk> chunks = chunker.chunk(input.title(), normalized);
        for (ReferenceKnowledgeChunk chunk : chunks) {
            insertChunk(connection, corpusId, sourceId, documentId, input, chunk);
        }
        return new Persisted(1, 1, chunks.size(), 0);
    }

    private void insertSource(Connection connection, UUID corpusId, UUID sourceId, String sourceRef,
                              ReferenceKnowledgeDocument input) throws SQLException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", input.title());
        metadata.put("sourceUrl", input.sourceUrl());
        metadata.put("version", input.version());
        metadata.put("license", input.license());
        metadata.put("fetchedAt", input.fetchedAt().toString());
        metadata.put("contentBoundary", "UNTRUSTED_REFERENCE_CONTENT");
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_source(
                  id, corpus_version_id, source_kind, source_ref, existing_contract_id,
                  trust_level, visibility, raw_hash, normalized_hash, metadata)
                VALUES (?, ?, ?, ?, NULL, 'AUTHORITATIVE', 'PUBLIC', ?, ?, ?::jsonb)
                """)) {
            statement.setObject(1, sourceId);
            statement.setObject(2, corpusId);
            statement.setString(3, input.sourceKind().toUpperCase(Locale.ROOT));
            statement.setString(4, sourceRef);
            statement.setString(5, input.contentHash().toLowerCase(Locale.ROOT));
            statement.setString(6, input.contentHash().toLowerCase(Locale.ROOT));
            statement.setString(7, json(metadata));
            statement.executeUpdate();
        }
    }

    private void insertDocument(Connection connection, UUID documentId, UUID sourceId, String normalized,
                                ReferenceKnowledgeDocument input) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_document(
                  id, source_id, document_kind, language, source_format, parser_status,
                  file_path, raw_content, normalized_content, raw_hash, normalized_hash, metadata)
                VALUES (?, ?, 'AUTHORITATIVE_REFERENCE', 'en', ?, 'PARSED', ?, ?, ?, ?, ?, ?::jsonb)
                """)) {
            statement.setObject(1, documentId);
            statement.setObject(2, sourceId);
            statement.setString(3, input.format().toUpperCase(Locale.ROOT));
            statement.setString(4, input.sourceKey());
            statement.setString(5, input.content());
            statement.setString(6, normalized);
            statement.setString(7, input.contentHash().toLowerCase(Locale.ROOT));
            statement.setString(8, input.contentHash().toLowerCase(Locale.ROOT));
            statement.setString(9, json(Map.of("title", input.title(), "sourceUrl", input.sourceUrl())));
            statement.executeUpdate();
        }
    }

    private void insertChunk(Connection connection, UUID corpusId, UUID sourceId, UUID documentId,
                             ReferenceKnowledgeDocument input, ReferenceKnowledgeChunk chunk) throws SQLException {
        UUID chunkId = deterministicId("reference-chunk", documentId + ":" + chunk.ordinal()
                + ":" + chunk.contentHash());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_chunk(
                  id, document_id, ordinal, chunk_kind, symbol_name, signature, file_path,
                  line_start, line_end, char_start, char_end, content, content_hash, metadata)
                VALUES (?, ?, ?, 'SECTION', ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """)) {
            statement.setObject(1, chunkId);
            statement.setObject(2, documentId);
            statement.setInt(3, chunk.ordinal());
            statement.setString(4, chunk.heading());
            statement.setString(5, input.sourceKey());
            statement.setInt(6, chunk.lineStart());
            statement.setInt(7, chunk.lineEnd());
            statement.setInt(8, chunk.charStart());
            statement.setInt(9, chunk.charEnd());
            statement.setString(10, chunk.content());
            statement.setString(11, chunk.contentHash());
            statement.setString(12, json(Map.of(
                    "heading", chunk.heading(), "sourceUrl", input.sourceUrl(),
                    "sourceId", sourceId.toString())));
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_chunk_search(
                  chunk_id, corpus_version_id, existing_contract_id, search_text)
                VALUES (?, ?, NULL, ?)
                """)) {
            statement.setObject(1, chunkId);
            statement.setObject(2, corpusId);
            statement.setString(3, searchText(input, chunk.heading(), chunk.content()));
            statement.executeUpdate();
        }
    }

    private void refreshSearchProjection(Connection connection, UUID sourceId,
                                         ReferenceKnowledgeDocument input) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE knowledge_chunk_search ks
                SET search_text = concat_ws(E'\\n', ?, ?, ?, c.symbol_name, c.content)
                FROM knowledge_chunk c
                JOIN knowledge_document d ON d.id = c.document_id
                WHERE ks.chunk_id = c.id AND d.source_id = ?
                """)) {
            statement.setString(1, input.sourceKey());
            statement.setString(2, compactAlias(input.sourceKey()));
            statement.setString(3, input.title());
            statement.setObject(4, sourceId);
            statement.executeUpdate();
        }
    }

    private String searchText(ReferenceKnowledgeDocument input, String heading, String content) {
        return String.join("\n", input.sourceKey(), compactAlias(input.sourceKey()),
                input.title(), heading, content);
    }

    private String compactAlias(String value) {
        return value.replaceAll("[^A-Za-z0-9_$]", "");
    }

    private UUID requireCorpus(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM knowledge_corpus_version WHERE corpus_name = ? AND version = ?
                """)) {
            statement.setString(1, properties.knowledge().corpusName());
            statement.setString(2, properties.knowledge().corpusVersion());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new KnowledgeStoreException("Configured Knowledge corpus does not exist");
                return resultSet.getObject(1, UUID.class);
            }
        }
    }

    private UUID findSource(Connection connection, UUID corpusId, String sourceRef, String hash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM knowledge_source
                WHERE corpus_version_id = ? AND source_ref = ? AND raw_hash = ?
                """)) {
            statement.setObject(1, corpusId);
            statement.setString(2, sourceRef);
            statement.setString(3, hash.toLowerCase(Locale.ROOT));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getObject(1, UUID.class) : null;
            }
        }
    }

    private boolean sourceRefExists(Connection connection, UUID corpusId, String sourceRef) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM knowledge_source WHERE corpus_version_id = ? AND source_ref = ?
                """)) {
            statement.setObject(1, corpusId);
            statement.setString(2, sourceRef);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new KnowledgeStoreException("Unable to serialize reference metadata", exception);
        }
    }

    private UUID deterministicId(String namespace, String value) {
        return UUID.nameUUIDFromBytes((namespace + ":" + value).getBytes(StandardCharsets.UTF_8));
    }

    private Connection connection() throws SQLException {
        AgentProperties.Knowledge knowledge = properties.knowledge();
        return DriverManager.getConnection(knowledge.databaseUrl(),
                knowledge.databaseUsername(), knowledge.databasePassword());
    }

    private record Persisted(int sources, int documents, int chunks, int skipped) {
    }
}
