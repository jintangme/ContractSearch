const solc = require('solc')

const DEFAULT_FILE_NAME = 'Contract.sol'
const MAX_SOURCE_BYTES = 1024 * 1024
const REMOTE_COMPILER_RELEASES = {
  '0.4': 'v0.4.26+commit.4563c3fc',
  '0.4.26': 'v0.4.26+commit.4563c3fc',
  '0.5': 'v0.5.17+commit.d19bba13',
  '0.5.17': 'v0.5.17+commit.d19bba13',
  '0.6': 'v0.6.12+commit.27d51765',
  '0.6.12': 'v0.6.12+commit.27d51765',
  '0.7': 'v0.7.6+commit.7338295f',
  '0.7.6': 'v0.7.6+commit.7338295f',
  '0.8.20': 'v0.8.20+commit.a1b79de6'
}
const compilerCache = new Map()

function makeDiagnostic({
  severity = 'error',
  message,
  formattedMessage,
  line = null,
  column = null
}) {
  return {
    severity,
    message,
    formattedMessage: formattedMessage || message,
    line,
    column
  }
}

function getLineColumnFromOffset(sourceCode, offset) {
  if (typeof offset !== 'number' || offset < 0) {
    return { line: null, column: null }
  }

  let line = 1
  let column = 1

  for (let index = 0; index < offset && index < sourceCode.length; index += 1) {
    if (sourceCode[index] === '\n') {
      line += 1
      column = 1
    } else {
      column += 1
    }
  }

  return { line, column }
}

function getLineColumnFromFormattedMessage(formattedMessage) {
  const match = formattedMessage?.match(/:(\d+):(\d+):/)
  if (!match) {
    return { line: null, column: null }
  }

  return {
    line: Number(match[1]),
    column: Number(match[2])
  }
}

function normalizeSolcMessage(item, sourceCode) {
  const fromLocation = getLineColumnFromOffset(sourceCode, item.sourceLocation?.start)
  const fromMessage = getLineColumnFromFormattedMessage(item.formattedMessage)
  const line = fromLocation.line ?? fromMessage.line
  const column = fromLocation.column ?? fromMessage.column

  return makeDiagnostic({
    severity: item.severity || 'error',
    message: item.message || item.formattedMessage || 'Solidity compile diagnostic',
    formattedMessage: item.formattedMessage || item.message,
    line,
    column
  })
}

function hasUnsupportedImport(sourceCode) {
  return /^\s*import\s+["'{*]/m.test(sourceCode)
}

function parseVersion(version) {
  const match = String(version || '').match(/(\d+)\.(\d+)\.(\d+)/)
  if (!match) return null
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
    raw: `${match[1]}.${match[2]}.${match[3]}`
  }
}

function compareVersions(a, b) {
  const left = typeof a === 'string' ? parseVersion(a) : a
  const right = typeof b === 'string' ? parseVersion(b) : b
  if (!left || !right) return 0
  if (left.major !== right.major) return left.major - right.major
  if (left.minor !== right.minor) return left.minor - right.minor
  return left.patch - right.patch
}

function detectSolidityPragma(sourceCode) {
  const match = String(sourceCode || '').match(/pragma\s+solidity\s+([^;]+);/i)
  if (!match) {
    return {
      expression: '',
      versions: [],
      preferredVersion: null,
      majorMinor: null
    }
  }

  const expression = match[1].trim()
  const versions = [...expression.matchAll(/\d+\.\d+\.\d+/g)].map(item => item[0])
  const first = versions[0] ? parseVersion(versions[0]) : null

  return {
    expression,
    versions,
    preferredVersion: first?.raw || null,
    majorMinor: first ? `${first.major}.${first.minor}` : null
  }
}

function currentCompilerVersion() {
  return parseVersion(solc.version())?.raw || solc.version()
}

function isCurrentCompilerCompatible(pragmaInfo) {
  if (!pragmaInfo.expression) return true

  const current = parseVersion(currentCompilerVersion())
  if (!current) return true

  const expression = pragmaInfo.expression
  const exact = expression.match(/^\s*(\d+\.\d+\.\d+)\s*$/)
  if (exact) return compareVersions(current, exact[1]) === 0

  const caret = expression.match(/\^\s*(\d+)\.(\d+)\.(\d+)/)
  if (caret) {
    const min = parseVersion(caret[0])
    if (!min) return true
    const upper = min.major === 0
      ? { major: 0, minor: min.minor + 1, patch: 0 }
      : { major: min.major + 1, minor: 0, patch: 0 }
    return compareVersions(current, min) >= 0 && compareVersions(current, upper) < 0
  }

  const gte = expression.match(/>=\s*(\d+\.\d+\.\d+)/)
  const gt = expression.match(/>\s*(\d+\.\d+\.\d+)/)
  const lte = expression.match(/<=\s*(\d+\.\d+\.\d+)/)
  const lt = expression.match(/<\s*(\d+\.\d+\.\d+)/)
  if (gte && compareVersions(current, gte[1]) < 0) return false
  if (gt && !gte && compareVersions(current, gt[1]) <= 0) return false
  if (lte && compareVersions(current, lte[1]) > 0) return false
  if (lt && !lte && compareVersions(current, lt[1]) >= 0) return false

  return true
}

