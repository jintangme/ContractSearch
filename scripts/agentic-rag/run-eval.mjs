#!/usr/bin/env node

import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const defaults = {
  fixture: path.join(repositoryRoot, 'docs/agentic-rag/eval/golden-set-v0.1.json'),
  out: path.join(repositoryRoot, 'docs/agentic-rag/eval/results/phase1-harness.jsonl'),
  adapter: 'mock',
}

const args = parseArgs(process.argv.slice(2))
const fixture = JSON.parse(await readFile(args.fixture, 'utf8'))
validateFixture(fixture)

if (args.check) {
  process.stdout.write(`validated ${fixture.cases.length} cases (${fixture.fixtureVersion})\n`)
  process.exit(0)
}

if (args.adapter !== 'mock') {
  throw new Error('Phase 1 supports only the deterministic mock adapter; live adapters arrive in Phase 1A')
}

const variants = ['DIRECT_GLM', 'NAIVE_RAG', 'AGENTIC_RAG']
const generatedAt = new Date().toISOString()
const rows = []
for (const testCase of fixture.cases) {
  for (const variant of variants) {
    rows.push(runMock(testCase, variant, fixture, generatedAt))
  }
}

await mkdir(path.dirname(args.out), { recursive: true })
await writeFile(args.out, `${rows.map(row => JSON.stringify(row)).join('\n')}\n`)
const summaryPath = args.out.replace(/\.jsonl$/, '.summary.json')
const summary = {
  schemaVersion: '1.0',
  fixtureVersion: fixture.fixtureVersion,
  adapter: args.adapter,
  generatedAt,
  qualityEvaluation: false,
  note: 'Harness-only deterministic output. These rows are not model quality scores.',
  caseCount: fixture.cases.length,
  runCount: rows.length,
  variants: Object.fromEntries(variants.map(variant => [variant, rows.filter(row => row.variant === variant).length])),
}
await writeFile(summaryPath, `${JSON.stringify(summary, null, 2)}\n`)
process.stdout.write(`wrote ${rows.length} JSONL rows to ${path.relative(repositoryRoot, args.out)}\n`)

function runMock(testCase, variant, source, generatedAt) {
  const deterministicInput = JSON.stringify({ fixture: source.fixtureVersion, id: testCase.id, variant })
  return {
    schemaVersion: '1.0',
    fixtureVersion: source.fixtureVersion,
    fixtureHash: sha256(JSON.stringify(source)),
    caseId: testCase.id,
    category: testCase.category,
    variant,
    modelProfile: 'glm-4.5-air',
    adapter: 'mock',
    status: 'HARNESS_OK',
    terminalState: 'COMPLETE',
    latencyMs: 0,
    toolCalls: variant === 'AGENTIC_RAG' ? testCase.expected.allowedTools.slice(0, 1) : [],
    evidenceIds: variant === 'DIRECT_GLM' ? [] : [`fixture:${testCase.id}`],
    outputHash: sha256(deterministicInput),
    generatedAt,
  }
}

function validateFixture(fixture) {
  if (fixture.schemaVersion !== '1.0' || !Array.isArray(fixture.cases)) {
    throw new Error('Unsupported golden-set envelope')
  }
  if (fixture.cases.length < 30) {
    throw new Error(`At least 30 seed cases are required; found ${fixture.cases.length}`)
  }
  const ids = new Set()
  for (const testCase of fixture.cases) {
    if (!/^CS-[0-9]{3}$/.test(testCase.id) || ids.has(testCase.id)) {
      throw new Error(`Invalid or duplicate case id: ${testCase.id}`)
    }
    ids.add(testCase.id)
    if (!testCase.task || !Array.isArray(testCase.expected?.capabilities)
        || !Array.isArray(testCase.expected?.answerCriteria)) {
      throw new Error(`Incomplete case: ${testCase.id}`)
    }
  }
}

function parseArgs(argv) {
  const parsed = { ...defaults, check: false }
  for (let index = 0; index < argv.length; index += 1) {
    if (argv[index] === '--fixture') parsed.fixture = path.resolve(argv[++index])
    else if (argv[index] === '--out') parsed.out = path.resolve(argv[++index])
    else if (argv[index] === '--adapter') parsed.adapter = argv[++index]
    else if (argv[index] === '--check') parsed.check = true
    else throw new Error(`Unknown argument: ${argv[index]}`)
  }
  return parsed
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}
