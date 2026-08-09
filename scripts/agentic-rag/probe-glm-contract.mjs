#!/usr/bin/env node

import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const outputPath = path.join(repositoryRoot, 'docs/agentic-rag/eval/results/glm-4.5-air-contract-probe.json')
const baseUrl = (process.env.ZHIPU_BASE_URL || 'https://open.bigmodel.cn/api/paas/v4').replace(/\/$/, '')
const model = process.env.AGENT_MODEL || 'glm-4.5-air'
const apiKey = process.env.ZHIPU_API_KEY || ''
const checkOnly = process.argv.includes('--check')

if (checkOnly) {
  process.stdout.write('GLM contract probe script is syntactically ready; no network request was sent.\n')
  process.exit(0)
}
if (!apiKey) {
  await mkdir(path.dirname(outputPath), { recursive: true })
  await writeFile(outputPath, `${JSON.stringify({
    probeVersion: '1.0',
    provider: 'zhipu',
    model,
    baseUrl,
    generatedAt: new Date().toISOString(),
    containsCredentials: false,
    status: 'BLOCKED_MISSING_ZHIPU_API_KEY',
    checks: {},
  }, null, 2)}\n`)
  process.stderr.write('GLM live contract probe blocked: export ZHIPU_API_KEY and rerun this script.\n')
  process.exit(2)
}

const tool = {
  type: 'function',
  function: {
    name: 'get_contract__v1',
    description: 'Return one contract by numeric id.',
    parameters: {
      type: 'object',
      additionalProperties: false,
      required: ['contractId'],
      properties: { contractId: { type: 'integer', minimum: 1 } },
    },
  },
}
const messages = [
  { role: 'system', content: 'You are a protocol test. Call the supplied tool exactly once and do not answer directly.' },
  { role: 'user', content: 'Get contract 1.' },
]

const report = {
  probeVersion: '1.0',
  provider: 'zhipu',
  model,
  baseUrl,
  generatedAt: new Date().toISOString(),
  containsCredentials: false,
  checks: {},
}

report.checks.synchronousToolCall = await runCheck(async () => {
  const response = await post({
    model, messages, tools: [tool], tool_choice: 'auto', temperature: 0.1,
    thinking: { type: 'disabled' }, stream: false,
  }, 60000)
  return summarizeJsonResponse(response)
})

report.checks.streamingToolCall = await runCheck(async () => {
  const response = await fetch(`${baseUrl}/chat/completions`, {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({
      model, messages, tools: [tool], tool_choice: 'auto', temperature: 0.1,
      thinking: { type: 'disabled' }, stream: true,
    }),
    signal: AbortSignal.timeout(60000),
  })
  const body = await response.text()
  if (!response.ok) throw new Error(`HTTP_${response.status}`)
  const chunks = body.split(/\r?\n/)
    .filter(line => line.startsWith('data:') && !line.includes('[DONE]'))
    .map(line => JSON.parse(line.slice(5).trim()))
  const toolDeltaCount = chunks.filter(chunk => chunk.choices?.some(choice => choice.delta?.tool_calls)).length
  return { httpStatus: response.status, chunkCount: chunks.length, toolDeltaCount, hasDone: body.includes('[DONE]') }
})

report.checks.thinkingEnabled = await runCheck(async () => {
  const response = await post({
    model, messages, tools: [tool], tool_choice: 'auto', temperature: 0.1,
    thinking: { type: 'enabled' }, stream: false,
  }, 60000)
  return summarizeJsonResponse(response)
})

report.checks.invalidParameter = await runCheck(async () => {
  const response = await fetch(`${baseUrl}/chat/completions`, {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ model, messages, temperature: -100 }),
    signal: AbortSignal.timeout(10000),
  })
  return { httpStatus: response.status, rejected: response.status >= 400 }
}, false)

report.checks.clientTimeout = await runCheck(async () => {
  await fetch(`${baseUrl}/chat/completions`, {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ model, messages }),
    signal: AbortSignal.timeout(1),
  })
  return { aborted: false }
}, false)

await mkdir(path.dirname(outputPath), { recursive: true })
await writeFile(outputPath, `${JSON.stringify(report, null, 2)}\n`)
process.stdout.write(`wrote sanitized probe report to ${path.relative(repositoryRoot, outputPath)}\n`)

async function post(payload, timeoutMs) {
  const response = await fetch(`${baseUrl}/chat/completions`, {
    method: 'POST', headers: headers(), body: JSON.stringify(payload), signal: AbortSignal.timeout(timeoutMs),
  })
  const body = await response.text()
  if (!response.ok) throw new Error(`HTTP_${response.status}`)
  return { status: response.status, json: JSON.parse(body) }
}

function summarizeJsonResponse(response) {
  const message = response.json.choices?.[0]?.message || {}
  return {
    httpStatus: response.status,
    finishReason: response.json.choices?.[0]?.finish_reason || null,
    toolCallCount: Array.isArray(message.tool_calls) ? message.tool_calls.length : 0,
    toolNames: (message.tool_calls || []).map(call => call.function?.name).filter(Boolean),
    hasReasoningContent: Boolean(message.reasoning_content),
    usage: response.json.usage || null,
  }
}

async function runCheck(fn, successOnReturn = true) {
  const started = Date.now()
  try {
    const detail = await fn()
    return { outcome: successOnReturn ? 'ACCEPTED' : 'OBSERVED', durationMs: Date.now() - started, detail }
  } catch (error) {
    const isTimeout = error?.name === 'TimeoutError' || error?.name === 'AbortError'
    return {
      outcome: isTimeout ? 'TIMEOUT_ENFORCED' : 'REJECTED',
      durationMs: Date.now() - started,
      errorClass: error?.name || 'Error',
      errorCode: String(error?.message || 'UNKNOWN').replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 80),
    }
  }
}

function headers() {
  return { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' }
}
