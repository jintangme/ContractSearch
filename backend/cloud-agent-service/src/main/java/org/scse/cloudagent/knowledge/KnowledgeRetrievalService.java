package org.scse.cloudagent.knowledge;

import org.scse.cloudagent.tool.ContractCatalogClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "agent.knowledge", name = "enabled", havingValue = "true")
public class KnowledgeRetrievalService {
    private static final int RRF_K = 60;
    private final KnowledgeRetrievalRepository repository;
    private final ContractCatalogClient catalogClient;

    public KnowledgeRetrievalService(KnowledgeRetrievalRepository repository,
                                     ContractCatalogClient catalogClient) {
        this.repository = repository;
        this.catalogClient = catalogClient;
    }

    public KnowledgeRetrievalResponse retrieve(String query, int limit, long userId) {
        int boundedLimit = Math.max(1, Math.min(limit, 10));
        List<String> degraded = new ArrayList<>();
        List<Map<String, Object>> semantic = safeCatalogSearch(query, "semantic", userId, degraded);
        List<Map<String, Object>> code = safeCatalogSearch(query, "code", userId, degraded);
        List<KnowledgeChunkMatch> chunks;
        try {
            chunks = repository.searchChunks(query, Math.min(50, boundedLimit * 5));
        } catch (RuntimeException exception) {
            chunks = List.of();
            degraded.add("chunk_text");
        }

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        addCatalogChannel(candidates, semantic, "semantic", 0);
        addCatalogChannel(candidates, code, "code", 1);
        int authoritativeRank = 0;
        int contractChunkRank = 0;
        for (KnowledgeChunkMatch chunk : chunks) {
            boolean authoritative = chunk.contractId() == null;
            int rank = authoritative ? ++authoritativeRank : ++contractChunkRank;
            String channel = authoritative ? "authoritativeText" : "contractChunkText";
            String key = chunk.contractId() == null
                    ? "source:" + chunk.sourceId() : "contract:" + chunk.contractId();
            Candidate candidate = candidates.computeIfAbsent(key,
                    ignored -> new Candidate(chunk.contractId()));
            if (!candidate.channelRanks.containsKey(channel)) {
                candidate.score += reciprocal(rank);
                candidate.channelRanks.put(channel, rank);
            }
            candidate.mergeSource(chunk);
            if (candidate.contractName == null) candidate.contractName = chunk.contractName();
            if (candidate.chunks.size() < 3) candidate.chunks.add(chunkMap(chunk));
        }

        List<Candidate> ranked = candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate item) -> item.score).reversed()
                        .thenComparing(Candidate::stableKey))
                .toList();
        List<Map<String, Object>> results = diversify(ranked, boundedLimit).stream()
                .map(Candidate::toMap)
                .toList();
        return new KnowledgeRetrievalResponse(query, results, degraded.stream().distinct().toList());
    }

    private List<Candidate> diversify(List<Candidate> ranked, int limit) {
        List<Candidate> selected = new ArrayList<>(ranked.stream().limit(limit).toList());
        if (limit < 2 || ranked.isEmpty()) return selected;
        ensureKind(selected, ranked, true, limit);
        ensureKind(selected, ranked, false, limit);
        return selected;
    }

    private void ensureKind(List<Candidate> selected, List<Candidate> ranked,
                            boolean reference, int limit) {
        if (selected.stream().anyMatch(item -> item.isReference() == reference)) return;
        Candidate candidate = ranked.stream().filter(item -> item.isReference() == reference)
                .findFirst().orElse(null);
        if (candidate == null || selected.contains(candidate)) return;
        if (selected.size() < limit) selected.add(candidate);
        else selected.set(selected.size() - 1, candidate);
    }

    private List<Map<String, Object>> safeCatalogSearch(String query, String mode, long userId,
                                                         List<String> degraded) {
        try {
            return catalogClient.search(query, mode, 10, userId);
        } catch (RuntimeException exception) {
            degraded.add("contract_" + mode);
            return List.of();
        }
    }

    private void addCatalogChannel(Map<String, Candidate> candidates, List<Map<String, Object>> results,
                                   String channel, int channelOrder) {
        for (int rank = 0; rank < results.size(); rank++) {
            Map<String, Object> result = results.get(rank);
            Long contractId = asLong(result.get("id"));
            if (contractId == null) continue;
            Candidate candidate = candidates.computeIfAbsent("contract:" + contractId,
                    ignored -> new Candidate(contractId));
            candidate.score += reciprocal(rank + 1);
            candidate.channelRanks.put(channel, rank + 1);
            candidate.catalogOrder = Math.min(candidate.catalogOrder, channelOrder);
            candidate.mergeCatalog(result);
        }
    }

    private double reciprocal(int rank) {
        return 1.0 / (RRF_K + rank);
    }

    private Map<String, Object> chunkMap(KnowledgeChunkMatch chunk) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chunkId", chunk.chunkId().toString());
        result.put("sourceId", chunk.sourceId().toString());
        put(result, "sourceKind", chunk.sourceKind());
        put(result, "sourceRef", chunk.sourceRef());
        put(result, "sourceTitle", chunk.sourceTitle());
        put(result, "sourceUrl", chunk.sourceUrl());
        put(result, "kind", chunk.chunkKind());
        put(result, "symbol", chunk.symbolName());
        put(result, "signature", chunk.signature());
        put(result, "filePath", chunk.filePath());
        result.put("lineStart", chunk.lineStart());
        result.put("lineEnd", chunk.lineEnd());
        put(result, "excerpt", chunk.excerpt());
        put(result, "contentHash", chunk.contentHash());
        put(result, "trustLevel", chunk.trustLevel());
        result.put("textScore", chunk.textScore());
        return Map.copyOf(result);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value != null && !value.toString().isBlank()) map.put(key, value);
    }

    private static final class Candidate {
        private final Long contractId;
        private UUID sourceId;
        private String sourceKind;
        private String sourceRef;
        private String sourceTitle;
        private String sourceUrl;
        private String trustLevel;
        private double score;
        private int catalogOrder = Integer.MAX_VALUE;
        private String contractName;
        private String category;
        private String description;
        private String compilerVersion;
        private final Map<String, Integer> channelRanks = new LinkedHashMap<>();
        private final List<Map<String, Object>> chunks = new ArrayList<>();

        private Candidate(Long contractId) {
            this.contractId = contractId;
        }

        private void mergeSource(KnowledgeChunkMatch chunk) {
            if (sourceId == null) sourceId = chunk.sourceId();
            if (sourceKind == null) sourceKind = chunk.sourceKind();
            if (sourceRef == null) sourceRef = chunk.sourceRef();
            if (sourceTitle == null) sourceTitle = chunk.sourceTitle();
            if (sourceUrl == null) sourceUrl = chunk.sourceUrl();
            if (trustLevel == null) trustLevel = chunk.trustLevel();
        }

        private boolean isReference() {
            return contractId == null;
        }

        private String stableKey() {
            return contractId == null ? "source:" + sourceId : "contract:" + contractId;
        }

        private void mergeCatalog(Map<String, Object> result) {
            if (contractName == null) contractName = text(result.get("contractName"));
            if (category == null) category = text(result.get("category"));
            if (description == null) description = text(result.get("description"));
            if (compilerVersion == null) compilerVersion = text(result.get("compilerVersion"));
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            if (contractId != null) result.put("contractId", contractId);
            if (sourceId != null) result.put("sourceId", sourceId.toString());
            putValue(result, "sourceKind", sourceKind);
            putValue(result, "sourceRef", sourceRef);
            putValue(result, "sourceTitle", sourceTitle);
            putValue(result, "sourceUrl", sourceUrl);
            putValue(result, "trustLevel", trustLevel);
            putValue(result, "contractName", contractName);
            putValue(result, "category", category);
            putValue(result, "description", description);
            putValue(result, "compilerVersion", compilerVersion);
            result.put("rrfScore", score);
            result.put("channelRanks", Map.copyOf(channelRanks));
            result.put("chunks", List.copyOf(chunks));
            return Map.copyOf(result);
        }

        private static String text(Object value) {
            return value == null || value.toString().isBlank() ? null : value.toString();
        }

        private static void putValue(Map<String, Object> map, String key, Object value) {
            if (value != null) map.put(key, value);
        }
    }
}
