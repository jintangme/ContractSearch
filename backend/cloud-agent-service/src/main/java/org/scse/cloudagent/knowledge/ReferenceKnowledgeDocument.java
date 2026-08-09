package org.scse.cloudagent.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ReferenceKnowledgeDocument(
        @NotBlank @Size(max = 160) String sourceKey,
        @NotBlank @Size(max = 300) String title,
        @NotBlank @Size(max = 1000) String sourceUrl,
        @NotBlank @Size(max = 64) String sourceKind,
        @NotBlank @Size(max = 100) String version,
        @NotBlank @Size(max = 100) String license,
        @NotBlank @Size(max = 32) String format,
        @NotBlank @Size(min = 64, max = 64) String contentHash,
        Instant fetchedAt,
        @NotBlank @Size(max = 2_000_000) String content
) {
    public ReferenceKnowledgeDocument {
        sourceKey = trim(sourceKey);
        title = trim(title);
        sourceUrl = trim(sourceUrl);
        sourceKind = trim(sourceKind);
        version = trim(version);
        license = trim(license);
        format = trim(format);
        contentHash = trim(contentHash);
        fetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
