export const AI_MODEL_STORAGE_KEY = 'contractSearch.ai.defaultModel'

export const AI_MODEL_OPTIONS = [
  { label: 'GLM-5.1', value: 'glm-5.1', desc: '旗舰模型，适合复杂代码修复与深度审查' },
  { label: 'GLM-5-Turbo', value: 'glm-5-turbo', desc: '高性能模型，适合复杂分析' },
  { label: 'GLM-5', value: 'glm-5', desc: '通用高阶模型' },
  { label: 'GLM-4.7', value: 'glm-4.7', desc: '适合日常代码分析和交互' },
  { label: 'GLM-4.7-FlashX', value: 'glm-4.7-flashx', desc: '快速增强模型' },
  { label: 'GLM-4.7-Flash', value: 'glm-4.7-flash', desc: '轻量快速模型' },
  { label: 'GLM-4.6', value: 'glm-4.6', desc: '稳定通用模型' },
  { label: 'GLM-4.5-AirX', value: 'glm-4.5-airx', desc: '轻量增强模型' },
  { label: 'GLM-4.5-Air', value: 'glm-4.5-air', desc: '轻量通用模型' },
  { label: 'GLM-4.5-Flash', value: 'glm-4.5-flash', desc: '快速经济模型' },
  { label: 'GLM-4-FlashX', value: 'glm-4-flashx-250414', desc: 'GLM-4 快速增强模型' },
  { label: 'GLM-4-Flash', value: 'glm-4-flash-250414', desc: 'GLM-4 快速模型' }
]

export const DEFAULT_AI_MODEL = 'glm-5.1'

export function isAllowedAiModel(model) {
  return AI_MODEL_OPTIONS.some(option => option.value === model)
}

export function getStoredAiModel() {
  const stored = localStorage.getItem(AI_MODEL_STORAGE_KEY)
  return isAllowedAiModel(stored) ? stored : DEFAULT_AI_MODEL
}

export function persistAiModel(model) {
  const nextModel = isAllowedAiModel(model) ? model : DEFAULT_AI_MODEL
  localStorage.setItem(AI_MODEL_STORAGE_KEY, nextModel)
  return nextModel
}
