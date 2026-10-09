// A conservative completeness check, not a Lean parser or execution sandbox.
function proofPolicyDiagnostics(source) {
  let visible = ''
  let blockDepth = 0
  let inString = false
  let inLineComment = false

  for (let i = 0; i < source.length; i += 1) {
    const char = source[i]
    const pair = source.slice(i, i + 2)
    if (inLineComment) {
      if (char === '\n') inLineComment = false
      visible += char === '\n' ? '\n' : ' '
    } else if (blockDepth > 0) {
      if (pair === '/-') { blockDepth += 1; visible += '  '; i += 1 }
      else if (pair === '-/') { blockDepth -= 1; visible += '  '; i += 1 }
      else visible += char === '\n' ? '\n' : ' '
    } else if (inString) {
      if (char === '\\' && i + 1 < source.length) { visible += '  '; i += 1 }
      else { if (char === '"') inString = false; visible += char === '\n' ? '\n' : ' ' }
    } else if (pair === '--') {
      inLineComment = true; visible += '  '; i += 1
    } else if (pair === '/-') {
      blockDepth = 1; visible += '  '; i += 1
    } else if (char === '"') {
      inString = true; visible += ' '
    } else {
      visible += char
    }
  }

  return [...visible.matchAll(/\b(sorry|admit|axiom|axioms)\b/g)].map(match => {
    const before = source.slice(0, match.index)
    const isAssumption = match[1].startsWith('axiom')
    return {
      line: before.split('\n').length,
      column: before.length - before.lastIndexOf('\n'),
      severity: 'error',
      message: isAssumption
        ? 'Explicit axiom declarations are outside this checker’s supported proof scope.'
        : `Incomplete proof placeholder: ${match[1]}.`,
      errorType: isAssumption ? 'UNSUPPORTED_PROOF_ASSUMPTION' : 'INCOMPLETE_PROOF'
    }
  })
}

function hasIncompleteProofWarning(diagnostics) {
  return diagnostics.some(item => /\b(?:sorry|sorryAx|admit)\b/i.test(item.message))
}

module.exports = { proofPolicyDiagnostics, hasIncompleteProofWarning }
