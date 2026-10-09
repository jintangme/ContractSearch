# Security and publication privacy

## Intended deployment

The included Compose stack is a single-user **local demonstration**, with host
ports bound to loopback. It is not a hardened public hosting configuration.
The contract runtime uses a shared in-memory EVM. The Lean checker can execute
submitted Lean programs and is deliberately excluded from the proxy.

The Gateway rejects the offline import route. File ingestion requires a
separate internal token, a configured allowed directory, and a direct private
service connection. Keep internal service and database ports private.

## Before sharing a change

```bash
python3 scripts/check-public-content.py
python3 scripts/check-doc-links.py
git diff --check
```

Review the diff and any images manually as well. Use only synthetic accounts,
example domains and demo contracts in screenshots. Avoid real profiles,
browser/account menus, wallet addresses belonging to users, conversations,
developer consoles, access tokens and database connection strings.

The public-content check is a limited, redacted heuristic over tracked and
non-ignored candidate files. It is not a guarantee that secrets cannot exist,
does not inspect private ignored files, and does not rewrite or certify Git
history. Historical commits can retain metadata and removed content. Review
commit identities and history separately before a new public release.

To prepare a reviewed source snapshot without Git history, run from the checkout:

```bash
python3 scripts/export-public-source.py
```

This first runs the current-file and documentation checks, then packages Git
source candidates into the ignored `artifacts/ContractSearch-source.zip`.
It excludes Git metadata, local configuration, private data and generated files.
Review the resulting archive before sharing it. Creating a snapshot does not
remove older content or commit metadata from an already published repository.

## Local files

Environment files, private data, uploads, generated logs, browser state, dumps
and backups must remain ignored. The Docker build context is an allowlist of
source directories with private/runtime files excluded. Never upload a whole
local project folder or backup archive as a release asset.

If a credential was previously exposed, deleting it from the latest file does
not revoke it. Rotate it at its provider and assess any history cleanup
separately.

## Reporting a vulnerability

Do not post credentials or personal data in a public issue. Use GitHub private
vulnerability reporting if it is enabled for this repository. Otherwise open
an issue requesting a private contact channel without including the sensitive
details.
