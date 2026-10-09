import test from 'node:test'
import assert from 'node:assert/strict'
import { createRuntimeApi } from '../src/api/runtime.js'
import { parseAbiArgument, functionKey, formatRuntimeValue } from '../src/features/contract-lab/abi.js'

test('runtime calls preserve payloads, cancellation and structured compiler errors', async () => {
  const controller = new AbortController()
  let request
  const diagnostic = { success: false, errors: [{ line: 4, message: 'Unexpected token' }] }
  const api = createRuntimeApi({ runtimeBase: '/runtime-api/', leanBase: '/lean-api', fetchImpl: async (url, init) => {
    request = { url, init }
    return new Response(JSON.stringify(diagnostic), { status: 400, headers: { 'Content-Type': 'application/json' } })
  } })
  const payload = { sourceCode: 'contract Demo {}', fileName: 'Demo.sol', compilerVersion: 'auto' }
  const result = await api.compile(payload, controller.signal)
  assert.equal(request.url, '/runtime-api/api/runtime/compile')
  assert.deepEqual(JSON.parse(request.init.body), payload)
  assert.equal(request.init.signal, controller.signal)
  assert.deepEqual(result, { ok: false, result: diagnostic })
})

test('non-JSON proxy failures produce a controlled runtime error', async () => {
  const api = createRuntimeApi({ runtimeBase: '/runtime-api', leanBase: '/lean-api', fetchImpl: async () => new Response('<html>502</html>', { status: 502 }) })
  await assert.rejects(api.deploy({}), /运行服务返回异常/)
})

test('ABI arguments retain integer precision and validate common contract inputs', () => {
  const amount = '115792089237316195423570985008687907853269984665640564039457584007913129639935'
  assert.equal(parseAbiArgument({ type: 'uint256' }, amount, 0), amount)
  assert.equal(parseAbiArgument({ type: 'bool' }, false, 0), false)
  assert.deepEqual(parseAbiArgument({ type: 'uint256[]' }, '[1,2,3]', 0), [1, 2, 3])
  assert.throws(() => parseAbiArgument({ type: 'uint256' }, '-1', 0), /不能为负数/)
  assert.throws(() => parseAbiArgument({ type: 'address' }, '0x123', 0), /地址格式不正确/)
  assert.throws(() => parseAbiArgument({ type: 'bool' }, 'yes', 0), /只能是 true 或 false/)
  assert.equal(functionKey({ name: 'transfer', inputs: [{ type: 'address' }, { type: 'uint256' }] }), 'transfer(address,uint256)')
  assert.equal(formatRuntimeValue({ value: 9007199254740993n }), '{"value":"9007199254740993"}')
})
