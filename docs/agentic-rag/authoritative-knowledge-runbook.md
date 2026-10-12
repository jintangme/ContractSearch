# Reference knowledge synchronization

Import pinned reference material alongside catalog source to support retrieval
of standards, language documentation and security guidance. The
[source manifest](knowledge-sources-v1.json) records exact upstream commits,
URLs, formats and declared licenses for Ethereum ERCs/EIPs, Solidity,
OpenZeppelin and OWASP. Review [data-source licensing](../reference/data-sources.md)
before redistributing imported content.

## Configure

Start the Agent with migrations applied, knowledge enabled and the desired
corpus name/version. The Compose demo uses `contractsearch / demo-v1`; host-run
services should set the same values when using that demo:

```dotenv
AGENT_KNOWLEDGE_ENABLED=true
AGENT_INTERNAL_TOKEN=<same token used by cloud-agent-service>
AGENT_KNOWLEDGE_CORPUS_NAME=contractsearch
AGENT_KNOWLEDGE_CORPUS_VERSION=demo-v1
```

From the repository root, fetch and verify without writing:

```bash
python3 scripts/with-env.py python3 scripts/agentic-rag/sync-authoritative-knowledge.py --dry-run
```

Then import through the private Agent port:

```bash
python3 scripts/with-env.py python3 scripts/agentic-rag/sync-authoritative-knowledge.py \
  --endpoint http://127.0.0.1:18082/internal/agent/v1/knowledge/references \
  --batch-size 8
```

The script fetches pinned upstream content over the network and submits it with
`X-Agent-Internal-Token`. It makes no model calls. The server accepts only
allowlisted repository/path prefixes on `raw.githubusercontent.com`, verifies
the declared immutable commit and normalized SHA-256, and rejects mismatches.
Reference content remains untrusted input even when its provenance is labeled
`AUTHORITATIVE`.

## Verify and update

Run `VALIDATE` through the [ingestion endpoint](knowledge-ingestion-runbook.md)
and check for zero integrity errors. Query the private retrieval endpoint with
terms such as `ERC20 transfer approve allowance` and inspect source URLs,
revisions and hashes. Results depend on the catalog and enabled retrieval
channels; the three-contract demo does not cover every reference topic.

Repeated synchronization of the same source revision and hash is idempotent.
To refresh content, update the manifest to pinned revisions and select a new
corpus version when replacing existing source identities. Keep the Agent's
retrieval configuration aligned with the intended corpus.

Set `AGENT_KNOWLEDGE_ENABLED=false` and restart the Agent to disable the feature.
The stored reference rows remain available for a later restart.
