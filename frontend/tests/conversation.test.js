import test from 'node:test'
import assert from 'node:assert/strict'
import { createLatestTaskController, pollAgentRun } from '../src/features/conversation/tasks.js'
import { prepareEvidence, evidenceLink, citedEvidenceIds } from '../src/features/conversation/evidence.js'
import { resolveFeatures } from '../src/config/features.js'

test('basic mode defaults to code search; full mode requires explicit opt-in', () => {
  for (const value of [undefined, '', 'false', '1']) {
    assert.deepEqual(resolveFeatures({ VITE_AGENT_ENABLED: value }), {
      agentEnabled: false, homeRoute: '/search', defaultSearchMode: 'code',
    })
  }
  assert.equal(resolveFeatures({ VITE_AGENT_ENABLED: 'true' }).homeRoute, '/conversation')
})

test('a new conversation invalidates both the transport and a late session response', () => {
  const tasks = createLatestTaskController()
  const old = tasks.begin()
  const current = tasks.begin()
  assert.equal(old.signal.aborted, true)
  assert.throws(() => old.assertCurrent(), { name: 'AbortError' })
  assert.equal(current.isCurrent(), true)
  tasks.cancel()
  assert.equal(current.isCurrent(), false)
})

test('run polling displays progress and stops at the terminal snapshot', async () => {
  const states = ['PLAN', 'TOOL_CALL', 'COMPLETE']
  const observed = []
  let calls = 0
  const run = await pollAgentRun('run-1', async (id, { signal }) => {
    assert.equal(id, 'run-1')
    assert.equal(signal.aborted, false)
    return { state: states[calls++] }
  }, { intervalMs: 0, onProgress: snapshot => observed.push(snapshot.state) })
  assert.equal(run.state, 'COMPLETE')
  assert.deepEqual(observed, states)
  assert.equal(calls, 3)
})

test('leaving during an in-flight poll cannot publish a stale response', async () => {
  const tasks = createLatestTaskController()
  const task = tasks.begin()
  let release
  let requestStarted
  const started = new Promise(resolve => { requestStarted = resolve })
  const observed = []
  const polling = pollAgentRun('run-1', async () => {
    requestStarted()
    return new Promise(resolve => { release = resolve })
  }, { signal: task.signal, intervalMs: 0, onProgress: run => observed.push(run) })
  const rejected = assert.rejects(polling, { name: 'AbortError' })
  await started
  tasks.cancel()
  release({ state: 'COMPLETE', answer: 'old answer' })
  await rejected
  assert.deepEqual(observed, [])
})

test('polling has a finite deadline and cancels its next wait', async () => {
  let requests = 0
  await assert.rejects(pollAgentRun('run-1', async () => {
    requests += 1
    return { state: 'PLAN' }
  }, { intervalMs: 100, timeoutMs: 5 }), /Agent 执行超时/)
  assert.equal(requests, 0)
})

test('same-titled evidence keeps distinct files, line ranges and sources', () => {
  const base = { title: 'ERC20', sourceType: 'KNOWLEDGE_CHUNK', sourceRef: 'chunk:a', excerpt: 'source', metadata: { filePath: 'Token.sol', lineStart: 1, lineEnd: 10 } }
  const entries = [
    { ...base, id: 'a' },
    { ...base, id: 'duplicate' },
    { ...base, id: 'b', metadata: { ...base.metadata, lineStart: 20, lineEnd: 30 } },
    { ...base, id: 'c', sourceRef: 'chunk:c' },
    ...Array.from({ length: 6 }, (_, index) => ({ ...base, id: `other-${index}`, sourceRef: `chunk:other-${index}` })),
  ]
  const evidence = prepareEvidence(entries, ['duplicate'])
  assert.equal(evidence.length, 9)
  assert.equal(evidence[0].relatedCount, 2)
  assert.equal(evidence[0].cited, true)
  assert.equal(evidence[1].location, 'Token.sol · L20–L30')
})

test('source links accept HTTP(S) or known contract routes, never executable URLs', () => {
  assert.deepEqual(evidenceLink({ sourceRef: 'contract:12' }), { href: '/contract/12', external: false })
  assert.equal(evidenceLink({ sourceRef: 'javascript:alert(1)' }), null)
  assert.equal(evidenceLink({ metadata: { sourceUrl: 'data:text/html,<script>' } }), null)
  assert.equal(evidenceLink({ metadata: { sourceUrl: 'https://user:secret@example.com' } }), null)
  assert.equal(evidenceLink({ metadata: { sourceUrl: 'https://example.com/spec#L20' } }).href, 'https://example.com/spec#L20')
})

test('only the final model action supplies the cited evidence ordering', () => {
  const events = [
    { type: 'model.action', data: { payload: { actionType: 'TOOL_CALL', citedEvidenceIds: ['a'] } } },
    { type: 'model.action', data: { payload: { actionType: 'FINAL_ANSWER', citedEvidenceIds: ['b'] } } },
  ]
  assert.deepEqual(citedEvidenceIds(events), ['b'])
  assert.equal(prepareEvidence([{ id: 'a' }, { id: 'b' }], citedEvidenceIds(events))[0].id, 'b')
})
