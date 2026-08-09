package org.scse.cloudagent.knowledge;

import java.util.List;

public record SourceManifest(
        String sourceRef,
        Long existingContractId,
        SourceFormat format,
        ParserStatus parserStatus,
        String rawHash,
        String normalizedHash,
        List<NormalizedSourceFile> files,
        List<String> diagnostics
) {
    public SourceManifest {
        files = List.copyOf(files);
        diagnostics = List.copyOf(diagnostics);
    }
}
