package org.scse.cloudagent.knowledge;

import org.scse.cloudagent.config.AgentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
@ConditionalOnProperty(prefix = "agent.knowledge", name = "enabled", havingValue = "true")
public class JdbcKnowledgeRetrievalRepository implements KnowledgeRetrievalRepository {
    private static final Pattern QUERY_TOKEN = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{1,63}|[0-9]{2,}");
    private final AgentProperties properties;

    public JdbcKnowledgeRetrievalRepository(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<KnowledgeChunkMatch> searchChunks(String query, int limit) {
        List<String> tokens = tokens(query);
        String tsQuery = String.join(" | ", tokens);
        if (tsQuery.isBlank()) return List.of();
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        try (Connection connection = connection()) {
            List<KnowledgeChunkMatch> matches = new ArrayList<>();
            matches.addAll(searchChannel(connection, tsQuery, tokens, boundedLimit, true));
            matches.addAll(searchChannel(connection, tsQuery, tokens, boundedLimit, false));
            return List.copyOf(matches);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Unable to search Knowledge chunks", exception);
        }
    }

    private List<KnowledgeChunkMatch> searchChannel(Connection connection, String tsQuery,
                                                     List<String> tokens,
                                                     int limit, boolean authoritative) throws SQLException {
        String scope = authoritative
                ? "ks.existing_contract_id IS NULL"
                : "ks.existing_contract_id IS NOT NULL";
        String sql = """
                SELECT ks.chunk_id, s.id AS source_id, s.source_kind, s.source_ref,
                       s.metadata->>'title' AS source_title,
                       s.metadata->>'sourceUrl' AS source_url,
                       ks.existing_contract_id, sc.contract_name,
                       c.chunk_kind, c.symbol_name, c.signature, c.file_path,
                       c.line_start, c.line_end, left(c.content, 4000) AS excerpt,
                       c.content_hash, s.trust_level,
                       EXISTS (
                         SELECT 1 FROM unnest(?::text[]) AS query_token(token)
                         WHERE lower(regexp_replace(d.file_path, '[^A-Za-z0-9_$]', '', 'g'))
                               LIKE '%%' || query_token.token
                       ) AS exact_source_key_match,
                       (SELECT count(*) FROM unnest(?::text[]) AS query_token(token)
                        WHERE to_tsvector('simple', concat_ws(' ', s.source_ref, s.metadata->>'title'))
                              @@ plainto_tsquery('simple', query_token.token)) AS source_coverage,
                       (SELECT count(*) FROM unnest(?::text[]) AS query_token(token)
                        WHERE ks.search_vector @@ plainto_tsquery('simple', query_token.token)) AS token_coverage,
                       ts_rank_cd(ks.search_vector, to_tsquery('simple', ?)) AS text_score
                FROM knowledge_chunk_search ks
                JOIN knowledge_corpus_version cv ON cv.id = ks.corpus_version_id
                JOIN knowledge_chunk c ON c.id = ks.chunk_id
                JOIN knowledge_document d ON d.id = c.document_id
                JOIN knowledge_source s ON s.id = d.source_id
                LEFT JOIN smart_contract sc ON sc.id = ks.existing_contract_id
                WHERE cv.corpus_name = ? AND cv.version = ?
                  AND ks.search_vector @@ to_tsquery('simple', ?)
                  AND %s
                ORDER BY exact_source_key_match DESC, source_coverage DESC,
                         token_coverage DESC, text_score DESC,
                         ks.existing_contract_id, c.ordinal
                LIMIT ?
                """.formatted(scope);
        List<KnowledgeChunkMatch> matches = new ArrayList<>();
        AgentProperties.Knowledge knowledge = properties.knowledge();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, connection.createArrayOf("text", tokens.toArray()));
            statement.setArray(2, connection.createArrayOf("text", tokens.toArray()));
            statement.setArray(3, connection.createArrayOf("text", tokens.toArray()));
            statement.setString(4, tsQuery);
            statement.setString(5, knowledge.corpusName());
            statement.setString(6, knowledge.corpusVersion());
            statement.setString(7, tsQuery);
            statement.setInt(8, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    matches.add(new KnowledgeChunkMatch(
                            resultSet.getObject("chunk_id", java.util.UUID.class),
                            resultSet.getObject("source_id", java.util.UUID.class),
                            resultSet.getString("source_kind"),
                            resultSet.getString("source_ref"),
                            resultSet.getString("source_title"),
                            resultSet.getString("source_url"),
                            resultSet.getObject("existing_contract_id", Long.class),
                            resultSet.getString("contract_name"),
                            resultSet.getString("chunk_kind"),
                            resultSet.getString("symbol_name"),
                            resultSet.getString("signature"),
                            resultSet.getString("file_path"),
                            resultSet.getInt("line_start"),
                            resultSet.getInt("line_end"),
                            resultSet.getString("excerpt"),
                            resultSet.getString("content_hash"),
                            resultSet.getString("trust_level"),
                            resultSet.getDouble("text_score")
                    ));
                }
            }
        }
        return List.copyOf(matches);
    }

    static String toTsQuery(String query) {
        return String.join(" | ", tokens(query));
    }

    private static List<String> tokens(String query) {
        Matcher matcher = QUERY_TOKEN.matcher(query == null ? "" : query);
        Set<String> tokens = new LinkedHashSet<>();
        while (matcher.find() && tokens.size() < 12) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(tokens);
    }

    private Connection connection() throws SQLException {
        AgentProperties.Knowledge knowledge = properties.knowledge();
        return DriverManager.getConnection(knowledge.databaseUrl(),
                knowledge.databaseUsername(), knowledge.databasePassword());
    }
}
