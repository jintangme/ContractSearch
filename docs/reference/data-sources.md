# Data sources and provenance

## Demonstration data

[`database/bootstrap/002-demo.sql`](../../database/bootstrap/002-demo.sql)
contains three small contracts written for this project's demonstration.
Their `demo:` identifiers are synthetic, not blockchain addresses. The seed
contains no accounts, private messages, wallet keys or precomputed embeddings.
The examples support local feature exploration and are not audited contracts.
See [third-party notices](../../THIRD_PARTY_NOTICES.md) for licensing status.

## Optional discovery

`scse-discover` queries Dune and enriches records through Etherscan. Before
redistributing retrieved source, retain its chain/address, upstream URL,
compiler metadata and license. API availability does not grant a license to
redistribute every returned contract.

Generated Parquet files and database dumps are excluded from Git. The import
callback reads only from an explicitly configured shared directory; it does
not transfer files between services. See the [backend guide](../development/backend.md).

## Reference knowledge

The [source manifest](../agentic-rag/knowledge-sources-v1.json) records pinned
upstream revisions, URLs and declared licenses for ERC/EIP standards, Solidity
documentation, OpenZeppelin references and OWASP guidance. Synchronization
also records normalized content hashes. Imported content stays in the local
database.

Reference materials retain their own licenses. Check the pinned revision and
content type before redistributing a bundle: documentation and code may use
different licenses. Follow the [reference synchronization guide](../agentic-rag/authoritative-knowledge-runbook.md)
to import or update these sources.
