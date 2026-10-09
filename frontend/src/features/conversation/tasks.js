export function abortError() {
  return new DOMException('The conversation changed', 'AbortError')
}

// Cancel the transport and also guard against transports that finish after abort.
export function createLatestTaskController() {
  let active = null
  return {
    cancel() {
      active?.abort(abortError())
      active = null
    },
    begin() {
      this.cancel()
      const controller = new AbortController()
      active = controller
      return {
        signal: controller.signal,
        isCurrent: () => active === controller && !controller.signal.aborted,
        assertCurrent() {
          if (active !== controller || controller.signal.aborted) throw abortError()
        },
      }
    },
  }
}

export function abortableDelay(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(signal.reason || abortError())
    const finish = () => {
      signal?.removeEventListener('abort', cancel)
      resolve()
    }
    const timer = setTimeout(finish, ms)
    const cancel = () => {
      clearTimeout(timer)
      signal.removeEventListener('abort', cancel)
      reject(signal.reason || abortError())
    }
    signal?.addEventListener('abort', cancel, { once: true })
  })
}

export async function pollAgentRun(runId, getRun, {
  signal, onProgress = () => {}, intervalMs = 700, timeoutMs = 300000,
} = {}) {
  if (!runId) throw new Error('Agent Run ID missing')
  const controller = new AbortController()
  const cancel = () => controller.abort(signal.reason || abortError())
  if (signal?.aborted) cancel()
  else signal?.addEventListener('abort', cancel, { once: true })
  const timer = setTimeout(() => controller.abort(new Error('Agent 执行超时')), timeoutMs)
  try {
    while (true) {
      await abortableDelay(intervalMs, controller.signal)
      const run = await getRun(runId, { signal: controller.signal })
      if (controller.signal.aborted) throw controller.signal.reason
      onProgress(run)
      if (['COMPLETE', 'FAILED'].includes(run?.state)) return run
    }
  } catch (error) {
    throw controller.signal.aborted ? controller.signal.reason : error
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', cancel)
  }
}
