export function shortenHash(value) {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 10)}...${value.slice(-6)}` : value
}

export function functionKey(fn) {
  const inputTypes = (fn.inputs || []).map(input => input.type).join(',')
  return `${fn.name}(${inputTypes})`
}

export function getFunctionInputKey(fn, index) {
  return `${functionKey(fn)}::arg::${index}`
}

export function getFunctionValueKey(fn) {
  return `${functionKey(fn)}::value`
}

export function formatFunctionSignature(fn) {
  const params = (fn.inputs || [])
    .map(input => `${input.type}${input.name ? ` ${input.name}` : ''}`)
    .join(', ')
  return `${fn.name}(${params})`
}

export function getInputPlaceholder(input) {
  if (input.type.endsWith('[]')) return '请输入 JSON 数组，例如 [1,2,3]'
  if (input.type.startsWith('uint') || input.type.startsWith('int')) return `请输入 ${input.type} 数值，例如 123`
  if (input.type === 'bool') return '请输入 true 或 false'
  if (input.type === 'address') return '请输入地址，例如 0x...'
  if (input.type.startsWith('bytes')) return '请输入 0x 开头的字节值'
  if (input.type === 'string') return '请输入文本'
  return `请输入 ${input.type}`
}

export function getEmptyArgMessage(input, label) {
  if (input.type.endsWith('[]')) return `${label} 不能为空，请输入 JSON 数组，例如 [1,2,3]`
  if (input.type.startsWith('uint') || input.type.startsWith('int')) return `${label} 不能为空，请输入一个 ${input.type} 数值`
  if (input.type === 'bool') return `${label} 不能为空，请输入 true 或 false`
  if (input.type === 'address') return `${label} 不能为空，请输入一个 address 地址`
  if (input.type.startsWith('bytes')) return `${label} 不能为空，请输入 0x 开头的字节值`
  if (input.type === 'string') return `${label} 不能为空，请输入文本`
  return `${label} 不能为空，请输入 ${input.type}`
}

export function isReadFunction(fn) {
  return fn.stateMutability === 'view' || fn.stateMutability === 'pure'
}

export function formatRuntimeValue(value) {
  if (value === undefined) return 'empty'
  if (value === null) return 'null'
  if (typeof value === 'bigint') return value.toString()
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value, (_key, currentValue) => (
        typeof currentValue === 'bigint' ? currentValue.toString() : currentValue
      ))
    } catch (err) {
      return String(value)
    }
  }
  return String(value)
}

export function normalizeEventArgs(args) {
  if (!args || typeof args !== 'object') return {}
  return Object.fromEntries(
    Object.entries(args).filter(([key]) => Number.isNaN(Number(key)))
  )
}

export function formatStateUpdate(update) {
  if (update.previousValue === undefined) return formatRuntimeValue(update.currentValue)
  return `${formatRuntimeValue(update.previousValue)} -> ${formatRuntimeValue(update.currentValue)}`
}

export function isEmptyArg(value) {
  return value === undefined || value === null || String(value).trim() === ''
}

export function parseAbiArgument(input, rawValue, index) {
  const label = input.name || `参数 ${index + 1}`

  if (isEmptyArg(rawValue)) {
    const message = getEmptyArgMessage(input, label)
    throw new Error(message)
  }

  const value = String(rawValue).trim()

  if (input.type.endsWith('[]')) {
    try {
      const parsed = JSON.parse(value)
      if (!Array.isArray(parsed)) throw new Error('not array')
      return parsed
    } catch (err) {
      const message = `${label} 需要 JSON 数组格式`
      throw new Error(message)
    }
  }

  if (/^u?int(\d+)?$/.test(input.type)) {
    if (!/^-?\d+$/.test(value)) {
      const message = `${label} 必须是整数`
      throw new Error(message)
    }
    if (input.type.startsWith('uint') && value.startsWith('-')) {
      const message = `${label} 不能为负数`
      throw new Error(message)
    }
    return value
  }

  if (input.type === 'bool') {
    if (typeof rawValue === 'boolean') return rawValue
    if (value !== 'true' && value !== 'false') {
      const message = `${label} 只能是 true 或 false`
      throw new Error(message)
    }
    return value === 'true'
  }

  if (input.type === 'address') {
    if (!/^0x[a-fA-F0-9]{40}$/.test(value)) {
      const message = `${label} 地址格式不正确`
      throw new Error(message)
    }
    return value
  }

  if (input.type === 'bytes32') {
    if (!/^0x[a-fA-F0-9]{64}$/.test(value)) {
      const message = `${label} 需要 32 字节 hex`
      throw new Error(message)
    }
    return value
  }

  if (input.type.startsWith('bytes')) {
    if (!/^0x[a-fA-F0-9]*$/.test(value)) {
      const message = `${label} 需要 hex 字符串`
      throw new Error(message)
    }
    return value
  }

  return value
}
