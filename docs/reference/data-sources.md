# Data sources and provenance

## Included demonstration data

`database/bootstrap/002-demo.sql` contains three small contracts written for
this repository's demonstration. Their `demo:` identifiers are synthetic and
are not blockchain addresses. They contain no user accounts, private messages,
real wallet keys, scraped contract bodies, or precomputed embeddings.

These examples illustrate application behavior. They are not audited contracts
or evidence of retrieval quality on a real-world corpus. Their project-specific
license status follows the repository; see [Third-party notices](../../THIRD_PARTY_NOTICES.md).

## Optional contract discovery

`scse-discover` queries Dune and enriches records using Etherscan. A data-source
API is a retrieval channel, not a blanket license for all retrieved contracts.
Record contract address/chain, upstream location, compiler metadata and each
source's license before redistribution. A public source or on-chain deployment
does not by itself establish permission to relicense its contents.

Generated Parquet files and complete database dumps remain outside Git.
The callback imports files from a deliberately configured shared directory;
it does not upload arbitrary local files.

## Reference knowledge

The canonical [manifest](../agentic-rag/knowledge-sources-v1.json) records source
title, exact upstream commit, URL, format and declared license for ERC/EIP
standards, Solidity documentation, OpenZeppelin references and OWASP guidance.
The synchronization process additionally computes normalized content hashes.

These are independently licensed materials. A future license for this project's
code will not replace their licenses. License declarations should be checked
against the pinned revision and actual content type before redistributing a
knowledge bundle; source-code licenses and documentation licenses can differ.
The current manifest is an inventory, not a blanket redistribution permission.

## Historical scale

Archived delivery records describe ingestion of a larger private local corpus.
Those counts are historical observations, not the size of the public demo, and
are not revalidated by the current basic-mode smoke test. Do not publish old
database backups, personal upload records, run identifiers or private logs to
reproduce those figures.
