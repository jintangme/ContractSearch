const { integerExpression } = require('./integer-expression')
// Shared, type-aware ABI values. Arithmetic uses only the bounded BigInt parser;
// no eval, generated scripts, floating-point conversion or network access.
function resolve(value, accounts, address) {
  if (Array.isArray(value)) return value.map(v => resolve(v, accounts, address))
  if (typeof value === 'string' && /^\$account[0-2]$/.test(value)) return accounts[Number(value.slice(-1))]
  if (value === '$contract') {
    if (!address) throw Error('$contract cannot be used before deployment')
    return address
  }
  return value
}
function typedValue(value, type, accounts, address) {
  if (type?.baseType === 'array' && Array.isArray(value)) return value.map(v => typedValue(v, type.arrayChildren, accounts, address))
  if (type?.baseType === 'tuple' && Array.isArray(value)) return value.map((v,i) => typedValue(v, type.components[i], accounts, address))
  const integer = /^(u?int)([0-9]*)$/.exec(type?.type || '')
  const expressionObject = value && typeof value === 'object' && !Array.isArray(value) && Object.hasOwn(value, 'integer')
  const plainInteger = typeof value === 'string' && /^[+-]?(?:[0-9]+|0[xX][0-9a-fA-F]+)$/.test(value.trim())
  if (!integer && (expressionObject || (type?.type !== 'string' && typeof value === 'string' && !plainInteger && /[()+\-*/%<>&|^]/.test(value))))
    throw Error('Integer arithmetic expressions require an integer ABI field')
  if (integer && (typeof value === 'string' || value && typeof value === 'object' && !Array.isArray(value))) {
    const bits = BigInt(Number(integer[2] || 256)), signed = integer[1] === 'int'
    const low = signed ? -(2n ** (bits - 1n)) : 0n, high = signed ? 2n ** (bits - 1n) - 1n : 2n ** bits - 1n
    const aliases = {
      '$uint256max': 2n ** 256n - 1n, 'maxUint': 2n ** 256n - 1n, 'type(uint256).max': 2n ** 256n - 1n,
      '$int256max': 2n ** 255n - 1n, 'maxInt': 2n ** 255n - 1n, 'type(int256).max': 2n ** 255n - 1n,
      '$int256min': -(2n ** 255n), 'minInt': -(2n ** 255n), 'type(int256).min': -(2n ** 255n),
      '$typeMax': signed ? 2n ** (bits - 1n) - 1n : 2n ** bits - 1n,
      '$typeMin': signed ? -(2n ** (bits - 1n)) : 0n
    }
    const expressionValue = expression => {
      const n = integerExpression(expression, aliases)
      if (n < low || n > high) throw Error(`ABI integer expression is outside ${type.type}`)
      return String(n)
    }
    if (value && typeof value === 'object') {
      if (Object.keys(value).length !== 1 || typeof value.integer !== 'string') throw Error('Use a single integer expression field')
      return expressionValue(value.integer)
    }
    if (Object.hasOwn(aliases,value)) {
      const n = aliases[value]
      if (n < low || n > high) throw Error(`ABI constant ${value} is outside ${type.type}; use $typeMin/$typeMax or the correctly signed explicit constant`)
      return String(n)
    }
    if (plainInteger) {
      // ABI decoders return integers as decimal BigInts. Canonicalize only
      // integer leaves so equivalent literal spellings also match in expected
      // returns/events; textual, bytes and address leaves remain unchanged.
      const text = value.trim(), negative = text.startsWith('-')
      const magnitude = /^[+-]/.test(text) ? text.slice(1) : text
      const n = (negative ? -1n : 1n) * BigInt(magnitude)
      if (n < low || n > high) throw Error(`ABI integer literal is outside ${type.type}`)
      return String(n)
    }
    // Other integer strings use the same exact grammar/resource bounds as
    // {integer: expression}; keep existing account-placeholder resolution.
    if (!/^\$(?:account[0-2]|contract)$/.test(value)) return expressionValue(value)
  }
  return resolve(value, accounts, address)
}
module.exports = { resolve, typedValue }
