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
        String tsQuery = toTsQuery(query);
        if (tsQuery.isBlank()) return List.of();
        String sql = """
                SELECT ks.chunk_id, ks.existing_contract_id, sc.contract_name,
                       c.chunk_kind, c.symbol_name, c.signature, c.file_path,
                       c.line_start, c.line_end, left(c.content, 4000) AS excerpt,
                       c.content_hash, s.trust_level,
                       ts_rank_cd(ks.search_vector, to_tsquery('simple', ?)) AS text_score
                FROM knowledge_chunk_search ks
                JOIN knowledge_corpus_version cv ON cv.id = ks.corpus_version_id
                JOIN knowledge_chunk c ON c.id = ks.chunk_id
                JOIN knowledge_document d ON d.id = c.document_id
                JOIN knowledge_source s ON s.id = d.source_id
                LEFT JOIN smart_contract sc ON sc.id = ks.existing_contract_id
                WHERE cv.corpus_name = ? AND cv.version = ?
                  AND ks.search_vector @@ to_tsquery('simple', ?)
                ORDER BY text_score DESC, ks.existing_contract_id, c.ordinal
                LIMIT ?
                """;
        List<KnowledgeChunkMatch> matches = new ArrayList<>();
        AgentProperties.Knowledge knowledge = properties.knowledge();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tsQuery);
            statement.setString(2, knowledge.corpusName());
            statement.setString(3, knowledge.corpusVersion());
            statement.setString(4, tsQuery);
            statement.setInt(5, Math.max(1, Math.min(limit, 100)));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    matches.add(new KnowledgeChunkMatch(
                            resultSet.getObject("chunk_id", java.util.UUID.class),
                            resultSet.getLong("existing_contract_id"),
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
            return List.copyOf(matches);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Unable to search Knowledge chunks", exception);
        }
    }

    static String toTsQuery(String query) {
        Matcher matcher = QUERY_TOKEN.matcher(query == null ? "" : query);
        Set<String> tokens = new LinkedHashSet<>();
        while (matcher.find() && tokens.size() < 12) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return String.join(" | ", tokens);
    }

    private Connection connection() throws SQLException {
        AgentProperties.Knowledge knowledge = properties.knowledge();
        return DriverManager.getConnection(knowledge.databaseUrl(),
                knowledge.databaseUsername(), knowledge.databasePassword());
    }
}
