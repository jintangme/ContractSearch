const crypto = require('crypto')
const ganache = require('ganache')
const { ethers } = require('ethers')

const SESSION_TTL_MS = 30 * 60 * 1000
const CLEANUP_INTERVAL_MS = 5 * 60 * 1000

const sessions = new Map()

let ganacheProvider = null
let ethersProvider = null
let deployerSigner = null
let cleanupTimer = null

class RuntimeError extends Error {
  constructor(message, errorType = 'DEPLOY_FAILED') {
    super(message)
    this.name = 'RuntimeError'
    this.errorType = errorType
  }
}

function normalizeError(err, fallbackType = 'DEPLOY_FAILED') {
  if (err instanceof RuntimeError) {
    return {
      success: false,
      message: err.message,
      errorType: err.errorType
    }
  }

  return {
    success: false,
    message: `${fallbackType === 'CALL_FAILED' ? '调用失败' : '部署失败'}：${err?.shortMessage || err?.reason || err?.message || '未知错误'}`,
    errorType: err?.errorType || fallbackType
  }
}

async function initRuntime() {
  if (ethersProvider && deployerSigner) {
    return {
      provider: ethersProvider,
      signer: deployerSigner
    }
  }

  try {
    ganacheProvider = ganache.provider({
      logging: {
        quiet: true
      },
      wallet: {
        totalAccounts: 5,
        defaultBalance: 1000
      },
      chain: {
        chainId: 1337,
        networkId: 1337
      }
    })

    ethersProvider = new ethers.BrowserProvider(ganacheProvider)
    deployerSigner = await ethersProvider.getSigner(0)

    startSessionCleanup()

    return {
      provider: ethersProvider,
      signer: deployerSigner
    }
  } catch (err) {
    throw new RuntimeError(
      `本地 EVM 沙盒初始化失败：${err.message || '未知错误'}`,
      'PROVIDER_INIT_FAILED'
    )
  }
}

function startSessionCleanup() {
  if (cleanupTimer) return

  cleanupTimer = setInterval(cleanupExpiredSessions, CLEANUP_INTERVAL_MS)
  cleanupTimer.unref?.()
}

function cleanupExpiredSessions() {
  const now = Date.now()

  for (const [sessionId, session] of sessions.entries()) {
    if (now - session.lastUsedAt > SESSION_TTL_MS) {
      sessions.delete(sessionId)
    }
  }
}

function createSessionId() {
  return `runtime_${Date.now()}_${crypto.randomBytes(8).toString('hex')}`
}

function getSession(sessionId) {
  const session = sessions.get(sessionId)
  if (!session) return null

  if (Date.now() - session.lastUsedAt > SESSION_TTL_MS) {
    sessions.delete(sessionId)
    return null
  }

  session.lastUsedAt = Date.now()
  return session
}

function validateAbi(abi) {
  if (!Array.isArray(abi)) {
    throw new RuntimeError('abi 缺失或格式不正确，应为数组', 'INVALID_ABI')
  }
}

function normalizeBytecode(bytecode) {
  if (typeof bytecode !== 'string' || bytecode.trim().length === 0) {
    throw new RuntimeError('bytecode 不能为空', 'INVALID_BYTECODE')
  }

  const trimmed = bytecode.trim()
  const normalized = trimmed.startsWith('0x') ? trimmed : `0x${trimmed}`

  if (!/^0x[0-9a-fA-F]+$/.test(normalized) || normalized === '0x') {
    throw new RuntimeError('bytecode 格式不正确，应为十六进制字符串', 'INVALID_BYTECODE')
  }

  return normalized
}

function validateConstructorArgs(constructorArgs) {
  if (constructorArgs === undefined) return []

  if (!Array.isArray(constructorArgs)) {
    throw new RuntimeError('constructorArgs 应为数组', 'INVALID_CONSTRUCTOR_ARGS')
  }

  return constructorArgs
}

function normalizeCallArgs(args) {
  if (args === undefined) return []

  if (!Array.isArray(args)) {
    throw new RuntimeError('args 应为数组', 'INVALID_ARGS')
  }

  return args
}

function serializeValue(value) {
  if (typeof value === 'bigint') return value.toString()
  if (Array.isArray(value)) return value.map(serializeValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .filter(([key]) => Number.isNaN(Number(key)))
        .map(([key, item]) => [key, serializeValue(item)])
    )
  }
  return value
}

function findFunctionFragment(abi, functionName) {
  if (typeof functionName !== 'string' || !functionName.trim()) {
    throw new RuntimeError('functionName 不能为空', 'FUNCTION_NOT_FOUND')
  }

  const matches = abi.filter(item => item.type === 'function' && item.name === functionName.trim())
  if (matches.length === 0) {
    throw new RuntimeError(`函数 ${functionName} 不存在`, 'FUNCTION_NOT_FOUND')
  }

  if (matches.length > 1) {
    throw new RuntimeError(`函数 ${functionName} 存在重载，当前接口暂不支持重载调用`, 'FUNCTION_OVERLOADED')
  }

  return matches[0]
}

