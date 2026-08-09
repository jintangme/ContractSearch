package org.scse.cloudagent.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class SoliditySourceRouter {
    private static final String DEFAULT_FILE = "Contract.sol";

    private final ObjectMapper objectMapper;

    public SoliditySourceRouter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SourceManifest route(String sourceRef, Long existingContractId, String sourceCode) {
        String raw = sourceCode == null ? "" : sourceCode;
        String rawHash = KnowledgeHashing.sha256(raw);
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return invalid(sourceRef, existingContractId, rawHash, "EMPTY_SOURCE");
        }

        if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
            SourceManifest wrapped = parseJson(sourceRef, existingContractId, rawHash,
                    trimmed.substring(1, trimmed.length() - 1),
                    SourceFormat.ETHERSCAN_WRAPPED_STANDARD_JSON);
            if (wrapped != null) {
                return wrapped;
            }
        }

        if (trimmed.startsWith("{")) {
            SourceManifest json = parseJson(sourceRef, existingContractId, rawHash, trimmed, null);
            if (json != null) {
                return json;
            }
            return partial(sourceRef, existingContractId, raw, rawHash, "MALFORMED_JSON_SOURCE");
        }

        if (looksLikeSolidity(trimmed)) {
            return parsed(sourceRef, existingContractId, rawHash, SourceFormat.PLAIN_SOLIDITY,
                    List.of(toFile(DEFAULT_FILE, raw)), List.of());
        }
        return invalid(sourceRef, existingContractId, rawHash, "UNRECOGNIZED_SOURCE_FORMAT");
    }

    private SourceManifest parseJson(String sourceRef, Long existingContractId, String rawHash,
                                     String json, SourceFormat forcedFormat) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode sources = root.path("sources");
            if (sources.isObject()) {
                List<NormalizedSourceFile> files = extractFiles(sources);
                if (!files.isEmpty()) {
                    SourceFormat format = forcedFormat == null ? SourceFormat.STANDARD_JSON : forcedFormat;
                    return parsed(sourceRef, existingContractId, rawHash, format, files, List.of());
                }
            }

            List<NormalizedSourceFile> files = extractFiles(root);
            if (!files.isEmpty()) {
                return parsed(sourceRef, existingContractId, rawHash,
                        SourceFormat.MULTI_FILE_SOURCE_MAP, files, List.of());
            }
            return null;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private List<NormalizedSourceFile> extractFiles(JsonNode object) {
        if (!object.isObject()) {
            return List.of();
        }
        List<NormalizedSourceFile> files = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            String content = value.isTextual() ? value.textValue()
                    : value.isObject() && value.path("content").isTextual()
                    ? value.path("content").textValue() : null;
            if (content != null) {
                files.add(toFile(field.getKey(), content));
            }
        }
        files.sort(Comparator.comparing(NormalizedSourceFile::path));
        return files;
    }

    private SourceManifest parsed(String sourceRef, Long existingContractId, String rawHash,
                                  SourceFormat format, List<NormalizedSourceFile> files,
                                  List<String> diagnostics) {
        return new SourceManifest(sourceRef, existingContractId, format, ParserStatus.PARSED,
                rawHash, manifestHash(files), files, diagnostics);
    }

    private SourceManifest partial(String sourceRef, Long existingContractId, String raw,
                                   String rawHash, String diagnostic) {
        NormalizedSourceFile file = toFile(DEFAULT_FILE, raw);
        return new SourceManifest(sourceRef, existingContractId, SourceFormat.PARTIAL,
                ParserStatus.PARTIAL, rawHash, manifestHash(List.of(file)), List.of(file),
                List.of(diagnostic));
    }

    private SourceManifest invalid(String sourceRef, Long existingContractId, String rawHash,
                                   String diagnostic) {
        return new SourceManifest(sourceRef, existingContractId, SourceFormat.INVALID,
                ParserStatus.INVALID, rawHash, KnowledgeHashing.sha256(""), List.of(),
                List.of(diagnostic));
    }

    private NormalizedSourceFile toFile(String path, String rawContent) {
        String normalized = normalize(rawContent);
        return new NormalizedSourceFile(path, rawContent, normalized,
                KnowledgeHashing.sha256(rawContent), KnowledgeHashing.sha256(normalized));
    }

    static String normalize(String source) {
        String unix = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = unix.split("\n", -1);
        StringBuilder normalized = new StringBuilder(unix.length() + 1);
        int lastNonEmpty = lines.length - 1;
        while (lastNonEmpty >= 0 && lines[lastNonEmpty].isBlank()) {
            lastNonEmpty--;
        }
        for (int i = 0; i <= lastNonEmpty; i++) {
            normalized.append(lines[i].replaceFirst("[ \\t]+$", "")).append('\n');
        }
        return normalized.toString();
    }

    private String manifestHash(List<NormalizedSourceFile> files) {
        StringBuilder canonical = new StringBuilder();
        files.stream().sorted(Comparator.comparing(NormalizedSourceFile::path))
                .forEach(file -> canonical.append(file.path()).append('\0')
                        .append(file.normalizedHash()).append('\n'));
        return KnowledgeHashing.sha256(canonical.toString());
    }

    private boolean looksLikeSolidity(String source) {
        return source.contains("pragma solidity")
                || source.matches("(?s).*(contract|interface|library|abstract\\s+contract)\\s+[A-Za-z_$][A-Za-z0-9_$]*.*");
    }
}
