const crypto = require('crypto')
const solc = require('solc')

const MAX_SOURCE_BYTES = 1024 * 1024
const MAX_SOURCE_FILES = 100

function compilerVersion() {
  const match = String(solc.version()).match(/\d+\.\d+\.\d+/)
  return match?.[0] || solc.version()
}

function diagnostic(item) {
  return {
    severity: item.severity || 'error',
    message: item.message || item.formattedMessage || 'Solidity parser diagnostic',
    formattedMessage: item.formattedMessage || item.message || 'Solidity parser diagnostic',
    sourceLocation: item.sourceLocation || null
  }
}

function validateSources(sources) {
  if (!sources || typeof sources !== 'object' || Array.isArray(sources)) {
    return 'sources must be an object keyed by Solidity file path'
  }
  const entries = Object.entries(sources)
  if (entries.length === 0 || entries.length > MAX_SOURCE_FILES) {
    return `sources must contain between 1 and ${MAX_SOURCE_FILES} files`
  }
  let bytes = 0
  for (const [path, value] of entries) {
    if (!path || path.length > 500 || typeof value !== 'string') {
      return 'each source must have a bounded path and string content'
    }
    bytes += Buffer.byteLength(value, 'utf8')
  }
  return bytes > MAX_SOURCE_BYTES ? 'combined source size exceeds 1MB' : null
}

function sourceRange(content, src) {
  const [startText, lengthText] = String(src || '').split(':')
  const startByte = Number(startText)
  const lengthByte = Number(lengthText)
  if (!Number.isInteger(startByte) || !Number.isInteger(lengthByte) || startByte < 0 || lengthByte <= 0) {
    return null
  }
  const bytes = Buffer.from(content, 'utf8')
  const endByte = Math.min(bytes.length, startByte + lengthByte)
  const before = bytes.subarray(0, startByte).toString('utf8')
  const declaration = bytes.subarray(startByte, endByte).toString('utf8')
  const startChar = before.length
  const endChar = startChar + declaration.length
  const lineStart = before.split('\n').length
  const lineEnd = lineStart + declaration.split('\n').length - 1
  return { startByte, lengthByte: endByte - startByte, startChar, endChar, lineStart, lineEnd, declaration }
}

function signatureOf(content) {
  const brace = content.indexOf('{')
  const semicolon = content.indexOf(';')
  const candidates = [brace, semicolon].filter(index => index >= 0)
  const end = candidates.length > 0 ? Math.min(...candidates) : content.length
  return content.slice(0, end).trim()
}

function typeStrings(parameters) {
  return (parameters?.parameters || []).map(parameter => (
    parameter.typeDescriptions?.typeString || parameter.typeName?.name || 'unknown'
  ))
}

function modifierNames(node) {
  return (node.modifiers || []).map(modifier => (
    modifier.modifierName?.name || modifier.modifierName?.namePath || 'unknown'
  ))
}

function declarationFromNode(node, content, path, contractName) {
  const range = sourceRange(content, node.src)
  if (!range) return null
  const nodeKind = node.nodeType === 'ModifierDefinition' ? 'modifier' : (node.kind || 'function')
  const name = node.name || nodeKind
  return {
    astId: node.id,
    nodeType: node.nodeType,
    kind: nodeKind,
    name,
    contractName: contractName || null,
    visibility: node.visibility || null,
    stateMutability: node.stateMutability || null,
    virtual: Boolean(node.virtual),
    parameterTypes: typeStrings(node.parameters),
    returnTypes: typeStrings(node.returnParameters),
    modifiers: modifierNames(node),
    src: node.src,
    filePath: path,
    startByte: range.startByte,
    lengthByte: range.lengthByte,
    startChar: range.startChar,
    endChar: range.endChar,
    lineStart: range.lineStart,
    lineEnd: range.lineEnd,
    signature: signatureOf(range.declaration),
    content: range.declaration,
    contentHash: crypto.createHash('sha256').update(range.declaration, 'utf8').digest('hex')
  }
}

function collectDeclarations(ast, content, path) {
  const declarations = []
  function visit(node, contractName = null) {
    if (!node || typeof node !== 'object') return
    const owner = node.nodeType === 'ContractDefinition' ? node.name : contractName
    if (node.nodeType === 'FunctionDefinition' || node.nodeType === 'ModifierDefinition') {
      const declaration = declarationFromNode(node, content, path, owner)
      if (declaration) declarations.push(declaration)
    }
    for (const child of node.nodes || []) visit(child, owner)
  }
  visit(ast)
  return declarations.sort((left, right) => left.startByte - right.startByte)
}

function parseSolidityAst({ sources }) {
  const validationError = validateSources(sources)
  if (validationError) {
    return {
      success: false,
      parser: 'solc-standard-json-ast',
      compilerVersion: compilerVersion(),
      declarations: [],
      errors: [{ severity: 'error', message: validationError, formattedMessage: validationError }],
      warnings: []
    }
  }

  const input = {
    language: 'Solidity',
    sources: Object.fromEntries(Object.entries(sources).map(([path, content]) => [path, { content }])),
    settings: {
      optimizer: { enabled: false, runs: 200 },
      outputSelection: { '*': { '': ['ast'] } }
    }
  }

  try {
    const output = JSON.parse(solc.compile(JSON.stringify(input)))
    const diagnostics = (output.errors || []).map(diagnostic)
    const errors = diagnostics.filter(item => item.severity === 'error')
    const warnings = diagnostics.filter(item => item.severity !== 'error')
    const declarations = []
    for (const [path, sourceOutput] of Object.entries(output.sources || {})) {
      if (sourceOutput.ast && typeof sources[path] === 'string') {
        declarations.push(...collectDeclarations(sourceOutput.ast, sources[path], path))
      }
    }
    return {
      success: errors.length === 0,
      parser: 'solc-standard-json-ast',
      compilerVersion: compilerVersion(),
      declarations,
      errors,
      warnings
    }
  } catch (error) {
    return {
      success: false,
      parser: 'solc-standard-json-ast',
      compilerVersion: compilerVersion(),
      declarations: [],
      errors: [{
        severity: 'error',
        message: error.message || 'Solidity AST parser failed',
        formattedMessage: error.stack || error.message || 'Solidity AST parser failed'
      }],
      warnings: []
    }
  }
}

module.exports = { parseSolidityAst, collectDeclarations, MAX_SOURCE_BYTES, MAX_SOURCE_FILES }
