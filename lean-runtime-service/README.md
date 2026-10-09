# Lean Runtime Service

Optional local Lean source checker for Contract Lab. The main search and Agent
workflow do not require it. It does not translate Solidity into a verified model
or establish that a contract is safe.

## Run locally

Install a Lean toolchain separately and point `LEAN_BIN` at its actual executable.
The documented target is Lean 4.31.0; this service does not install or verify that
version automatically. An `elan` shim may attempt a download, so check the binary
before starting the service:

```bash
# From this directory, with a toolchain already installed:
export LEAN_BIN=/path/to/lean-4.31.0/bin/lean
"$LEAN_BIN" --version
npm ci
npm start
```

| Variable | Default | Meaning |
|---|---|---|
| `LEAN_HOST` | `127.0.0.1` | Listen address; legacy `HOST` is a fallback |
| `LEAN_PORT` | `19100` | Listen port; legacy `PORT` is a fallback |
| `LEAN_BIN` | `lean` | Executable selected by the operator |
| `LEAN_TIMEOUT_MS` | `10000` | Per-check timeout, capped at 60000 ms |
| `LEAN_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Comma-separated local browser origins |

Environment variables must be exported; this Node service does not automatically
load a `.env` file. Source and request size are limited to 1 MB.

## Check endpoint

```bash
curl http://127.0.0.1:19100/api/lean/check \
  -H 'Content-Type: application/json' \
  -d '{"sourceCode":"example : True := by\n  trivial","fileName":"Proof.lean"}'
```

`POST /api/lean/check` returns `success`, `status` (`passed` or `failed`),
`message`, `errors`, `warnings`, and `elapsedMs`. A successful response means
that the selected Lean executable accepted the submitted source and the local
checks found no supported incomplete-proof marker.

- Explicit `sorry` / `admit` placeholders are rejected as `INCOMPLETE_PROOF`.
- Explicit `axiom` / `axioms` declarations are outside the supported scope and
  rejected as `UNSUPPORTED_PROOF_ASSUMPTION`.
- A compiler warning about `sorry` also fails the check, even with exit code 0.
- Ordinary comments and string literals are excluded from the source scan.
- Missing executables return `LEAN_NOT_FOUND`; timeouts return `LEAN_TIMEOUT`.

These are conservative checks, not a complete Lean parser, an audit of imported
axioms, or a guarantee about arbitrary metaprograms. Imported libraries and their
assumptions are not independently audited. The service uses a temporary directory
and removes it after the check; it is not a Lake project or a Mathlib environment.

## Execution boundary

Lean source can execute code through its language facilities. This service runs
the executable with the host process's permissions. Temporary files, a timeout,
origin checks, and rejecting `sorry` do **not** provide an operating-system sandbox.

Use trusted input on a local machine. Do not publish this endpoint through Nginx,
a tunnel, or a public container port. A public proof service would require
separate OS isolation, least-privilege credentials, resource limits and
authentication. Checking a hand-written Lean model does not prove its equivalence
to the original Solidity or EVM execution.

## Tests

```bash
npm test
```

Tests cover rejected placeholders, compiler warnings, temporary-file cleanup and
a missing executable using a substitute runner. They need no Lean installation
and do not constitute a live proof-check result.
