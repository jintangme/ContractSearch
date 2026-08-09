# Authoritative Knowledge Runbook

## Scope

This checkpoint adds a small, reproducible domain pack to the existing `contractsearch / delivery-stage2-v1` corpus. It does not create a second vector database, crawl arbitrary websites, activate a new Search index, or call a language model. Reference text is data, never instructions.

The manifest is `docs/agentic-rag/knowledge-sources-v1.json`. It pins every source to a 40-character commit and records title, source kind, format and license. Current source families are Ethereum ERCs/EIPs, Solidity documentation, OpenZeppelin Contracts and OWASP Smart Contract Top 10.

## Security boundary

- The private endpoint is `POST /internal/agent/v1/knowledge/references` and requires `X-Agent-Internal-Token`.
- Only `raw.githubusercontent.com` and the repository/path prefixes hard-coded by the service are accepted.
- Branch names and mutable URLs are rejected; the URL must contain the declared commit.
- The service recomputes normalized SHA-256 and rejects a mismatch.
- Each document must be 500 bytes to 2 MiB and use `MARKDOWN`, `RST`, `ASCIIDOC` or `SOLIDITY`.
- Trust is assigned by the server as `AUTHORITATIVE`; content is still wrapped with the untrusted-reference boundary.

## Sync

Required environment:

```text
AGENT_KNOWLEDGE_ENABLED=true
AGENT_INTERNAL_TOKEN=<same token used by cloud-agent-service>
AGENT_KNOWLEDGE_CORPUS_NAME=contractsearch
AGENT_KNOWLEDGE_CORPUS_VERSION=delivery-stage2-v1
```

Fetch and verify without writing:

```text
python3 scripts/agentic-rag/sync-authoritative-knowledge.py --dry-run
```

Import through the private Agent port:

```text
AGENT_INTERNAL_TOKEN=<token> python3 scripts/agentic-rag/sync-authoritative-knowledge.py \
  --endpoint http://127.0.0.1:18082/internal/agent/v1/knowledge/references \
  --batch-size 8
```

The operation is idempotent for the same source key, commit and content hash. A repeat reports all sources as skipped while refreshing their text-search projection. A changed body under an existing immutable source reference is rejected.

## Acceptance

Run Knowledge `VALIDATE` and require zero orphan documents, orphan chunks, missing search rows and duplicate source identities. Then query the private retrieval endpoint with:

1. `ERC20 transfer approve allowance token standard`
2. `mint onlyOwner AccessControl MINTER_ROLE`
3. `reentrancy nonReentrant checks effects interactions external call`

Each response must contain an `AUTHORITATIVE` result with source URL/version/hash provenance, at least one existing contract candidate, and no degraded channels in the normal local environment.

## Refresh and rollback

To update a source, add a new manifest version with a new immutable commit; do not mutate an already published corpus identity. Normal rollback is `AGENT_KNOWLEDGE_ENABLED=false` followed by restarting only `cloud-agent-service`. V5 and the reference rows are additive and do not alter the old Search API/UI. Restore the pre-stage dump only when physical removal is explicitly required.
