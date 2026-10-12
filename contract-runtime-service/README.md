# Contract Runtime Service

Local Solidity compilation, AST extraction, and EVM execution for Contract Lab
and AgenticRAG. It uses Express, solc, ethers, and Ganache. Transactions execute
on in-memory chains; no wallet extension, public RPC, or real funds are required.

## Install and run

Use Node.js 22 and npm from the repository root:

```bash
cd contract-runtime-service
npm ci
npm start
```

The default address is `http://127.0.0.1:19090`. Override it with `RUNTIME_HOST`
and `RUNTIME_PORT`; `HOST` and `PORT` are fallback variables. The process does
not automatically load `.env` files. `npm run dev` uses the same entry point.

```bash
curl http://127.0.0.1:19090/health
```

Health returns `status`, `mode: "shared-local-evm"`, and `compilerVersion`.
Send JSON with `Content-Type: application/json`; the HTTP body limit is 1 MB.

## API

All POST routes below are relative to `/api/runtime`.

| Route | Request | Result |
|---|---|---|
| `/parse` | `sources`: map of file names to source strings | Declaration metadata, source spans, hashes, and diagnostics |
| `/compile` | `sourceCode`, optional `fileName` and `compilerVersion` | `success`, compiler information, `contracts` with ABI/bytecode, errors and warnings |
| `/deploy` | `contractName`, `abi`, `bytecode`, optional `constructorArgs` | Session ID, local contract address, and transaction receipt fields |
| `/call` | `sessionId`, `functionName`, `args`, optional `value` | Read return values or a transaction receipt with decoded events |
| `/validation-plan` | `plan`, optional `sourceCode` and `completeSource` | Canonical `planHash`, counts, and optional ABI preflight |
| `/validate-candidate` | `sourceCode`, `plan`, `planHash` | Source/plan hashes, compile result, scenario checks, and execution status |

Compilation, parsing, deployment, and calls return HTTP 400 on unsuccessful
results. Candidate checks can return HTTP 200 with `allChecksPassed: false`;
inspect the JSON result rather than treating HTTP success as a passed test.

### Compile, deploy, and call

Compile a single self-contained Solidity file:

```json
{
  "fileName": "Counter.sol",
  "sourceCode": "pragma solidity ^0.8.20; contract Counter { uint public count; function increment() public { count += 1; } }"
}
```

Choose an entry from `contracts`, then send its `contractName`, `abi`, and
`bytecode` to `/deploy`, with `constructorArgs: []` for this example. Use the
returned session ID to call `/call`:

```json
{"sessionId":"<returned session ID>","functionName":"increment","args":[],"value":"0"}
```

Call `count` with empty arguments to read the new value. The `value` field is
an **ether-denominated decimal string**, not wei. Integer outputs are serialized
as decimal strings. Lab deployments share one in-memory chain and signer.
Sessions expire after 30 minutes of inactivity; expiration removes a session
reference, not its deployed chain state. Restarting the service clears both.

### Validate a candidate

Candidate checks use a declarative plan with named requirements and scenarios.
Each scenario names one requirement, has its own fresh deployment, and contains
ordered ABI calls. This example runs against the service started above:

```javascript
const base = 'http://127.0.0.1:19090/api/runtime';
const sourceCode = 'pragma solidity ^0.8.20; contract Counter { uint public count; function increment() public { count += 1; } }';
const plan = {
  contractName: 'Counter', constructorArgs: [],
  requirements: [{ id: 'increment', description: 'Increment updates state' }],
  scenarios: [{
    id: 'increment_once', requirementId: 'increment', steps: [
      { kind: 'write', method: 'increment', args: [], from: 0, expect: 'success' },
      { kind: 'read', method: 'count', args: [], from: 0, expect: 'equal', expected: '1' }
    ]
  }]
};
async function post(route, body) {
  const response = await fetch(base + route, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  const result = await response.json();
  if (!response.ok) throw new Error(JSON.stringify(result));
  return result;
}
const { planHash } = await post('/validation-plan', { plan, sourceCode, completeSource: true });
const result = await post('/validate-candidate', { sourceCode, plan, planHash });
console.log(result.status, result.allChecksPassed, result.scenarios);
```

Run the example in a JavaScript module (`.mjs`) with Node.js 22. The expected
result is `CHECKS_PASSED` and `allChecksPassed: true`. Keep the returned `planHash`
with the unchanged plan; candidate validation rejects a mismatched hash.
`/validation-plan` validates the supplied object but does not store a server-side
plan. AgenticRAG manages plan freezing in its own run workspace.

Plans support read assertions, successful writes, expected reverts, exact ordered
events, write return values, and bounded ETH balance changes. Write returns are
checked by pre-transaction simulation; state and events come from the transaction.
`from` selects account 0, 1, or 2; `$account0`–`$account2` are distinct nonzero
accounts and `$contract` denotes the deployed contract. Use strings for large
integers. Optional scenario `constructorArgs` overrides the plan default.

Limits: 24,000 source characters; 24,000 characters in serialized plans;
12 requirements and 12 scenarios; 10 steps per scenario and 64 steps total.
Each candidate runs in a disposable child process with a 25-second timeout and
256 MB Node heap limit. At most two workers run concurrently; excess requests
return `BUSY`. Receipts identify the exact source and plan hashes. Passing checks
establishes only the declared scenarios, not general correctness or security.

## Compiler and execution scope

The bundled compiler is solc **0.8.35**. `/parse`, candidate validation, and ABI
preflight use it without downloading compilers. Parsing accepts up to 100 source
files with 1 MB combined source and returns declaration metadata, not a full AST.

`/compile` supports a single file without external imports. It can download and
cache selected solc versions in memory: 0.4.26, 0.5.17, 0.6.12, 0.7.6, and 0.8.20.
This is a bounded version mapping, not an arbitrary pragma resolver; exact
versions may be unavailable. Remote compilation needs network access. Ganache
uses Shanghai; compilation with solc 0.8.20+ targets Shanghai for compatibility.

There is no runtime user authentication or tenant isolation. Disposable candidate
workers bound execution and contain process failures, but are not an OS security
sandbox. Keep the API local; see [security guidance](../SECURITY.md) before exposing
it. Lab state, sessions, and candidate execution results are not persisted.

## Tests

```bash
npm test
```

Tests run offline with the bundled compiler and local EVM. They cover AST spans,
compile/deploy/call, session expiration, plan validation, ABI values, candidate
assertions, constructor overrides, and worker failure/concurrency handling.
Some platforms lack Ganache's native µWS binary and fall back to JavaScript.
