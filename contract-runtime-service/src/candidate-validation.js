const path = require('node:path')
const { fork } = require('node:child_process')
const { validatePlan, digest, planHash } = require('./candidate-plan')
let active = 0
const MAX_ACTIVE = 2
const TIMEOUT_MS = 25000

async function validateCandidate(payload) {
  if (!payload || Object.keys(payload).some(k => !['sourceCode', 'plan', 'planHash'].includes(k))) throw Error('Unknown candidate payload fields')
  validatePlan(payload.plan)
  if (payload.planHash !== planHash(payload.plan)) throw Error('Plan hash mismatch')
  if (typeof payload.sourceCode !== 'string' || !payload.sourceCode.trim() || payload.sourceCode.length > 24000) throw Error('Candidate source must contain 1 to 24000 characters')
  if (active >= MAX_ACTIVE) return { status: 'BUSY', allChecksPassed: false, sourceHash: digest(payload.sourceCode), planHash: payload.planHash }
  active++
  let worker
  try {
    const env = Object.fromEntries(['PATH','LANG','TZ','TMPDIR','TMP','TEMP'].filter(k => process.env[k] !== undefined).map(k => [k,process.env[k]]))
    worker = fork(path.join(__dirname, 'candidate-worker.js'), [], {
      execArgv:['--max-old-space-size=256'], env,
      stdio:['ignore','ignore','pipe','ipc'], serialization:'json'
    })
    return await new Promise(resolve => {
      let settled = false, stderr = ''
      worker.stderr.on('data', chunk => { stderr = (stderr + String(chunk)).slice(-1500) })
      const finish = result => {
        if (settled) return
        settled = true; clearTimeout(timer); resolve({ ...result, isolation:'DISPOSABLE_PROCESS' })
      }
      const failure = status => ({ status, allChecksPassed: false, sourceHash: digest(payload.sourceCode), planHash: payload.planHash })
      const timer = setTimeout(() => finish(failure('TIMEOUT')), TIMEOUT_MS)
      worker.once('message', finish)
      worker.once('error', () => finish(failure('WORKER_ERROR')))
      worker.once('exit', (exitCode, signal) => finish({ ...failure('WORKER_EXIT'), exitCode, signal, diagnostic:stderr }))
      worker.send(payload, error => { if (error) finish({ ...failure('WORKER_ERROR'), message:error.code || 'IPC_ERROR' }) })
    })
  } finally {
    try {
      // A failed spawn has no PID and may emit close without ever emitting exit.
      if (worker?.pid && worker.exitCode === null && worker.signalCode === null) await new Promise(resolve => {
        const timer = setTimeout(() => worker.kill('SIGKILL'), 1500)
        worker.once('exit', () => { clearTimeout(timer); resolve() })
        worker.kill('SIGTERM')
      })
    } finally { active-- }
  }
}
module.exports = { validateCandidate, TIMEOUT_MS }
