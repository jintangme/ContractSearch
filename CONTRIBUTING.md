# Development contributions

Start with the [quick start](docs/development/quickstart.md) and the
[architecture](docs/architecture/overview.md). Keep a change focused and explain
its user-visible behavior, validation, and limitations.

Before proposing a change, run the relevant tests plus publication checks:

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix contract-runtime-service test
npm --prefix lean-runtime-service test
python3 scripts/check-public-content.py
python3 scripts/check-doc-links.py
git diff --check
```

Database/live-service tests are opt-in; see
[backend development](docs/development/backend.md). Do not point them at a
production or personal database. Model probes require explicit local
credentials and can incur usage charges.

Changes to public setup should be checked against a fresh demo volume.
Changes to an external dependency or imported source should update
[third-party records](THIRD_PARTY_NOTICES.md).
Keep test artifacts and real account data out of the repository.

Project-specific licensing is still pending; discuss contribution and
redistribution terms with the maintainer before contributing substantial code.
