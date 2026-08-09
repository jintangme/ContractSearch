package org.scse.cloudagent.knowledge;

import org.junit.jupiter.api.Test;
import org.scse.cloudagent.tool.ContractCatalogClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalServiceTest {
    @Test
    void fusesCatalogAndChunkChannelsAndKeepsEvidenceSpans() {
        ContractCatalogClient catalog = mock(ContractCatalogClient.class);
        when(catalog.search("ERC20 mint", "semantic", 10, 1L)).thenReturn(List.of(
                Map.of("id", 7L, "contractName", "RewardToken", "description", "ERC20 reward token"),
                Map.of("id", 8L, "contractName", "Other")));
        when(catalog.search("ERC20 mint", "code", 10, 1L)).thenReturn(List.of(
                Map.of("id", 9L, "contractName", "MintOnly")));
        KnowledgeRetrievalRepository repository = (query, limit) -> List.of(
                contractChunk(7L, "RewardToken", "FUNCTION", "mint",
                        "function mint(address,uint256)", "RewardToken.sol", 20, 24,
                        "function mint(address to, uint256 amount) onlyOwner", "abc", "COMMUNITY", 0.9),
                contractChunk(7L, "RewardToken", "FUNCTION", "_mint",
                        "function _mint(address,uint256)", "RewardToken.sol", 26, 30,
                        "function _mint(address to, uint256 amount)", "def", "COMMUNITY", 0.8));

        KnowledgeRetrievalResponse response = new KnowledgeRetrievalService(repository, catalog)
                .retrieve("ERC20 mint", 3, 1L);

        assertThat(response.results()).hasSize(3);
        assertThat(response.results().get(0).get("contractId")).isEqualTo(7L);
        assertThat(response.results().get(0).get("channelRanks"))
                .isEqualTo(Map.of("semantic", 1, "contractChunkText", 1));
        assertThat((Double) response.results().get(0).get("rrfScore"))
                .isEqualTo(2.0 / 61.0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) response.results().get(0).get("chunks");
        assertThat(chunks.get(0)).containsEntry("symbol", "mint")
                .containsEntry("lineStart", 20)
                .containsEntry("lineEnd", 24);
        assertThat(response.degradedChannels()).isEmpty();
    }

    @Test
    void createsSafeOrQueryForCodeSymbols() {
        assertThat(JdbcKnowledgeRetrievalRepository.toTsQuery("ERC20 mint onlyOwner ERC20"))
                .isEqualTo("erc20 | mint | onlyowner");
        assertThat(JdbcKnowledgeRetrievalRepository.toTsQuery("积分 权限")).isEmpty();
    }

    @Test
    void stillReturnsChunkEvidenceWhenCatalogSearchIsUnavailable() {
        ContractCatalogClient catalog = mock(ContractCatalogClient.class);
        when(catalog.search("nonReentrant", "semantic", 10, 1L))
                .thenThrow(new IllegalStateException("embedding unavailable"));
        when(catalog.search("nonReentrant", "code", 10, 1L))
                .thenThrow(new IllegalStateException("catalog unavailable"));
        KnowledgeRetrievalRepository repository = (query, limit) -> List.of(
                contractChunk(42L, "Vault", "FUNCTION", "withdraw",
                        "function withdraw()", "Vault.sol", 30, 40,
                        "function withdraw() nonReentrant", "abc", "COMMUNITY", 0.9));

        KnowledgeRetrievalResponse response = new KnowledgeRetrievalService(repository, catalog)
                .retrieve("nonReentrant", 3, 1L);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).get("contractId")).isEqualTo(42L);
        assertThat(response.degradedChannels())
                .containsExactly("contract_semantic", "contract_code");
    }

    @Test
    void keepsAnAuthoritativeReferenceAlongsideContractCandidates() {
        ContractCatalogClient catalog = mock(ContractCatalogClient.class);
        when(catalog.search("ERC20", "semantic", 10, 1L)).thenReturn(List.of(
                Map.of("id", 1L, "contractName", "TokenA"),
                Map.of("id", 2L, "contractName", "TokenB")));
        when(catalog.search("ERC20", "code", 10, 1L)).thenReturn(List.of(
                Map.of("id", 1L, "contractName", "TokenA"),
                Map.of("id", 2L, "contractName", "TokenB")));
        KnowledgeRetrievalRepository repository = (query, limit) -> List.of(
                referenceChunk("ERC-20 Token Standard", "ERC_STANDARD", "transfer allowance approve"));

        KnowledgeRetrievalResponse response = new KnowledgeRetrievalService(repository, catalog)
                .retrieve("ERC20", 2, 1L);

        assertThat(response.results()).hasSize(2);
        assertThat(response.results()).anyMatch(item -> "ERC_STANDARD".equals(item.get("sourceKind")));
        assertThat(response.results()).anyMatch(item -> item.get("contractId") != null);
    }

    private static KnowledgeChunkMatch contractChunk(long contractId, String contractName,
                                                       String kind, String symbol, String signature,
                                                       String file, int lineStart, int lineEnd,
                                                       String excerpt, String hash, String trust,
                                                       double score) {
        UUID sourceId = UUID.randomUUID();
        return new KnowledgeChunkMatch(UUID.randomUUID(), sourceId, "CONTRACT_LIBRARY",
                "contract:" + contractId, null, null, contractId, contractName,
                kind, symbol, signature, file, lineStart, lineEnd, excerpt, hash, trust, score);
    }

    private static KnowledgeChunkMatch referenceChunk(String title, String sourceKind, String excerpt) {
        UUID sourceId = UUID.randomUUID();
        return new KnowledgeChunkMatch(UUID.randomUUID(), sourceId, sourceKind,
                "reference:erc-20", title, "https://raw.githubusercontent.com/ethereum/ERCs/commit/ERCS/erc-20.md",
                null, null, "SECTION", "Specification", null, "erc-20", 1, 10,
                excerpt, "abc", "AUTHORITATIVE", 0.9);
    }
}
