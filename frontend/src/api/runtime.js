export function createRuntimeApi({ runtimeBase, leanBase, fetchImpl = globalThis.fetch }) {
  async function request(base, path, payload, signal, invalidResponseMessage) {
    const response = await fetchImpl(`${base.replace(/\/$/, '')}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal,
    })
    if (!(response.headers.get('content-type') || '').includes('application/json')) {
      throw new Error(invalidResponseMessage)
    }
    return { ok: response.ok, result: await response.json() }
  }

  return {
    compile: (payload, signal) => request(runtimeBase, '/api/runtime/compile', payload, signal, '编译服务返回异常'),
    deploy: (payload, signal) => request(runtimeBase, '/api/runtime/deploy', payload, signal, '运行服务返回异常'),
    call: (payload, signal) => request(runtimeBase, '/api/runtime/call', payload, signal, '运行服务返回异常'),
    checkLean: (payload, signal) => request(leanBase, '/api/lean/check', payload, signal, 'Lean 检查服务返回异常'),
  }
}

export const runtimeApi = createRuntimeApi({
  runtimeBase: import.meta.env?.VITE_RUNTIME_API_BASE || 'http://localhost:19090',
  leanBase: import.meta.env?.VITE_LEAN_API_BASE || 'http://localhost:19100',
})
