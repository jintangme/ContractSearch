import { readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const projectRoot = resolve(import.meta.dirname, '../..')
const queryPath = resolve(projectRoot, 'docs/agentic-rag/eval/phase0-smoke-queries.json')
const outputPath = resolve(projectRoot, 'docs/agentic-rag/eval/phase0-search-baseline.json')
const backendUrl = process.env.PHASE0_BACKEND_URL || 'http://127.0.0.1:18081'
const userId = String(process.env.PHASE0_USER_ID || '').trim()

if (!userId) {
  throw new Error('PHASE0_USER_ID is required for the current authenticated POST /api/search contract')
}

const queries = JSON.parse(await readFile(queryPath, 'utf8'))
const runs = []

for (const item of queries) {
  const startedAt = performance.now()
  const response = await fetch(`${backendUrl}/api/search`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userId
    },
    body: JSON.stringify({
      input: item.query,
      searchMode: item.mode,
      limit: 10
    }),
    signal: AbortSignal.timeout(75_000)
  })
  const payload = await response.json()
  const data = Array.isArray(payload?.data) ? payload.data : []

  runs.push({
    id: item.id,
    mode: item.mode,
    query: item.query,
    httpStatus: response.status,
    success: response.ok && payload?.status === true,
    latencyMs: Math.round(performance.now() - startedAt),
    requestedLimit: 10,
    returnedCount: data.length,
    top10: data.slice(0, 10).map(result => ({
      id: result.id,
      contractName: result.contractName || null,
      rrfScore: result.rrfScore ?? null
    }))
  })
}

const successfulRuns = runs.filter(run => run.success)
const output = {
  capturedDate: new Date().toISOString().slice(0, 10),
  environment: 'local-development',
  backendUrl,
  authenticatedTestContext: true,
  queryCount: runs.length,
  successCount: successfulRuns.length,
  averageLatencyMs: successfulRuns.length
    ? Math.round(successfulRuns.reduce((sum, run) => sum + run.latencyMs, 0) / successfulRuns.length)
    : null,
  runs
}

await writeFile(outputPath, `${JSON.stringify(output, null, 2)}\n`, 'utf8')
console.log(JSON.stringify({
  outputPath,
  queryCount: output.queryCount,
  successCount: output.successCount,
  averageLatencyMs: output.averageLatencyMs
}))