function normalizeRequestedCompilerVersion(value) {
  if (!value || value === 'auto') return ''
  return String(value).replace(/^solc\s*/i, '').trim()
}

function selectRemoteCompilerRelease(pragmaInfo, requestedCompilerVersion = '') {
  const requested = normalizeRequestedCompilerVersion(requestedCompilerVersion)
  if (requested && REMOTE_COMPILER_RELEASES[requested]) {
    return REMOTE_COMPILER_RELEASES[requested]
  }

  if (!pragmaInfo.expression || isCurrentCompilerCompatible(pragmaInfo)) return null

  const exact = pragmaInfo.expression.match(/^\s*(\d+\.\d+\.\d+)\s*$/)
  if (exact && REMOTE_COMPILER_RELEASES[exact[1]]) {
    return REMOTE_COMPILER_RELEASES[exact[1]]
  }

  if (pragmaInfo.majorMinor && REMOTE_COMPILER_RELEASES[pragmaInfo.majorMinor]) {
    return REMOTE_COMPILER_RELEASES[pragmaInfo.majorMinor]
  }

  if (pragmaInfo.preferredVersion && pragmaInfo.preferredVersion.startsWith('0.8.20')) {
    return REMOTE_COMPILER_RELEASES['0.8.20']
  }

  return null
}

function loadRemoteCompiler(release) {
  if (compilerCache.has(release)) return compilerCache.get(release)

  const promise = new Promise((resolve, reject) => {
    solc.loadRemoteVersion(release, (err, compiler) => {
      if (err) {
        reject(err)
        return
      }
      resolve(compiler)
    })
  })

  compilerCache.set(release, promise)
  return promise
}

async function getCompilerForSource(sourceCode, requestedCompilerVersion = '') {
  const pragma = detectSolidityPragma(sourceCode)
  const bundledVersion = currentCompilerVersion()
  const requested = normalizeRequestedCompilerVersion(requestedCompilerVersion)
  const remoteRelease = selectRemoteCompilerRelease(pragma, requested)
  const manualMode = Boolean(requested)

  if (manualMode && requested === bundledVersion) {
    return {
      compiler: solc,
      pragma,
      bundledVersion,
      selectedCompilerVersion: bundledVersion,
      remoteRelease: null,
      versionMismatch: false,
      compilerMode: 'manual'
    }
  }

  if (!remoteRelease) {
    return {
      compiler: solc,
      pragma,
      bundledVersion,
      selectedCompilerVersion: bundledVersion,
      remoteRelease: null,
      versionMismatch: Boolean(!manualMode && pragma.expression && !isCurrentCompilerCompatible(pragma)),
      compilerMode: manualMode ? 'manual' : 'auto'
    }
  }

  try {
    const compiler = await loadRemoteCompiler(remoteRelease)
    return {
      compiler,
      pragma,
      bundledVersion,
      selectedCompilerVersion: parseVersion(compiler.version())?.raw || compiler.version(),
      remoteRelease,
      versionMismatch: false,
      compilerMode: manualMode ? 'manual' : 'auto'
    }
  } catch (err) {
    return {
      compiler: solc,
      pragma,
      bundledVersion,
      selectedCompilerVersion: bundledVersion,
      remoteRelease,
      versionMismatch: true,
      compilerMode: manualMode ? 'manual' : 'auto',
      compilerLoadError: err.message || '无法加载匹配的 Solidity 编译器'
    }
  }
}

function validateSource(sourceCode) {
  if (typeof sourceCode !== 'string' || sourceCode.trim().length === 0) {
    return makeDiagnostic({
      message: 'sourceCode 不能为空',
      formattedMessage: 'sourceCode 不能为空'
    })
  }

  if (Buffer.byteLength(sourceCode, 'utf8') > MAX_SOURCE_BYTES) {
    return makeDiagnostic({
      message: 'sourceCode 超过 1MB 限制',
      formattedMessage: 'sourceCode 超过 1MB 限制'
    })
  }

  if (hasUnsupportedImport(sourceCode)) {
    return makeDiagnostic({
      message: '当前编译服务仅支持单文件源码，暂不处理外部 import',
      formattedMessage: '当前编译服务仅支持单文件源码，暂不处理外部 import'
    })
  }

  return null
}

