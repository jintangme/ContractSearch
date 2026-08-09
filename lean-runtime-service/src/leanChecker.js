const { execFile } = require('child_process')
const crypto = require('crypto')
const fs = require('fs/promises')
const os = require('os')
const path = require('path')

const MAX_SOURCE_LENGTH = 1024 * 1024
const DEFAULT_FILE_NAME = 'Proof.lean'

function safeLeanFileName(fileName) {
  const baseName = path.basename(String(fileName || DEFAULT_FILE_NAME)).replace(/[^\w.-]/g, '_')
  if (!baseName || baseName === '.' || baseName === '..') return DEFAULT_FILE_NAME
  return baseName.endsWith('.lean') ? baseName : `${baseName}.lean`
}

function parseLeanDiagnostics(output, fallbackSeverity = 'error') {
  const lines = String(output || '').split(/\r?\n/)
  const diagnostics = []
  let current = null

  const flush = () => {
    if (!current) return
    current.message = current.message.trim()
    diagnostics.push(current)
    current = null
  }

  for (const line of lines) {
    if (!line.trim()) continue
    const match = line.match(/^[^:\n]+:(\d+):(\d+):\s*(error|warning):\s*(.*)$/i)
    if (match) {
      flush()
      current = {
        line: Number(match[1]),
        column: Number(match[2]),
        severity: match[3].toLowerCase(),
        message: match[4] || ''
      }
      continue
    }

    if (current) {
      current.message += `${current.message ? '\n' : ''}${line}`
    } else {
      diagnostics.push({
        line: null,
        column: null,
        severity: fallbackSeverity,
        message: line
      })
    }
  }

  flush()
  return diagnostics
}

function splitDiagnostics(output) {
  const diagnostics = parseLeanDiagnostics(output)
  return {
    errors: diagnostics.filter(item => item.severity !== 'warning'),
    warnings: diagnostics.filter(item => item.severity === 'warning')
  }
}

function execLean(leanBin, filePath, timeoutMs, cwd) {
  return new Promise((resolve) => {
    execFile(leanBin, [filePath], {
      cwd,
      timeout: timeoutMs,
      maxBuffer: 1024 * 1024,
      windowsHide: true
    }, (error, stdout, stderr) => {
      resolve({ error, stdout, stderr })
    })
  })
}

async function checkLeanSource({ sourceCode, fileName } = {}) {
  const startedAt = Date.now()
  const code = typeof sourceCode === 'string' ? sourceCode : ''

  if (!code.trim()) {
    return {
      success: false,
      status: 'failed',
      message: 'Lean 源码不能为空。',
      errorType: 'EMPTY_SOURCE',
      errors: [{
        line: null,
        column: null,
        severity: 'error',
        message: 'Lean 源码不能为空。'
      }],
      warnings: [],
      elapsedMs: Date.now() - startedAt
    }
  }

  if (Buffer.byteLength(code, 'utf8') > MAX_SOURCE_LENGTH) {
    return {
      success: false,
      status: 'failed',
      message: 'Lean 源码超过 1MB 限制。',
      errorType: 'SOURCE_TOO_LARGE',
      errors: [{
        line: null,
        column: null,
        severity: 'error',
        message: 'Lean 源码超过 1MB 限制。'
      }],
      warnings: [],
      elapsedMs: Date.now() - startedAt
    }
  }

  const leanBin = process.env.LEAN_BIN || 'lean'
  const timeoutMs = Number(process.env.LEAN_TIMEOUT_MS || 10000)
  const tempDir = path.join(os.tmpdir(), `lean-check-${crypto.randomBytes(8).toString('hex')}`)
  const safeFileName = safeLeanFileName(fileName)
  const filePath = path.join(tempDir, safeFileName)

  try {
    await fs.mkdir(tempDir, { recursive: true })
    await fs.writeFile(filePath, code, 'utf8')

    const result = await execLean(leanBin, filePath, Number.isFinite(timeoutMs) ? timeoutMs : 10000, tempDir)
    const elapsedMs = Date.now() - startedAt
    const combinedOutput = [result.stdout, result.stderr].filter(Boolean).join('\n')
    const { errors, warnings } = splitDiagnostics(combinedOutput)

    if (!result.error) {
      return {
        success: true,
        status: 'passed',
        message: 'Lean proof checked successfully.',
        errors: [],
        warnings,
        elapsedMs
      }
    }

    if (result.error.code === 'ENOENT') {
      return {
        success: false,
        status: 'failed',
        errorType: 'LEAN_NOT_FOUND',
        message: '未检测到 Lean 命令，请先安装 Lean4 或配置 LEAN_BIN。',
        errors: [],
        warnings,
        elapsedMs
      }
    }

    if (result.error.killed || result.error.signal === 'SIGTERM') {
      return {
        success: false,
        status: 'failed',
        errorType: 'LEAN_TIMEOUT',
        message: `Lean proof check timed out after ${Number.isFinite(timeoutMs) ? timeoutMs : 10000}ms.`,
        errors: errors.length ? errors : [{
          line: null,
          column: null,
          severity: 'error',
          message: 'Lean proof check timed out.'
        }],
        warnings,
        elapsedMs
      }
    }

    return {
      success: false,
      status: 'failed',
      message: 'Lean proof check failed.',
      errors: errors.length ? errors : [{
        line: null,
        column: null,
        severity: 'error',
        message: combinedOutput.trim() || result.error.message || 'Lean proof check failed.'
      }],
      warnings,
      elapsedMs
    }
  } catch (err) {
    return {
      success: false,
      status: 'failed',
      errorType: 'LEAN_CHECK_ERROR',
      message: err.message || 'Lean proof check failed.',
      errors: [{
        line: null,
        column: null,
        severity: 'error',
        message: err.message || 'Lean proof check failed.'
      }],
      warnings: [],
      elapsedMs: Date.now() - startedAt
    }
  } finally {
    await fs.rm(tempDir, { recursive: true, force: true }).catch(() => {})
  }
}

module.exports = {
  checkLeanSource,
  parseLeanDiagnostics
}
