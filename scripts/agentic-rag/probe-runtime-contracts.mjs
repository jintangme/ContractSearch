#!/usr/bin/env node

import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const outputPath = path.join(repositoryRoot, 'docs/agentic-rag/eval/results/runtime-contract-probe.json')
const contractBase = process.env.CONTRACT_RUNTIME_URL || 'http://127.0.0.1:19090'
const leanBase = process.env.LEAN_RUNTIME_URL || 'http://127.0.0.1:19100'

if (process.argv.includes('--check')) {
  process.stdout.write('Runtime contract probe is syntactically ready; no runtime request was sent.\n')
  process.exit(0)
}

const report = {
  probeVersion: '1.0',
  generatedAt: new Date().toISOString(),
  policy: { compileOnly: true, deployCalled: false, contractCallCalled: false, leanToolEnabled: false },
  contractRuntime: {},
  leanRuntime: {},
}

report.contractRuntime.validCompile = await probe(async () => {
  const response = await post(`${contractBase}/api/runtime/compile`, {
    fileName: 'Phase1Probe.sol',
    sourceCode: '// SPDX-License-Identifier: MIT\npragma solidity ^0.8.20; contract Phase1Probe { function ok() external pure returns (bool) { return true; } }',
  }, 15000)
  return {
    httpStatus: response.status,
    success: response.body.success,
    compilerVersion: response.body.compilerVersion,
    contractCount: response.body.contracts?.length || 0,
    errorCount: response.body.errors?.length || 0,
  }
})

report.contractRuntime.invalidCompile = await probe(async () => {
  const response = await post(`${contractBase}/api/runtime/compile`, {
    fileName: 'Broken.sol',
    sourceCode: 'pragma solidity ^0.8.20; contract Broken { function x( external {} }',
  }, 15000, true)
  return {
    httpStatus: response.status,
    success: response.body.success,
    structuredErrors: Array.isArray(response.body.errors),
    errorCount: response.body.errors?.length || 0,
  }
})

report.leanRuntime.basicCheck = await probe(async () => {
  const response = await post(`${leanBase}/api/lean/check`, {
    fileName: 'Phase1Probe.lean',
    sourceCode: 'example : True := by\n  trivial',
  }, 15000, true)
  return {
    httpStatus: response.status,
    success: response.body.success,
    status: response.body.status,
    errorType: response.body.errorType || null,
    errorCount: response.body.errors?.length || 0,
    targetVersion: '4.31.0',
    versionVerified: false
  }
})

await mkdir(path.dirname(outputPath), { recursive: true })
await writeFile(outputPath, `${JSON.stringify(report, null, 2)}\n`)
process.stdout.write(`wrote runtime probe report to ${path.relative(repositoryRoot, outputPath)}\n`)

async function post(url, payload, timeoutMs, acceptErrorStatus = false) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(timeoutMs),
  })
  const body = await response.json()
  if (!response.ok && !acceptErrorStatus) throw new Error(`HTTP_${response.status}`)
  return { status: response.status, body }
}

async function probe(fn) {
  const started = Date.now()
  try {
    const detail = await fn()
    return { outcome: 'OBSERVED', durationMs: Date.now() - started, detail }
  } catch (error) {
    return {
      outcome: error?.name === 'TimeoutError' ? 'TIMEOUT' : 'UNAVAILABLE',
      durationMs: Date.now() - started,
      errorClass: error?.name || 'Error',
      errorCode: String(error?.message || 'UNKNOWN').replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 80),
    }
  }
}