function buildStandardInput(sourceCode, fileName, compilerVersion) {
  return {
    language: 'Solidity',
    sources: {
      [fileName]: {
        content: sourceCode
      }
    },
    settings: {
      // Ganache 7's local chain runs Shanghai. Newer solc defaults may emit
      // opcodes for a later fork, which compile successfully but fail at runtime.
      ...(compareVersions(compilerVersion, '0.8.20') >= 0 ? { evmVersion: 'shanghai' } : {}),
      optimizer: {
        enabled: false,
        runs: 200
      },
      outputSelection: {
        '*': {
          '*': ['abi', 'evm.bytecode.object']
        }
      }
    }
  }
}

function extractContracts(output, fileName) {
  const compiledFile = output.contracts?.[fileName] || {}

  return Object.entries(compiledFile).map(([contractName, contract]) => {
    const bytecodeObject = contract.evm?.bytecode?.object || ''

    return {
      contractName,
      abi: contract.abi || [],
      bytecode: bytecodeObject ? `0x${bytecodeObject}` : '0x'
    }
  })
}

function findPrimaryContractName(contracts) {
  return contracts.find(contract => contract.bytecode && contract.bytecode !== '0x')?.contractName
    || contracts[0]?.contractName
    || null
}

async function compileSolidity({ sourceCode, fileName = DEFAULT_FILE_NAME, compilerVersion = 'auto' }) {
  const normalizedFileName = typeof fileName === 'string' && fileName.trim()
    ? fileName.trim()
    : DEFAULT_FILE_NAME
  const detectedPragma = detectSolidityPragma(sourceCode)
  const bundledCompilerVersion = currentCompilerVersion()

  const validationError = validateSource(sourceCode)
  if (validationError) {
    return {
      success: false,
      contractName: null,
      contracts: [],
      errors: [validationError],
      warnings: [],
      detectedPragma,
      compilerVersion: bundledCompilerVersion,
      selectedCompilerVersion: bundledCompilerVersion,
      versionMismatch: false,
      compilerMode: normalizeRequestedCompilerVersion(compilerVersion) ? 'manual' : 'auto'
    }
  }

  try {
    const compilerContext = await getCompilerForSource(sourceCode, compilerVersion)
    if (compilerContext.compilerLoadError) {
      return {
        success: false,
        contractName: null,
        contracts: [],
        errors: [
          makeDiagnostic({
            message: `当前源码要求 Solidity ${compilerContext.pragma.expression}，但本地编译器为 ${compilerContext.bundledVersion}，且匹配编译器加载失败`,
            formattedMessage: `Compiler version mismatch\nRequired pragma: ${compilerContext.pragma.expression}\nBundled compiler: ${compilerContext.bundledVersion}\nTried compiler: ${compilerContext.remoteRelease || '-'}\nReason: ${compilerContext.compilerLoadError}`
          })
        ],
        warnings: [],
        detectedPragma: compilerContext.pragma,
        compilerVersion: compilerContext.bundledVersion,
        selectedCompilerVersion: compilerContext.selectedCompilerVersion,
        targetCompilerRelease: compilerContext.remoteRelease,
        versionMismatch: true,
        compilerMode: compilerContext.compilerMode
      }
    }

    const input = buildStandardInput(sourceCode, normalizedFileName, compilerContext.selectedCompilerVersion)
    const output = JSON.parse(compilerContext.compiler.compile(JSON.stringify(input)))
    const diagnostics = (output.errors || []).map(item => normalizeSolcMessage(item, sourceCode))
    const errors = diagnostics.filter(item => item.severity === 'error')
    const warnings = diagnostics.filter(item => item.severity !== 'error')
    const contracts = errors.length > 0 ? [] : extractContracts(output, normalizedFileName)
    const contractName = errors.length > 0 ? null : findPrimaryContractName(contracts)
    const hasCompilerVersionError = errors.some(item => (
      /requires different compiler version|current compiler/i.test(`${item.message}\n${item.formattedMessage}`)
    ))

    return {
      success: errors.length === 0,
      contractName,
      contracts,
      errors,
      warnings,
      detectedPragma: compilerContext.pragma,
      compilerVersion: compilerContext.bundledVersion,
      selectedCompilerVersion: compilerContext.selectedCompilerVersion,
      targetCompilerRelease: compilerContext.remoteRelease,
      versionMismatch: compilerContext.versionMismatch || hasCompilerVersionError,
      compilerMode: compilerContext.compilerMode
    }
  } catch (err) {
    return {
      success: false,
      contractName: null,
      contracts: [],
      errors: [
        makeDiagnostic({
          message: err.message || 'Solidity 编译服务异常',
          formattedMessage: err.stack || err.message || 'Solidity 编译服务异常'
        })
      ],
      warnings: [],
      detectedPragma,
      compilerVersion: bundledCompilerVersion,
      selectedCompilerVersion: bundledCompilerVersion,
      versionMismatch: false,
      compilerMode: normalizeRequestedCompilerVersion(compilerVersion) ? 'manual' : 'auto'
    }
  }
}

module.exports = {
  compileSolidity,
  detectSolidityPragma,
  MAX_SOURCE_BYTES
}
