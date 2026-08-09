package org.scse.cloudagent.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIngestionPlannerTest {
    private final SoliditySourceRouter router = new SoliditySourceRouter(new ObjectMapper());
    private final KnowledgeIngestionPlanner planner =
            new KnowledgeIngestionPlanner(router, new SolidityChunker());

    @Test
    void recognizesSupportedSourceFormatsAndProducesStableHashes() {
        String solidity = "pragma solidity ^0.8.20;\r\ncontract Token { }   \r\n";
        SourceManifest plain = router.route("contract:1", 1L, solidity);
        SourceManifest standardJson = router.route("contract:2", 2L,
                "{\"language\":\"Solidity\",\"sources\":{\"B.sol\":{\"content\":\"contract B {}\"},"
                        + "\"A.sol\":{\"content\":\"contract A {}\"}}}");
        SourceManifest etherscan = router.route("contract:3", 3L,
                "{{\"language\":\"Solidity\",\"sources\":{\"C.sol\":{\"content\":\"contract C {}\"}}}}");
        SourceManifest malformed = router.route("contract:4", 4L, "{not-json");

        assertThat(plain.format()).isEqualTo(SourceFormat.PLAIN_SOLIDITY);
        assertThat(plain.files().get(0).normalizedContent())
                .isEqualTo("pragma solidity ^0.8.20;\ncontract Token { }\n");
        assertThat(router.route("contract:1", 1L, solidity).normalizedHash())
                .isEqualTo(plain.normalizedHash());
        assertThat(standardJson.format()).isEqualTo(SourceFormat.STANDARD_JSON);
        assertThat(standardJson.files()).extracting(NormalizedSourceFile::path)
                .containsExactly("A.sol", "B.sol");
        assertThat(etherscan.format()).isEqualTo(SourceFormat.ETHERSCAN_WRAPPED_STANDARD_JSON);
        assertThat(malformed.parserStatus()).isEqualTo(ParserStatus.PARTIAL);
        assertThat(malformed.diagnostics()).containsExactly("MALFORMED_JSON_SOURCE");
        assertThat(planner.dryRun("contract:4", 4L, "{not-json").chunks()).isEmpty();
    }

    @Test
    void dryRunBuildsDeterministicFunctionAndModifierChunksWithLineMappings() {
        String source = """
                pragma solidity ^0.8.20;
                contract Vault {
                    modifier onlyOwner() {
                        _;
                    }

                    // function fake() must not become a chunk
                    function deposit(uint256 amount)
                        public
                        onlyOwner
                    {
                        require(amount > 0, "function hidden() {}");
                    }
                }
                """;

        KnowledgeIngestionPlan first = planner.dryRun("contract:42", 42L, source);
        KnowledgeIngestionPlan second = planner.dryRun("contract:42", 42L, source);

        assertThat(first.manifest().parserStatus()).isEqualTo(ParserStatus.PARSED);
        assertThat(first.chunks()).hasSize(2);
        assertThat(first.chunks()).extracting(KnowledgeChunk::kind)
                .containsExactly(ChunkKind.MODIFIER, ChunkKind.FUNCTION);
        KnowledgeChunk function = first.chunks().get(1);
        assertThat(function.symbolName()).isEqualTo("deposit");
        assertThat(function.lineStart()).isEqualTo(8);
        assertThat(function.lineEnd()).isEqualTo(13);
        assertThat(function.content()).contains("require(amount > 0");
        assertThat(second).isEqualTo(first);
    }
}
