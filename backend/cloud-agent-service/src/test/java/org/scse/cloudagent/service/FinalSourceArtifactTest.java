package org.scse.cloudagent.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FinalSourceArtifactTest {
    private String block(String source) { return "\u0060\u0060\u0060solidity\n" + source + "\u0060\u0060\u0060"; }
    @Test void preservesSingleCompleteUnitBytesIncludingFinalNewline() {
        String source = "// license\npragma solidity ^0.8.20;\ncontract X { string a = \"}\"; }\n";
        assertThat(FinalSourceArtifact.singleCompleteSource("Draft:\n" + block(source))).isEqualTo(source);
    }
    @Test void ignoresCommentAndStringDeclarationsAndFunctionFragments() {
        assertThat(FinalSourceArtifact.singleCompleteSource(block("// contract Fake {}\nfunction x() public {}"))).isNull();
        assertThat(FinalSourceArtifact.singleCompleteSource(block("string a = 'contract Fake {}';"))).isNull();
        assertThat(FinalSourceArtifact.singleCompleteSource(block("contract Broken {"))).isNull();
    }
    @Test void rejectsMultipleCompleteUnitsInSeparateBlocksInsteadOfSelectingByOutcome() {
        assertThatThrownBy(() -> FinalSourceArtifact.singleCompleteSource(block("contract A {}") + block("contract B {}")))
                .hasMessageContaining("Ambiguous source delivery");
    }
    @Test void supportsInterfaceAndLibraryWithoutRequiringPragma() {
        assertThat(FinalSourceArtifact.singleCompleteSource(block("interface I { function f() external; }\n")))
                .isEqualTo("interface I { function f() external; }\n");
        assertThat(FinalSourceArtifact.isCompleteUnit("library L { function f() internal {} }")).isTrue();
    }
}
