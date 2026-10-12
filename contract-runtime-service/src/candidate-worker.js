// Only declarative ABI calls are executed. No generated JS or external RPC.
const ganache = require('ganache')
const { BrowserProvider, ContractFactory } = require('ethers')
const { compileSolidity } = require('./compiler')
const { digest, planHash, constructorArgsForScenario } = require('./candidate-plan')

function normalize(value) {
  if (typeof value === 'bigint' || typeof value === 'number') return String(value)
  if (Array.isArray(value)) return Array.from(value, normalize)
  if (typeof value === 'string' && /^0x[0-9a-fA-F]{40}$/.test(value)) return value.toLowerCase()
  return value
}
const { resolve, typedValue } = require('./abi-values')
function isRevert(error) {
  // A malformed ABI, missing function or exhausted gas is not an expected revert.
  const detail = [error.shortMessage, error.message, error.info?.error?.message].filter(Boolean).join(' ')
  return error.code === 'CALL_EXCEPTION' && error.action === 'call' && typeof error.data === 'string'
    && /^0x(?:[a-fA-F0-9]{2})*$/.test(error.data) && !/out of gas|invalid opcode/i.test(detail)
}

// Oracle-free exploration: observations are NOT additional pass/fail tests.
// Only bounded read-only ABI calls on fresh local state; no generated scripts.
async function probeBoundaries(contract, plan, accounts, address) {
  const names = new Set(plan.scenarios.flatMap(s => s.steps.filter(t => t.kind === 'read').map(t => t.method)))
  const methods = [...names].map(n => contract.interface.getFunction(n)).filter(f => f && ['view', 'pure'].includes(f.stateMutability)
    && f.inputs.length && f.inputs.every(i => /^(uint([0-9]+)?|address|bool)$/.test(i.type)))
    .sort((a, b) => b.inputs.filter(i => i.type.startsWith('uint')).length - a.inputs.filter(i => i.type.startsWith('uint')).length)
  const probes = []
  for (const method of methods.slice(0, 3)) {
    const integers = method.inputs.map((i, n) => i.type.startsWith('uint') ? n : -1).filter(n => n >= 0)
    if (!integers.length) continue
    const max = i => String(2n ** BigInt(Number(i.type.slice(4) || 256)) - 1n)
    const base = method.inputs.map(i => i.type === 'address' ? accounts[0] : i.type === 'bool' ? false : '1')
    const vectors = [base.map((x, n) => integers.includes(n) ? '0' : x), base,
      base.map((x, n) => integers.includes(n) ? max(method.inputs[n]) : x),
      base.map((x, n) => n === integers[0] ? max(method.inputs[n]) : x)]
    const seen = new Set()
    for (const args of vectors) {
      const key = JSON.stringify(args)
      if (seen.has(key)) continue
      seen.add(key)
      const probe = { method: method.format('sighash'), args, scope: 'ORACLE_FREE_FRESH_STATE_READ_ONLY', interpretation: 'May be outside the task domain. Revert alone is NOT a defect.' }
      try {
        probe.observed = normalize(await contract.getFunction(method.format('sighash')).staticCall(...args, { gasLimit: 300000 }))
        probe.status = 'RETURNED'
      } catch (error) {
        probe.status = isRevert(error) ? 'REVERTED' : 'INCONCLUSIVE'
        probe.reason = String(error.shortMessage || error.message).slice(0, 300)
      }
      probes.push(probe)
    }
  }
  return probes
}

