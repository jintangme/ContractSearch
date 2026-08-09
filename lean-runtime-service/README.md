# Lean Runtime Service

Lean4 proof checking service for Contract Lab Proof Workbench.

This service only checks Lean4 source code. It does not generate proofs, does not automatically prove Solidity contract safety, and does not directly verify EVM semantics.

## Start

```bash
cd lean-runtime-service
npm install
npm run dev
```

Default configuration:

```bash
PORT=19100
LEAN_BIN=lean
LEAN_TIMEOUT_MS=10000
```

If Lean is installed in a custom location:

```bash
LEAN_BIN=/path/to/lean npm run dev
```

## API

`POST http://localhost:19100/api/lean/check`

Request:

```json
{
  "sourceCode": "example : True := by\n  trivial",
  "fileName": "Proof.lean"
}
```

Passed response:

```json
{
  "success": true,
  "status": "passed",
  "message": "Lean proof checked successfully.",
  "errors": [],
  "warnings": [],
  "elapsedMs": 120
}
```

Failed response:

```json
{
  "success": false,
  "status": "failed",
  "message": "Lean proof check failed.",
  "errors": [
    {
      "line": 3,
      "column": 12,
      "severity": "error",
      "message": "unsolved goals"
    }
  ],
  "warnings": [],
  "elapsedMs": 130
}
```

If Lean4 is not installed or `LEAN_BIN` is wrong, the service returns `LEAN_NOT_FOUND`:

```json
{
  "success": false,
  "status": "failed",
  "errorType": "LEAN_NOT_FOUND",
  "message": "未检测到 Lean 命令，请先安装 Lean4 或配置 LEAN_BIN。",
  "errors": [],
  "warnings": []
}
```

## curl

```bash
curl -X POST http://localhost:19100/api/lean/check \
  -H "Content-Type: application/json" \
  -d '{"sourceCode":"example : True := by\n  trivial","fileName":"Proof.lean"}'
```
