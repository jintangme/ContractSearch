const test = require('node:test')
const assert = require('node:assert/strict')
const { compileSolidity } = require('../src/compiler')
const { deployContract, callContract, getSession, cleanupExpiredSessions, closeRuntime, SESSION_TTL_MS } = require('../src/runtime')

test('bundled compiler returns diagnostics for invalid source without a remote download', async () => {
  const result = await compileSolidity({ sourceCode: 'pragma solidity ^0.8.20; contract Broken { function }' })
  assert.equal(result.success, false)
  assert.ok(result.errors.length > 0)
  assert.equal(result.selectedCompilerVersion, '0.8.35')
  assert.deepEqual(result.contracts, [])
})

test('compile, deploy and call work together; expired sessions cannot be reused', async (t) => {
  t.after(closeRuntime)
  const compiled = await compileSolidity({ sourceCode: `pragma solidity ^0.8.20;
contract Note {
  string public note;
  function setNote(string memory value) public { note = value; }
}` })
  assert.equal(compiled.success, true, JSON.stringify(compiled.errors))
  const deployment = await deployContract({ ...compiled.contracts[0], constructorArgs: [] })
  assert.equal(deployment.success, true, deployment.message)
  const write = await callContract({ sessionId: deployment.sessionId, functionName: 'setNote', args: ['verified locally'] })
  assert.equal(write.success, true, write.message)
  const read = await callContract({ sessionId: deployment.sessionId, functionName: 'note', args: [] })
  assert.equal(read.success, true, read.message)
  assert.equal(read.returnValue, 'verified locally')
  getSession(deployment.sessionId).lastUsedAt = Date.now() - SESSION_TTL_MS - 1
  cleanupExpiredSessions()
  assert.equal(getSession(deployment.sessionId), null)
  const expired = await callContract({ sessionId: deployment.sessionId, functionName: 'note' })
  assert.equal(expired.errorType, 'SESSION_NOT_FOUND')
})
