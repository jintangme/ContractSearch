package org.scse.cloudagent.knowledge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "agent.knowledge", name = "enabled", havingValue = "true")
public class ReferenceKnowledgeIngestionService {
    private static final Pattern PINNED_REF = Pattern.compile("[0-9a-f]{40}");
    private static final Set<String> SOURCE_KINDS = Set.of(
            "ERC_STANDARD", "EIP_STANDARD", "SOLIDITY_DOCS",
            "OPENZEPPELIN_REFERENCE", "OWASP_SECURITY");
    private static final Set<String> FORMATS = Set.of("MARKDOWN", "RST", "ASCIIDOC", "SOLIDITY");
    private static final List<String> ALLOWED_PATHS = List.of(
            "/ethereum/ERCs/", "/ethereum/EIPs/", "/argotorg/solidity/",
            "/OpenZeppelin/openzeppelin-contracts/",
            "/OWASP/www-project-smart-contract-top-10/");

    private final JdbcReferenceKnowledgeStore store;

    public ReferenceKnowledgeIngestionService(JdbcReferenceKnowledgeStore store) {
        this.store = store;
    }

    public ReferenceKnowledgeIngestionResponse ingest(ReferenceKnowledgeIngestionRequest request) {
        request.documents().forEach(this::validate);
        return store.persist(request.documents());
    }

    private void validate(ReferenceKnowledgeDocument document) {
        if (document.content().length() < 500) {
            throw new IllegalArgumentException("Reference document is too small: " + document.sourceKey());
        }
        String kind = document.sourceKind().toUpperCase(Locale.ROOT);
        if (!SOURCE_KINDS.contains(kind)) throw new IllegalArgumentException("Unsupported sourceKind: " + kind);
        String format = document.format().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(format)) throw new IllegalArgumentException("Unsupported format: " + format);
        if (!document.contentHash().matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("contentHash must be SHA-256 hex");
        }
        String actualHash = KnowledgeHashing.sha256(ReferenceKnowledgeChunker.normalize(document.content()));
        if (!actualHash.equalsIgnoreCase(document.contentHash())) {
            throw new IllegalArgumentException("contentHash mismatch for " + document.sourceKey());
        }
        URI uri;
        try {
            uri = URI.create(document.sourceUrl());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid sourceUrl for " + document.sourceKey());
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"raw.githubusercontent.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("sourceUrl must use the approved raw GitHub host");
        }
        String path = uri.getPath();
        if (ALLOWED_PATHS.stream().noneMatch(path::startsWith)) {
            throw new IllegalArgumentException("sourceUrl repository is not allowlisted");
        }
        String[] parts = path.split("/");
        if (parts.length < 5 || !PINNED_REF.matcher(parts[3]).matches()
                || !document.version().equals(parts[3])) {
            throw new IllegalArgumentException("sourceUrl must contain the declared immutable commit");
        }
    }
}