async function validateCandidate({ sourceCode, plan }) {
  const result = { sourceHash: digest(sourceCode), planHash: planHash(plan), validationScope: 'DECLARED_VISIBLE_CHECKS', unexecutedAnalysisObligations: plan.analysisObligations || [], allChecksPassed: false, requirementCoverage: 0, scenarios: [] }
  // Pin to the installed compiler. Never fetch a compiler for generated input.
  const bundledVersion = require('solc').version().match(/\d+\.\d+\.\d+/)[0]
  const compiled = await compileSolidity({ sourceCode, fileName: 'Candidate.sol', compilerVersion: bundledVersion })
  result.compile = { success: compiled.success, selectedCompilerVersion: compiled.selectedCompilerVersion, errors: compiled.errors, warnings: compiled.warnings }
  if (!compiled.success) return { ...result, status: 'COMPILE_FAILED' }
  const artifact = compiled.contracts.find(c => c.contractName === plan.contractName && c.bytecode !== '0x')
  if (!artifact) return { ...result, status: 'CONTRACT_NOT_FOUND' }
  for (const scenario of plan.scenarios) {
    const row = { id: scenario.id, requirementId: scenario.requirementId, passed: true, steps: [] }
    const raw = ganache.provider({ logging: { quiet: true }, wallet: { totalAccounts: 3, deterministic: true }, chain: { hardfork: 'shanghai' }, miner: { blockGasLimit: 8000000 } })
    try {
      const provider = new BrowserProvider(raw)
      provider.pollingInterval = 50
      const signers = await Promise.all([0, 1, 2].map(i => provider.getSigner(i)))
      const accounts = await Promise.all(signers.map(s => s.getAddress()))
      const factory = new ContractFactory(artifact.abi, artifact.bytecode, signers[0])
      const constructorArgs = constructorArgsForScenario(plan, scenario).map((v, i) => typedValue(v, factory.interface.deploy.inputs[i], accounts))
      const contract = await factory.deploy(...constructorArgs, { gasLimit: 3000000 })
      await contract.waitForDeployment()
      const address = await contract.getAddress()
      if (!('boundaryProbes' in result)) {
        const deployment = { scenarioId: scenario.id, constructorArgs: constructorArgsForScenario(plan, scenario), resolvedConstructorArgs: normalize(constructorArgs), scope: 'FRESH_DEPLOYMENT_BEFORE_SCENARIO_STEPS' }
        result.boundaryProbes = (await probeBoundaries(contract, plan, accounts, address)).map(probe => ({ ...probe, deployment }))
      }
      for (const [index, step] of scenario.steps.entries()) {
        let outcome = { index, method: step.method, from: step.from, args: step.args, expectation: step.expect, passed: false }
        try {
          const fragment = contract.interface.getFunction(step.method)
          if (!fragment) throw Error('Function not found in candidate ABI')
          const readOnly = ['view', 'pure'].includes(fragment.stateMutability)
          if (readOnly !== (step.kind === 'read')) throw Error('Step kind does not match ABI mutability')
          const fn = contract.connect(signers[step.from]).getFunction(step.method)
          const args = step.args.map((v, i) => typedValue(v, fragment.inputs[i], accounts, address))
          const options = { gasLimit: 300000, ...(step.valueWei ? { value: BigInt(step.valueWei) } : {}) }
          // Explicit static call determines an EVM revert; a failed transaction
          // receipt alone cannot distinguish a revert from exhausted gas.
          const actual = await fn.staticCall(...args, options)
          if (step.expect === 'revert') outcome.reason = 'EXPECTED_REVERT_NOT_OBSERVED'
          else if (step.kind === 'write') {
            const balances = step.balanceChanges || []
            const balanceOf = async account => BigInt(await raw.request({ method: 'eth_getBalance', params: [resolve(account, accounts, address), 'latest'] }))
            const before = await Promise.all(balances.map(change => balanceOf(change.account)))
            const receipt = await (await fn(...args, options)).wait()
            outcome.passed = true
            if ('expectedReturn' in step) {
              if (!fragment.outputs.length) throw Error('Function has no return values')
              const expected = normalize(fragment.outputs.length === 1
                ? typedValue(step.expectedReturn, fragment.outputs[0], accounts, address)
                : step.expectedReturn.map((v, i) => typedValue(v, fragment.outputs[i], accounts, address)))
              // Return data is from the pre-transaction eth_call simulation;
              // state/events below are from the actual transaction receipt.
              const observed = normalize(actual)
              outcome.returnCheck = { expected, observed, scope: 'PRE_TRANSACTION_SIMULATION' }
              outcome.passed = JSON.stringify(expected) === JSON.stringify(observed)
              if (!outcome.passed) outcome.reason = 'RETURN_VALUE_MISMATCH'
            }
            if ('events' in step) {
              // Assert the exact ordered event sequence from this contract only.
              // Unknown own-address logs fail; external logs cannot impersonate it.
              const observed = receipt.logs.filter(log => log.address.toLowerCase() === address.toLowerCase()).map(log => {
                const decoded = contract.interface.parseLog(log)
                if (!decoded) throw Error('Undecodable event from candidate')
                return { name: decoded.name, args: normalize(Array.from(decoded.args)) }
              })
              const expected = step.events.map(event => {
                const eventType = contract.interface.getEvent(event.name)
                return { name: event.name, args: normalize(event.args.map((v, i) => typedValue(v, eventType?.inputs[i], accounts, address))) }
              })
              outcome.events = { expected, observed }
              const eventsMatch = JSON.stringify(expected) === JSON.stringify(observed)
              outcome.passed = outcome.passed && eventsMatch
              if (!eventsMatch) outcome.reason = 'EVENT_SEQUENCE_MISMATCH'
            }
            if (balances.length) {
              outcome.balanceChanges = await Promise.all(balances.map(async (change, i) => {
                const observed = String((await balanceOf(change.account)) - before[i])
                return { account: change.account, expected: change.deltaWei, observed, passed: BigInt(observed) === BigInt(change.deltaWei) }
              }))
              if (outcome.balanceChanges.some(change => !change.passed)) { outcome.passed = false; outcome.reason = 'BALANCE_DELTA_MISMATCH' }
            }
          } else {
            const expected = normalize(fragment.outputs.length === 1
              ? typedValue(step.expected, fragment.outputs[0], accounts, address)
              : step.expected.map((v, i) => typedValue(v, fragment.outputs[i], accounts, address)))
            const observed = normalize(actual)
            outcome = { ...outcome, expected, observed, passed: JSON.stringify(expected) === JSON.stringify(observed) }
          }
        } catch (error) {
          outcome.passed = step.expect === 'revert' && isRevert(error)
          if (outcome.passed && 'revertReason' in step) {
            outcome.expectedReason = step.revertReason
            outcome.observedReason = error.reason || ''
            outcome.passed = outcome.observedReason === step.revertReason
          }
          outcome.reason = outcome.passed ? 'EXPECTED_REVERT' : String(error.shortMessage || error.message).slice(0, 500)
        }
        row.steps.push(outcome)
        if (!outcome.passed) { row.passed = false; break }
      }
    } catch (error) { row.passed = false; row.error = String(error.shortMessage || error.message).slice(0, 500) }
    finally { await raw.disconnect() }
    result.scenarios.push(row)
  }
  result.requirements = plan.requirements.map(r => {
    const rows = result.scenarios.filter(s => s.requirementId === r.id)
    const passed = rows.filter(s => s.passed).length
    return { id: r.id, passedScenarios: passed, totalScenarios: rows.length, coverage: passed / rows.length }
  })
  result.requirementCoverage = result.requirements.reduce((sum, r) => sum + r.coverage, 0) / result.requirements.length
  result.allChecksPassed = result.requirementCoverage === 1
  return { ...result, status: result.allChecksPassed ? 'CHECKS_PASSED' : 'CHECKS_FAILED' }
}

// A disposable OS process owns solc, Ganache and their native/Wasm state.
// A native V8 crash here cannot terminate the HTTP service or other checks.
if (require.main === module) process.once('message', async payload => {
  let result
  try { result = await validateCandidate(payload) }
  catch (error) { result = { status:'RUNTIME_ERROR', allChecksPassed:false, sourceHash:digest(payload.sourceCode), planHash:planHash(payload.plan), message:String(error.message).slice(0,500) } }
  if (process.connected) process.send(result, () => { if (process.connected) process.disconnect() })
})
module.exports = { validateCandidate }
