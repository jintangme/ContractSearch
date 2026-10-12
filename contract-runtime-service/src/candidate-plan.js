const crypto = require('node:crypto')

function canonical(value) {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']'
  if (value && typeof value === 'object') return '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical(value[k])).join(',') + '}'
  return JSON.stringify(value)
}
const digest = value => crypto.createHash('sha256').update(value).digest('hex')
const planHash = plan => digest(canonical(plan))
// Presence matters: [] is an explicit no-argument deployment, not a fallback.
const constructorArgsForScenario = (plan, scenario) => Object.hasOwn(scenario, 'constructorArgs') ? scenario.constructorArgs : plan.constructorArgs

function fields(value, allowed, required = []) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error('Expected an object')
  const unknown=Object.keys(value).filter(k=>!allowed.includes(k)), missing=required.filter(k=>!(k in value))
  if (unknown.length || missing.length) throw Error('Invalid plan fields: unknown=['+unknown.join(',')+']; missing=['+missing.join(',')+']')
}
function text(value, max, pattern) {
  if (typeof value !== 'string' || !value.trim() || value.length > max || (pattern && !pattern.test(value))) throw Error(`Invalid plan text: expected nonempty string of at most ${max} characters${pattern ? ' matching '+pattern : ''}; actual length=${typeof value === 'string' ? value.length : 'not a string'}`)
}
function scalarTree(value, depth = 0) {
  if (depth > 4) throw Error('Arguments exceed nesting limit')
  if (Array.isArray(value)) {
    if (value.length > 32) throw Error('Too many array arguments')
    value.forEach(v => scalarTree(v, depth + 1)); return
  }
  if (value && typeof value === 'object' && Object.keys(value).length === 1 && typeof value.integer === 'string' && value.integer.length > 0 && value.integer.length <= 256) return
  if (typeof value === 'boolean' || (typeof value === 'number' && Number.isSafeInteger(value))) return
  if (typeof value === 'string' && value.length <= 2000) return
  throw Error('ABI values must be bounded strings, safe integers, booleans, arrays or {integer: bounded expression}; use strings for large integers')
}
function validatePlan(plan) {
  fields(plan, ['contractName', 'constructorArgs', 'requirements', 'scenarios', 'analysisObligations'], ['contractName', 'constructorArgs', 'requirements', 'scenarios'])
  if (JSON.stringify(plan).length > 24000) throw Error('Plan exceeds size limit')
  text(plan.contractName, 100, /^[A-Za-z_][A-Za-z0-9_]*$/)
  if (!Array.isArray(plan.constructorArgs)) throw Error('constructorArgs must be an array')
  scalarTree(plan.constructorArgs)
  if ('analysisObligations' in plan) {
    if (!Array.isArray(plan.analysisObligations) || plan.analysisObligations.length > 8) throw Error('Specify at most 8 analysis obligations')
    plan.analysisObligations.forEach(value => text(value, 500))
  }
  if (!Array.isArray(plan.requirements) || !plan.requirements.length || plan.requirements.length > 12) throw Error('Specify 1 to 12 requirements')
  const ids = new Set()
  for (const r of plan.requirements) {
    fields(r, ['id', 'description'], ['id', 'description'])
    text(r.id, 96, /^[A-Za-z0-9_-]+$/); text(r.description, 500)
    if (ids.has(r.id)) throw Error('Duplicate requirement ID')
    ids.add(r.id)
  }
  if (!Array.isArray(plan.scenarios) || !plan.scenarios.length || plan.scenarios.length > 12) throw Error('Specify 1 to 12 scenarios')
  const scenarios = new Set(); const covered = new Set(); let steps = 0
  for (const scenario of plan.scenarios) {
    fields(scenario, ['id', 'requirementId', 'steps', 'setupSteps', 'constructorArgs'], ['id', 'requirementId', 'steps'])
    text(scenario.id, 96, /^[A-Za-z0-9_-]+$/)
    if (scenarios.has(scenario.id) || !ids.has(scenario.requirementId)) throw Error('Invalid scenario/requirement reference')
    scenarios.add(scenario.id); covered.add(scenario.requirementId)
    if ('constructorArgs' in scenario) {
      if (!Array.isArray(scenario.constructorArgs)) throw Error('Scenario constructorArgs must be an array')
      scalarTree(scenario.constructorArgs)
    }
    if (!Array.isArray(scenario.steps) || !scenario.steps.length || scenario.steps.length > 10) throw Error('Specify 1 to 10 steps per scenario')
    if ('setupSteps' in scenario && (!Number.isInteger(scenario.setupSteps) || scenario.setupSteps < 0 || scenario.setupSteps > scenario.steps.length)) throw Error('setupSteps must count an unchanged prefix of the scenario')
    for (const step of scenario.steps) {
      fields(step, ['kind', 'method', 'args', 'from', 'expect', 'expected', 'expectedReturn', 'valueWei', 'events', 'revertReason', 'balanceChanges'], ['kind', 'method', 'args', 'from', 'expect'])
      if (!['read', 'write'].includes(step.kind) || !['equal', 'revert', 'success'].includes(step.expect)) throw Error('Unsupported step kind or expectation')
      if (step.kind === 'write' && step.expect === 'equal') throw Error('Use a subsequent read to assert state after a write')
      if (step.kind === 'read' && step.expect === 'success') throw Error('Reads must assert an expected value or revert')
      text(step.method, 200, /^[A-Za-z_][A-Za-z0-9_]*(\([^()]*\))?$/)
      if (!Number.isInteger(step.from) || step.from < 0 || step.from > 2) throw Error('Sender must be account 0, 1 or 2')
      if (!Array.isArray(step.args)) throw Error('args must be an array')
      scalarTree(step.args)
      if (step.expect === 'equal') {
        if (!('expected' in step)) throw Error('Expected value is required')
        scalarTree(step.expected)
      } else if ('expected' in step) throw Error('Unexpected expected value')
      if ('expectedReturn' in step) {
        if (step.kind !== 'write' || step.expect !== 'success') throw Error('expectedReturn requires a successful write')
        scalarTree(step.expectedReturn)
      }
      if ('valueWei' in step && (step.kind !== 'write' || typeof step.valueWei !== 'string' || !/^\d{1,19}$/.test(step.valueWei) || BigInt(step.valueWei) > 10n ** 18n)) throw Error('Invalid bounded transaction value')
      if ('revertReason' in step) {
        if (step.expect !== 'revert') throw Error('revertReason requires expect=revert')
        text(step.revertReason, 200)
      }
      if ('events' in step) {
        if (step.kind !== 'write' || step.expect !== 'success' || !Array.isArray(step.events) || step.events.length > 8) throw Error('events requires a successful write and at most 8 events')
        for (const event of step.events) {
          fields(event, ['name', 'args'], ['name', 'args'])
          text(event.name, 100, /^[A-Za-z_][A-Za-z0-9_]*$/)
          if (!Array.isArray(event.args)) throw Error('Event args must be an array')
          scalarTree(event.args)
        }
      }
      if ('balanceChanges' in step) {
        if (step.kind !== 'write' || step.expect !== 'success' || !Array.isArray(step.balanceChanges) || !step.balanceChanges.length || step.balanceChanges.length > 3) throw Error('balanceChanges requires a successful write and 1 to 3 assertions')
        const accounts = new Set()
        for (const change of step.balanceChanges) {
          fields(change, ['account', 'deltaWei'], ['account', 'deltaWei'])
          if (!/^\$(account[0-2]|contract)$/.test(change.account) || change.account === '$account' + step.from || accounts.has(change.account)) throw Error('Balance assertions require distinct non-sender accounts or $contract (sender pays gas)')
          accounts.add(change.account)
          if (typeof change.deltaWei !== 'string' || !/^-?\d{1,19}$/.test(change.deltaWei) || BigInt(change.deltaWei) > 10n ** 18n || BigInt(change.deltaWei) < -(10n ** 18n)) throw Error('Invalid bounded balance delta')
        }
      }
      steps++
    }
  }
  if (steps > 64) throw Error(`Plan has ${steps} steps; maximum 64`)
  const missing = [...ids].filter(id => !covered.has(id))
  if (missing.length) throw Error(`Requirements without scenarios: ${missing.join(', ')}`)
  return { planHash: planHash(plan), requirements: plan.requirements.length, scenarios: plan.scenarios.length }
}
module.exports = { canonical, digest, planHash, validatePlan, constructorArgsForScenario }
