package org.scse.cloudagent.knowledge;

import java.util.List;

public record AstDeclaration(
        long astId,
        String nodeType,
        String kind,
        String name,
        String contractName,
        String visibility,
        String stateMutability,
        boolean virtual,
        List<String> parameterTypes,
        List<String> returnTypes,
        List<String> modifiers,
        String filePath,
        int startChar,
        int endChar,
        int lineStart,
        int lineEnd,
        String signature,
        String content
) {
    public AstDeclaration {
        parameterTypes = List.copyOf(parameterTypes);
        returnTypes = List.copyOf(returnTypes);
        modifiers = List.copyOf(modifiers);
    }
}