function validateFunctionArgs(fragment, args) {
  const inputCount = Array.isArray(fragment.inputs) ? fragment.inputs.length : 0
  if (args.length !== inputCount) {
    throw new RuntimeError(
      `参数数量不匹配：需要 ${inputCount} 个，收到 ${args.length} 个`,
      'INVALID_ARGS'
    )
  }
}

function normalizeTransactionValue(fragment, value) {
  if (value === undefined || value === null || value === '') return 0n

  const normalized = ethers.parseEther(String(value))
  if (fragment.stateMutability !== 'payable' && normalized > 0n) {
    throw new RuntimeError('nonpayable 函数不能携带 value', 'INVALID_VALUE')
  }

  return normalized
}

function parseReceiptEvents(receipt, abi) {
  const iface = new ethers.Interface(abi)
  const events = []

  for (const log of receipt.logs || []) {
    try {
      const parsed = iface.parseLog(log)
      if (!parsed) continue

      const args = {}
      parsed.fragment.inputs.forEach((input, index) => {
        args[input.name || `arg${index}`] = serializeValue(parsed.args[index])
      })

      events.push({
        eventName: parsed.name,
        args
      })
    } catch (err) {
      // Ignore logs that do not belong to this contract ABI.
    }
  }

  return events
}

function makeSession({
  contractName,
  abi,
  bytecode,
  contractAddress,
  deployer
}) {
  const now = Date.now()
  const sessionId = createSessionId()

  sessions.set(sessionId, {
    sessionId,
    contractName,
    abi,
    bytecode,
    contractAddress,
    deployer,
    deployedAt: now,
    lastUsedAt: now
  })

  return sessionId
}

async function deployContract({
  contractName,
  abi,
  bytecode,
  constructorArgs
}) {
  try {
    validateAbi(abi)
    const normalizedBytecode = normalizeBytecode(bytecode)
    const normalizedConstructorArgs = validateConstructorArgs(constructorArgs)
    const normalizedContractName = typeof contractName === 'string' && contractName.trim()
      ? contractName.trim()
      : 'Contract'

    const { signer } = await initRuntime()
    const factory = new ethers.ContractFactory(abi, normalizedBytecode, signer)
    const contract = await factory.deploy(...normalizedConstructorArgs)
    const deploymentTransaction = contract.deploymentTransaction()
    const receipt = await deploymentTransaction.wait()
    const contractAddress = await contract.getAddress()
    const deployer = await signer.getAddress()
    const sessionId = makeSession({
      contractName: normalizedContractName,
      abi,
      bytecode: normalizedBytecode,
      contractAddress,
      deployer
    })

    return {
      success: true,
      sessionId,
      contractName: normalizedContractName,
      contractAddress,
      deployer,
      transactionHash: receipt.hash,
      gasUsed: receipt.gasUsed?.toString() || '0',
      blockNumber: receipt.blockNumber,
      network: 'Local Sandbox'
    }
  } catch (err) {
    return normalizeError(err)
  }
}

async function callContract({
  sessionId,
  functionName,
  args,
  value
}) {
  try {
    const session = getSession(sessionId)
    if (!session) {
      throw new RuntimeError('当前运行会话不存在或已过期，请重新部署合约', 'SESSION_NOT_FOUND')
    }

    const fragment = findFunctionFragment(session.abi, functionName)
    const normalizedArgs = normalizeCallArgs(args)
    validateFunctionArgs(fragment, normalizedArgs)

    const { signer } = await initRuntime()
    const contract = new ethers.Contract(session.contractAddress, session.abi, signer)
    const mutability = fragment.stateMutability || 'nonpayable'

    if (mutability === 'view' || mutability === 'pure') {
      const returnValue = await contract[fragment.name](...normalizedArgs)
      const decodedReturnValue = Array.isArray(returnValue)
        ? serializeValue(returnValue)
        : [serializeValue(returnValue)]

      session.lastUsedAt = Date.now()

      return {
        success: true,
        type: 'call',
        functionName: fragment.name,
        returnValue: decodedReturnValue.length === 1
          ? String(decodedReturnValue[0])
          : JSON.stringify(decodedReturnValue),
        decodedReturnValue
      }
    }

    const txValue = normalizeTransactionValue(fragment, value)
    const transaction = await contract[fragment.name](...normalizedArgs, { value: txValue })
    const receipt = await transaction.wait()
    const events = parseReceiptEvents(receipt, session.abi)
    session.lastUsedAt = Date.now()

    return {
      success: true,
      type: 'transaction',
      functionName: fragment.name,
      transactionHash: receipt.hash,
      gasUsed: receipt.gasUsed?.toString() || '0',
      blockNumber: receipt.blockNumber,
      events
    }
  } catch (err) {
    return normalizeError(err, 'CALL_FAILED')
  }
}

module.exports = {
  SESSION_TTL_MS,
  callContract,
  cleanupExpiredSessions,
  deployContract,
  getSession,
  initRuntime,
  normalizeError
}
