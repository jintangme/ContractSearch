package org.scse.cloudagent.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceKnowledgeChunkerTest {
    private final ReferenceKnowledgeChunker chunker = new ReferenceKnowledgeChunker();

    @Test
    void createsHeadingAwareChunksWithStableSpans() {
        String content = """
                # ERC-20
                Intro text.

                ## Specification
                transfer and approve requirements.

                ## Security
                Callers must validate permissions.
                """.strip();

        var chunks = chunker.chunk("ERC-20", content);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(1).heading()).isEqualTo("Specification");
        assertThat(chunks.get(1).lineStart()).isEqualTo(4);
        assertThat(chunks.get(1).content()).contains("transfer and approve");
        assertThat(chunks).allMatch(chunk -> chunk.charEnd() > chunk.charStart());
    }

    @Test
    void boundsLargeSectionsWithoutLosingContent() {
        String content = "# Large\n" + "nonReentrant access control paragraph\n".repeat(400);

        var chunks = chunker.chunk("Large", content);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> chunk.content().length() <= 5_000);
        assertThat(chunks.get(0).lineStart()).isEqualTo(1);
    }
}
