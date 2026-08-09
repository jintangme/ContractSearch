package org.scse.cloudagent.tool;

import org.scse.cloudagent.domain.TrustLevel;

import java.util.Map;

public record EvidenceDraft(
        String sourceType,
        String sourceRef,
        String title,
        String excerpt,
        TrustLevel trustLevel,
        Map<String, Object> metadata
) {
    public EvidenceDraft {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
