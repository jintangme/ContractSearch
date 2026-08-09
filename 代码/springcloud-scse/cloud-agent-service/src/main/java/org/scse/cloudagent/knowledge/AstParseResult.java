package org.scse.cloudagent.knowledge;

import java.util.List;

public record AstParseResult(
        boolean success,
        String parser,
        String compilerVersion,
        List<AstDeclaration> declarations,
        List<String> diagnostics
) {
    public AstParseResult {
        declarations = List.copyOf(declarations);
        diagnostics = List.copyOf(diagnostics);
    }
}
