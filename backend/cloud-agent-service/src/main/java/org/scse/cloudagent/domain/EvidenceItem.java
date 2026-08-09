package org.scse.cloudagent.domain;

import java.util.Map;
import java.util.UUID;

public record EvidenceItem(
        UUID id,
        String sourceType,
        String sourceRef,
        String title,
        String excerpt,
        TrustLevel trustLevel,
        Map<String, Object> metadata
) {
}
