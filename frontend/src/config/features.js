export function resolveFeatures(env = {}) {
  const agentEnabled = env.VITE_AGENT_ENABLED === 'true'
  return Object.freeze({
    agentEnabled,
    homeRoute: agentEnabled ? '/conversation' : '/search',
    defaultSearchMode: agentEnabled ? 'semantic' : 'code',
  })
}

export const features = resolveFeatures(import.meta.env)
