package org.scse.cloudagent.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SolidityChunker {
    private static final Pattern DECLARATION = Pattern.compile(
            "\\b(function|modifier|constructor|receive|fallback)\\b(?:\\s+([A-Za-z_$][A-Za-z0-9_$]*))?");

    public List<KnowledgeChunk> chunk(NormalizedSourceFile file) {
        String source = file.normalizedContent();
        String searchable = maskCommentsAndStrings(source);
        Matcher matcher = DECLARATION.matcher(searchable);
        List<KnowledgeChunk> chunks = new ArrayList<>();
        while (matcher.find()) {
            int declarationStart = matcher.start();
            int bodyStart = findDeclarationTerminator(searchable, matcher.end());
            if (bodyStart < 0) {
                continue;
            }
            int end = searchable.charAt(bodyStart) == ';'
                    ? bodyStart + 1 : findMatchingBrace(searchable, bodyStart) + 1;
            if (end <= bodyStart) {
                continue;
            }
            String keyword = matcher.group(1);
            String name = matcher.group(2);
            String symbol = name == null ? keyword : name;
            String signature = source.substring(declarationStart, bodyStart).strip();
            chunks.add(toChunk(chunks.size(), kindOf(keyword), symbol, signature,
                    file.path(), source, declarationStart, end));
        }
        if (chunks.isEmpty() && !source.isBlank()) {
            chunks.add(toChunk(0, ChunkKind.FILE, file.path(), null,
                    file.path(), source, 0, source.length()));
        }
        return List.copyOf(chunks);
    }

    private KnowledgeChunk toChunk(int ordinal, ChunkKind kind, String symbol, String signature,
                                   String path, String source, int start, int end) {
        String content = source.substring(start, end);
        return new KnowledgeChunk(ordinal, kind, symbol, signature, path,
                lineAt(source, start), lineAt(source, Math.max(start, end - 1)),
                start, end, content, KnowledgeHashing.sha256(content));
    }

    private int findDeclarationTerminator(String source, int from) {
        int parentheses = 0;
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                parentheses++;
            } else if (c == ')') {
                parentheses = Math.max(0, parentheses - 1);
            } else if (parentheses == 0 && (c == '{' || c == ';')) {
                return i;
            }
        }
        return -1;
    }

    private int findMatchingBrace(String source, int openingBrace) {
        int depth = 0;
        for (int i = openingBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private int lineAt(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private ChunkKind kindOf(String keyword) {
        return switch (keyword) {
            case "function" -> ChunkKind.FUNCTION;
            case "modifier" -> ChunkKind.MODIFIER;
            case "constructor" -> ChunkKind.CONSTRUCTOR;
            case "receive" -> ChunkKind.RECEIVE;
            case "fallback" -> ChunkKind.FALLBACK;
            default -> throw new IllegalArgumentException("Unknown Solidity declaration: " + keyword);
        };
    }

    private String maskCommentsAndStrings(String source) {
        StringBuilder masked = new StringBuilder(source);
        boolean lineComment = false;
        boolean blockComment = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                } else {
                    masked.setCharAt(i, ' ');
                }
            } else if (blockComment) {
                if (c == '*' && next == '/') {
                    masked.setCharAt(i, ' ');
                    masked.setCharAt(i + 1, ' ');
                    blockComment = false;
                    i++;
                } else if (c != '\n') {
                    masked.setCharAt(i, ' ');
                }
            } else if (quote != 0) {
                if (c != '\n') {
                    masked.setCharAt(i, ' ');
                }
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '/' && next == '/') {
                masked.setCharAt(i, ' ');
                masked.setCharAt(i + 1, ' ');
                lineComment = true;
                i++;
            } else if (c == '/' && next == '*') {
                masked.setCharAt(i, ' ');
                masked.setCharAt(i + 1, ' ');
                blockComment = true;
                i++;
            } else if (c == '\'' || c == '"') {
                masked.setCharAt(i, ' ');
                quote = c;
            }
        }
        return masked.toString();
    }
}
