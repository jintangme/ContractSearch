# Changelog

## Unreleased — 2.0 public demonstration preparation

- Add a fresh database bootstrap with three original sample contracts and no seeded users.
- Add a loopback-only Compose demonstration, private configuration generator and API smoke check.
- Separate basic mode from explicitly enabled Agent mode.
- Extract Contract Lab modules and improve evidence expansion and conversation cancellation.
- Reject public file import and untrusted identity headers; restrict internal import paths and tokens.
- Align the local compiler target and EVM hardfork; add compile/deploy/call regression coverage.
- Fix the runtime lockfile's optional macOS dependency metadata for Linux container installation.
- Default frontend development to loopback and preserve Vite's host validation.
- Clarify Lean checking boundaries and reject known incomplete-proof inputs.
- Add third-party attribution, data provenance, publication checks and CI definitions.
- Separate historical delivery records from current documentation and validation.
- Provide a checked source ZIP export without Git history or local runtime data.

Historical development context is in [version evolution](docs/version-history.md)
and the [archive](docs/archive/README.md). A public version release is not implied
by the historical phase numbers.
