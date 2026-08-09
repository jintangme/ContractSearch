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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "agent.knowledge", name = "enabled", havingValue = "true")
public class JdbcKnowledgeIngestionStore implements KnowledgeIngestionStore {
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public JdbcKnowledgeIngestionStore(AgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public KnowledgeSourceBatch loadContracts(long afterId, int limit) {
        String sql = """
                SELECT id, contract_name, source_code
                FROM smart_contract
                WHERE id > ? AND COALESCE(is_deleted, false) = false
                  AND uploader_id IS NULL
                  AND source_code IS NOT NULL AND length(trim(source_code)) > 0
                ORDER BY id
                LIMIT ?
                """;
        List<ContractSourceRecord> rows = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, afterId);
            statement.setInt(2, limit + 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new ContractSourceRecord(resultSet.getLong("id"),
                            resultSet.getString("contract_name"), resultSet.getString("source_code")));
                }
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Unable to load contract sources", exception);
        }
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows.remove(rows.size() - 1);
        return new KnowledgeSourceBatch(rows, hasMore);
    }

    @Override
    public long resumeAfter(UUID jobId, long fallback) {
        if (jobId == null) return fallback;
        String sql = "SELECT checkpoint FROM knowledge_ingestion_job WHERE id = ?";
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, jobId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new KnowledgeStoreException("Unknown Knowledge ingestion job");
                String checkpoint = resultSet.getString(1);
                return checkpoint == null || checkpoint.isBlank() ? fallback : Long.parseLong(checkpoint);
            }
        } catch (SQLException | NumberFormatException exception) {
            throw new KnowledgeStoreException("Unable to resolve Knowledge ingestion checkpoint", exception);
        }
    }

    @Override
    public KnowledgeWriteResult persist(UUID requestedJobId, List<PlannedKnowledgeContract> plans,
                                        long checkpoint, boolean hasMore) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                UUID corpusId = ensureCorpus(connection);
                UUID jobId = ensureJob(connection, corpusId, requestedJobId);
                int sourceCount = 0;
                int documentCount = 0;
                int chunkCount = 0;
                int errorCount = 0;
                for (PlannedKnowledgeContract planned : plans) {
                    if (itemExists(connection, jobId, planned.source().id())) continue;
                    Persisted persisted = persistContract(connection, corpusId, jobId, planned);
                    sourceCount += persisted.sourceCount();
                    documentCount += persisted.documentCount();
                    chunkCount += persisted.chunkCount();
                    errorCount += persisted.errorCount();
                }
                updateJob(connection, jobId, checkpoint, hasMore, sourceCount,
                        documentCount, chunkCount, errorCount);
                connection.commit();
                return new KnowledgeWriteResult(jobId, sourceCount, documentCount, chunkCount, errorCount);
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof KnowledgeStoreException storeException) throw storeException;
                throw new KnowledgeStoreException("Unable to persist Knowledge ingestion batch", exception);
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Unable to open Knowledge ingestion transaction", exception);
        }
    }

    @Override
    public KnowledgeValidationResult validateCorpus() {
        try (Connection connection = connection()) {
            UUID corpusId = findCorpus(connection);
            if (corpusId == null) return new KnowledgeValidationResult(0, 0, 0, 0, 0, 0, 0, 0);
            long sources = scalar(connection,
                    "SELECT count(*) FROM knowledge_source WHERE corpus_version_id = ?", corpusId);
            long documents = scalar(connection, """
                    SELECT count(*) FROM knowledge_document d
                    JOIN knowledge_source s ON s.id = d.source_id
                    WHERE s.corpus_version_id = ?
                    """, corpusId);
            long chunks = scalar(connection, """
                    SELECT count(*) FROM knowledge_chunk c
                    JOIN knowledge_document d ON d.id = c.document_id
                    JOIN knowledge_source s ON s.id = d.source_id
                    WHERE s.corpus_version_id = ?
                    """, corpusId);
            long searchRows = scalar(connection, """
                    SELECT count(*) FROM knowledge_chunk_search WHERE corpus_version_id = ?
                    """, corpusId);
            long orphanDocuments = scalar(connection, """
                    SELECT count(*) FROM knowledge_document d
                    LEFT JOIN knowledge_source s ON s.id = d.source_id
                    WHERE s.id IS NULL
                    """, null);
            long orphanChunks = scalar(connection, """
                    SELECT count(*) FROM knowledge_chunk c
                    LEFT JOIN knowledge_document d ON d.id = c.document_id
                    WHERE d.id IS NULL
                    """, null);
            long missingChunkSearchRows = scalar(connection, """
                    SELECT count(*) FROM knowledge_chunk c
                    JOIN knowledge_document d ON d.id = c.document_id
                    JOIN knowledge_source s ON s.id = d.source_id
                    LEFT JOIN knowledge_chunk_search ks ON ks.chunk_id = c.id
                    WHERE s.corpus_version_id = ? AND ks.chunk_id IS NULL
                    """, corpusId);
            long duplicates = scalar(connection, """
                    SELECT count(*) FROM (
                      SELECT normalized_hash FROM knowledge_source
                      WHERE corpus_version_id = ?
                      GROUP BY normalized_hash HAVING count(*) > 1
                    ) duplicate_hashes
                    """, corpusId);
            return new KnowledgeValidationResult(sources, documents, chunks, searchRows,
                    orphanDocuments, orphanChunks, missingChunkSearchRows, duplicates);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Unable to validate Knowledge corpus", exception);
        }
    }

    private Persisted persistContract(Connection connection, UUID corpusId, UUID jobId,
                                      PlannedKnowledgeContract planned) throws SQLException {
        SourceManifest manifest = planned.plan().manifest();
        ExistingAlias alias = findAlias(connection, corpusId, planned.source().id());
        if (alias != null && !alias.rawHash().equals(manifest.rawHash())) {
            throw new KnowledgeStoreException("Contract source changed within an immutable corpus version: "
                    + planned.source().id());
        }
        UUID existingCanonical = alias == null
                ? findCanonicalSource(connection, corpusId, manifest.normalizedHash()) : alias.sourceId();
        UUID sourceId = existingCanonical == null ? UUID.randomUUID() : existingCanonical;
        boolean canonical = existingCanonical == null;
        if (canonical) insertSource(connection, corpusId, sourceId, manifest);
        insertAlias(connection, corpusId, sourceId, planned.source(), manifest.rawHash());

        int documents = 0;
        int chunks = 0;
        int errors = 0;
        if (canonical) {
            for (NormalizedSourceFile file : manifest.files()) {
                UUID documentId = deterministicId("document", sourceId + ":" + file.path()
                        + ":" + file.normalizedHash());
                documents += insertDocument(connection, documentId, sourceId, file, manifest);
                for (KnowledgeChunk chunk : planned.plan().chunks()) {
                    if (!file.path().equals(chunk.filePath())) continue;
                    chunks += insertChunk(connection, corpusId, planned.source().id(), documentId, chunk);
                }
            }
        }

        String status = canonical ? outcomeStatus(manifest) : "SKIPPED";
        String errorCode = null;
        if (manifest.parserStatus() != ParserStatus.PARSED) {
            errorCode = "SOURCE_" + manifest.parserStatus().name();
        } else if (planned.plan().chunker().contains("fallback")) {
            errorCode = "AST_FALLBACK";
        }
        if (errorCode != null) {
            insertError(connection, jobId, manifest.sourceRef(), errorCode,
                    planned.plan().diagnostics());
            errors = 1;
            if ("SUCCEEDED".equals(status)) status = "PARTIAL";
        }
        insertItem(connection, jobId, planned.source().id(), sourceId, status,
                documents, chunks, errorCode);
        return new Persisted(1, documents, chunks, errors);
    }

    private UUID ensureCorpus(Connection connection) throws SQLException {
        UUID existing = findCorpus(connection);
        if (existing != null) return existing;
        AgentProperties.Knowledge knowledge = properties.knowledge();
        UUID id = deterministicId("corpus", knowledge.corpusName() + ":" + knowledge.corpusVersion());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_corpus_version(id, corpus_name, version, status, configuration)
                VALUES (?, ?, ?, 'BUILDING', '{"chunker":"solc-ast-v1"}'::jsonb)
                ON CONFLICT (corpus_name, version) DO NOTHING
                """)) {
            statement.setObject(1, id);
            statement.setString(2, knowledge.corpusName());
            statement.setString(3, knowledge.corpusVersion());
            statement.executeUpdate();
        }
        UUID resolved = findCorpus(connection);
        if (resolved == null) throw new KnowledgeStoreException("Unable to create Knowledge corpus version");
        return resolved;
    }

    private UUID findCorpus(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM knowledge_corpus_version WHERE corpus_name = ? AND version = ?
                """)) {
            statement.setString(1, properties.knowledge().corpusName());
            statement.setString(2, properties.knowledge().corpusVersion());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getObject(1, UUID.class) : null;
            }
        }
    }

    private UUID ensureJob(Connection connection, UUID corpusId, UUID requestedJobId) throws SQLException {
        UUID jobId = requestedJobId == null ? UUID.randomUUID() : requestedJobId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_ingestion_job(id, corpus_version_id, status, mode, started_at)
                VALUES (?, ?, 'RUNNING', 'INCREMENTAL', CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """)) {
            statement.setObject(1, jobId);
            statement.setObject(2, corpusId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM knowledge_ingestion_job WHERE id = ? AND corpus_version_id = ?
                """)) {
            statement.setObject(1, jobId);
            statement.setObject(2, corpusId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new KnowledgeStoreException("Ingestion job belongs to another corpus");
            }
        }
        return jobId;
    }

    private UUID findCanonicalSource(Connection connection, UUID corpusId, String normalizedHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM knowledge_source
                WHERE corpus_version_id = ? AND normalized_hash = ?
                ORDER BY created_at, id LIMIT 1
                """)) {
            statement.setObject(1, corpusId);
            statement.setString(2, normalizedHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getObject(1, UUID.class) : null;
            }
        }
    }

    private ExistingAlias findAlias(Connection connection, UUID corpusId, long contractId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT source_id, raw_hash FROM knowledge_source_alias
                WHERE corpus_version_id = ? AND existing_contract_id = ?
                """)) {
            statement.setObject(1, corpusId);
            statement.setLong(2, contractId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? new ExistingAlias(resultSet.getObject(1, UUID.class), resultSet.getString(2)) : null;
            }
        }
    }

    private void insertSource(Connection connection, UUID corpusId, UUID sourceId,
                              SourceManifest manifest) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_source(
                  id, corpus_version_id, source_kind, source_ref, existing_contract_id,
                  trust_level, visibility, raw_hash, normalized_hash, metadata)
                VALUES (?, ?, 'CONTRACT_LIBRARY', ?, ?, 'COMMUNITY', 'PUBLIC', ?, ?, ?::jsonb)
                ON CONFLICT (corpus_version_id, source_ref, raw_hash) DO NOTHING
                """)) {
            statement.setObject(1, sourceId);
            statement.setObject(2, corpusId);
            statement.setString(3, manifest.sourceRef());
            statement.setObject(4, manifest.existingContractId());
            statement.setString(5, manifest.rawHash());
            statement.setString(6, manifest.normalizedHash());
            statement.setString(7, json(Map.of("sourceFormat", manifest.format().name())));
            statement.executeUpdate();
        }
    }

    private void insertAlias(Connection connection, UUID corpusId, UUID sourceId,
                             ContractSourceRecord source, String rawHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_source_alias(
                  corpus_version_id, source_id, existing_contract_id, source_ref, raw_hash)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (corpus_version_id, existing_contract_id) DO NOTHING
                """)) {
            statement.setObject(1, corpusId);
            statement.setObject(2, sourceId);
            statement.setLong(3, source.id());
            statement.setString(4, "contract:" + source.id());
            statement.setString(5, rawHash);
            statement.executeUpdate();
        }
    }

    private int insertDocument(Connection connection, UUID documentId, UUID sourceId,
                               NormalizedSourceFile file, SourceManifest manifest) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_document(
                  id, source_id, document_kind, language, source_format, parser_status,
                  file_path, raw_content, normalized_content, raw_hash, normalized_hash, metadata)
                VALUES (?, ?, 'SOLIDITY_SOURCE', 'solidity', ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb)
                ON CONFLICT (source_id, file_path) DO NOTHING
                """)) {
            statement.setObject(1, documentId);
            statement.setObject(2, sourceId);
            statement.setString(3, manifest.format().name());
            statement.setString(4, manifest.parserStatus().name());
            statement.setString(5, file.path());
            statement.setString(6, file.rawContent());
            statement.setString(7, file.normalizedContent());
            statement.setString(8, file.rawHash());
            statement.setString(9, file.normalizedHash());
            return statement.executeUpdate();
        }
    }

    private int insertChunk(Connection connection, UUID corpusId, long contractId,
                            UUID documentId, KnowledgeChunk chunk) throws SQLException {
        UUID chunkId = deterministicId("chunk", documentId + ":" + chunk.ordinal()
                + ":" + chunk.contentHash());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_chunk(
                  id, document_id, ordinal, chunk_kind, symbol_name, signature, file_path,
                  line_start, line_end, char_start, char_end, content, content_hash, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (document_id, ordinal) DO NOTHING
                """)) {
            statement.setObject(1, chunkId);
            statement.setObject(2, documentId);
            statement.setInt(3, chunk.ordinal());
            statement.setString(4, chunk.kind().name());
            statement.setString(5, chunk.symbolName());
            statement.setString(6, chunk.signature());
            statement.setString(7, chunk.filePath());
            statement.setInt(8, chunk.lineStart());
            statement.setInt(9, chunk.lineEnd());
            statement.setInt(10, chunk.charStart());
            statement.setInt(11, chunk.charEnd());
            statement.setString(12, chunk.content());
            statement.setString(13, chunk.contentHash());
            statement.setString(14, json(chunk.metadata()));
            int inserted = statement.executeUpdate();
            insertChunkSearch(connection, chunkId, corpusId, contractId, chunk);
            return inserted;
        }
    }

    private void insertChunkSearch(Connection connection, UUID chunkId, UUID corpusId,
                                   long contractId, KnowledgeChunk chunk) throws SQLException {
        String searchText = String.join("\n",
                chunk.symbolName() == null ? "" : chunk.symbolName(),
                chunk.signature() == null ? "" : chunk.signature(),
                chunk.content());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_chunk_search(
                  chunk_id, corpus_version_id, existing_contract_id, search_text)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (chunk_id) DO NOTHING
                """)) {
            statement.setObject(1, chunkId);
            statement.setObject(2, corpusId);
            statement.setLong(3, contractId);
            statement.setString(4, searchText);
            statement.executeUpdate();
        }
    }

    private boolean itemExists(Connection connection, UUID jobId, long contractId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM knowledge_ingestion_item WHERE job_id = ? AND existing_contract_id = ?
                """)) {
            statement.setObject(1, jobId);
            statement.setLong(2, contractId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void insertItem(Connection connection, UUID jobId, long contractId, UUID sourceId,
                            String status, int documents, int chunks, String errorCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_ingestion_item(
                  job_id, existing_contract_id, source_id, status, document_count, chunk_count, error_code)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (job_id, existing_contract_id) DO NOTHING
                """)) {
            statement.setObject(1, jobId);
            statement.setLong(2, contractId);
            statement.setObject(3, sourceId);
            statement.setString(4, status);
            statement.setInt(5, documents);
            statement.setInt(6, chunks);
            statement.setString(7, errorCode);
            statement.executeUpdate();
        }
    }

    private void insertError(Connection connection, UUID jobId, String sourceRef,
                             String errorCode, List<String> diagnostics) throws SQLException {
        String message = diagnostics.isEmpty() ? errorCode : String.join(" | ", diagnostics);
        if (message.length() > 4000) message = message.substring(0, 4000);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_ingestion_error(
                  job_id, source_ref, stage, error_code, retryable, sanitized_message)
                VALUES (?, ?, 'PARSE_AND_CHUNK', ?, false, ?)
                """)) {
            statement.setObject(1, jobId);
            statement.setString(2, sourceRef);
            statement.setString(3, errorCode);
            statement.setString(4, message);
            statement.executeUpdate();
        }
    }

    private void updateJob(Connection connection, UUID jobId, long checkpoint, boolean hasMore,
                           int sources, int documents, int chunks, int errors) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE knowledge_ingestion_job
                SET checkpoint = ?, source_count = source_count + ?,
                    document_count = document_count + ?, chunk_count = chunk_count + ?,
                    error_count = error_count + ?,
                    status = CASE WHEN ? THEN 'RUNNING'
                                  WHEN error_count + ? > 0 THEN 'PARTIAL'
                                  ELSE 'SUCCEEDED' END,
                    completed_at = CASE WHEN ? THEN NULL ELSE CURRENT_TIMESTAMP END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)) {
            statement.setString(1, Long.toString(checkpoint));
            statement.setInt(2, sources);
            statement.setInt(3, documents);
            statement.setInt(4, chunks);
            statement.setInt(5, errors);
            statement.setBoolean(6, hasMore);
            statement.setInt(7, errors);
            statement.setBoolean(8, hasMore);
            statement.setObject(9, jobId);
            statement.executeUpdate();
        }
    }

    private long scalar(Connection connection, String sql, UUID corpusId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (corpusId != null) statement.setObject(1, corpusId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private String outcomeStatus(SourceManifest manifest) {
        return switch (manifest.parserStatus()) {
            case PARSED -> "SUCCEEDED";
            case PARTIAL -> "PARTIAL";
            case INVALID -> "FAILED";
        };
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new KnowledgeStoreException("Unable to serialize Knowledge metadata", exception);
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

    private record Persisted(int sourceCount, int documentCount, int chunkCount, int errorCount) {
    }

    private record ExistingAlias(UUID sourceId, String rawHash) {
    }
}
