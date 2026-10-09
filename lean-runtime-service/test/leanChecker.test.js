const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const { checkLeanSource } = require('../src/leanChecker')

test('rejects explicit proof gaps and assumptions before invoking Lean', async () => {
  for (const sourceCode of ['example : False := by sorry', 'example : False := by admit', 'axiom unsafeClaim : False']) {
    const result = await checkLeanSource({ sourceCode }, {
      execute: () => { throw new Error('Lean must not run for a rejected proof') }
    })
    assert.equal(result.success, false)
    assert.match(result.errorType, /INCOMPLETE_PROOF|UNSUPPORTED_PROOF_ASSUMPTION/)
    assert.equal(result.errors[0].line, 1)
  }
})

test('ignores markers inside nested comments and strings and removes temporary files', async () => {
  const sourceCode = '/- sorry /- axiom -/ admit -/\n-- sorry\ndef label := "sorry"\nexample : True := by trivial'
  let tempDir
  const result = await checkLeanSource({ sourceCode, fileName: '../Proof.lean' }, {
    execute: async (_bin, filePath, _timeout, cwd) => {
      tempDir = cwd
      assert.equal(await fs.readFile(filePath, 'utf8'), sourceCode)
      assert.equal(filePath, `${cwd}/Proof.lean`)
      return { error: null, stdout: '', stderr: '' }
    }
  })
  assert.equal(result.status, 'passed')
  assert.match(result.message, /only to the submitted Lean source/)
  await assert.rejects(fs.access(tempDir), { code: 'ENOENT' })
})

test('compiler warnings about generated sorry do not count as a complete check', async () => {
  const result = await checkLeanSource({ sourceCode: 'example : True := by generated_tactic' }, {
    execute: async () => ({ error: null, stdout: "Proof.lean:1:0: warning: declaration uses 'sorry'", stderr: '' })
  })
  assert.equal(result.success, false)
  assert.equal(result.errorType, 'INCOMPLETE_PROOF')
  assert.equal(result.errors[0].severity, 'error')
})

test('reports a missing Lean installation without claiming a checked proof', async () => {
  const result = await checkLeanSource({ sourceCode: 'example : True := by trivial' }, {
    execute: async () => ({ error: { code: 'ENOENT' }, stdout: '', stderr: '' })
  })
  assert.equal(result.errorType, 'LEAN_NOT_FOUND')
  assert.equal(result.success, false)
})
