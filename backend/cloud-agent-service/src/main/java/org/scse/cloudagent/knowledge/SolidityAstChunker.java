package org.scse.cloudagent.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SolidityAstChunker {
    public List<KnowledgeChunk> chunk(AstParseResult parseResult, List<NormalizedSourceFile> files) {
        Map<String, NormalizedSourceFile> filesByPath = files.stream()
                .collect(Collectors.toUnmodifiableMap(NormalizedSourceFile::path, Function.identity()));
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (AstDeclaration declaration : parseResult.declarations()) {
            validateMapping(declaration, filesByPath.get(declaration.filePath()));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("parser", parseResult.parser());
            metadata.put("compilerVersion", parseResult.compilerVersion());
            metadata.put("astId", declaration.astId());
            metadata.put("astNodeType", declaration.nodeType());
            put(metadata, "contractName", declaration.contractName());
            put(metadata, "visibility", declaration.visibility());
            put(metadata, "stateMutability", declaration.stateMutability());
            metadata.put("virtual", declaration.virtual());
            metadata.put("parameterTypes", declaration.parameterTypes());
            metadata.put("returnTypes", declaration.returnTypes());
            metadata.put("modifiers", declaration.modifiers());
            chunks.add(new KnowledgeChunk(chunks.size(), kindOf(declaration.kind()),
                    declaration.name(), declaration.signature(), declaration.filePath(),
                    declaration.lineStart(), declaration.lineEnd(), declaration.startChar(),
                    declaration.endChar(), declaration.content(),
                    KnowledgeHashing.sha256(declaration.content()), metadata));
        }
        return List.copyOf(chunks);
    }

    private void validateMapping(AstDeclaration declaration, NormalizedSourceFile file) {
        if (file == null || declaration.startChar() < 0
                || declaration.endChar() <= declaration.startChar()
                || declaration.endChar() > file.normalizedContent().length()
                || !file.normalizedContent().substring(declaration.startChar(), declaration.endChar())
                .equals(declaration.content())) {
            throw new IllegalArgumentException("AST declaration does not map to normalized source: "
                    + declaration.filePath() + "#" + declaration.astId());
        }
    }

    private ChunkKind kindOf(String kind) {
        return switch (kind) {
            case "modifier" -> ChunkKind.MODIFIER;
            case "constructor" -> ChunkKind.CONSTRUCTOR;
            case "receive" -> ChunkKind.RECEIVE;
            case "fallback" -> ChunkKind.FALLBACK;
            default -> ChunkKind.FUNCTION;
        };
    }

    private void put(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) metadata.put(key, value);
    }
}
