const { test } = require('node:test')
const assert = require('node:assert/strict')
const { validatePlan, planHash, canonical, digest } = require('../src/candidate-plan')
const { validateCandidate } = require('../src/candidate-validation')
const plan = require('./fixtures/candidate-counter.json')
const source = `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;
contract Counter {
  uint public count;
  address public owner;
  constructor(address initialOwner) { owner = initialOwner; }
  function increment() public { require(msg.sender == owner, "owner only"); count += 1; }
}`
const check = (sourceCode, p = plan) => validateCandidate({ sourceCode, plan: p, planHash: planHash(p) })

test('canonical plan hash ignores key order but retains requirements and Unicode', () => {
  const reordered = Object.fromEntries(Object.entries(plan).reverse())
  assert.equal(planHash(plan), planHash(reordered))
  assert.match(canonical(plan), /初始值/)
  const changed = structuredClone(plan); changed.scenarios[1].steps[2].expected = '1'
  assert.notEqual(planHash(plan), planHash(changed))
})

test('rejects unbounded, empty, uncovered, duplicated and executable plans', () => {
  assert.throws(() => validatePlan(undefined), /object/)
  for (const mutate of [
    p => { p.requirements = [] },
    p => { p.scenarios = [] },
    p => { p.scenarios.pop() },
    p => { p.scenarios[1].id = p.scenarios[0].id },
    p => { p.requirements[1].id = p.requirements[0].id },
    p => { p.scenarios[0].steps[0].script = 'fetch("https://example.com")' },
    p => { p.rpcUrl = 'https://example.com' },
    p => { p.scenarios[0].steps[0].expect = 'success' },
    p => { p.constructorArgs = [Number.MAX_SAFE_INTEGER + 1] },
    p => { p.scenarios[1].steps[0].valueWei = '1000000000000000001' }
  ]) {
    const p = structuredClone(plan); mutate(p)
    assert.throws(() => validatePlan(p))
  }
})

test('real compile and isolated EVM check constructor, repeated state changes and permissions', async () => {
  const r = await check(source)
  assert.equal(r.status, 'CHECKS_PASSED', JSON.stringify(r))
  assert.equal(r.requirementCoverage, 1)
  assert.equal(r.sourceHash, digest(source))
  assert.equal(r.planHash, planHash(plan))
  assert.equal(r.scenarios[2].steps[1].observed, '0', 'each scenario must use fresh state')
})

test('compilable regressions receive partial requirement scores, not successful validation', async () => {
  for (const [candidate, failing] of [
    [source.replace('count += 1', 'count = 1'), 'repeated'],
    [source.replace('require(msg.sender == owner, "owner only");', ''), 'denied']
  ]) {
    const r = await check(candidate)
    assert.equal(r.compile.success, true)
    assert.equal(r.allChecksPassed, false)
    assert.equal(r.requirementCoverage, 2 / 3)
    assert.equal(r.scenarios.find(s => s.id === failing).passed, false)
  }
})

test('compiler errors and absent deployable contract fail before behavior checks', async () => {
  const syntax = await check('pragma solidity ^0.8.20; contract Counter { broken }')
  assert.equal(syntax.status, 'COMPILE_FAILED')
  assert.equal(syntax.allChecksPassed, false)
  const absent = await check(source.replace('contract Counter', 'contract Other'))
  assert.equal(absent.status, 'CONTRACT_NOT_FOUND')
})

test('missing ABI, invalid opcode and out of gas cannot satisfy expected revert', async () => {
  const p = structuredClone(plan)
  p.requirements = [p.requirements[2]]; p.scenarios = [p.scenarios[2]]
  for (const candidate of [
    source.replace('increment()', 'different()'),
    source.replace('require(msg.sender == owner, "owner only"); count += 1;', 'assembly { invalid() }'),
    source.replace('require(msg.sender == owner, "owner only"); count += 1;', 'while (true) { count += 1; }')
  ]) {
    const r = await check(candidate, p)
    assert.equal(r.allChecksPassed, false, JSON.stringify(r))
    assert.equal(r.scenarios[0].steps[0].passed, false)
  }
})

test('payload must match frozen plan and rejects arbitrary fields', async () => {
  await assert.rejects(validateCandidate({ sourceCode: source, plan, planHash: 'wrong' }), /hash mismatch/)
  await assert.rejects(validateCandidate({ sourceCode: source, plan, planHash: planHash(plan), script: '' }), /Unknown/)
})

test('concurrent worker cap rejects excess work and releases slots after completion', async () => {
  const first = check('bad solidity'); const second = check('bad solidity')
  const busy = await check(source)
  assert.equal(busy.status, 'BUSY')
  await Promise.all([first, second])
  assert.equal((await check('bad solidity')).status, 'COMPILE_FAILED')
})

test('event receipts catch missing, extra, misordered and wrong-argument logs despite correct state', async () => {
  const code = source.replace('uint public count;', 'event Changed(address indexed actor, uint beforeValue, uint afterValue); uint public count;')
    .replace('count += 1;', 'count += 1; emit Changed(msg.sender, count - 1, count);')
  const p = structuredClone(plan)
  p.requirements = [p.requirements[1]]; p.scenarios = [p.scenarios[1]]
  for (const step of p.scenarios[0].steps) if (step.kind === 'write') {
    const previous = p.scenarios[0].steps.slice(0, p.scenarios[0].steps.indexOf(step)).filter(s => s.kind === 'write').length
    step.events = [{ name: 'Changed', args: ['$account0', String(previous), String(previous + 1)] }]
  }
  assert.equal((await check(code, p)).allChecksPassed, true)
  for (const bad of [
    code.replace('emit Changed(msg.sender, count - 1, count);', ''),
    code.replace('count - 1, count);', 'count, count - 1);'),
    code.replace('emit Changed(msg.sender, count - 1, count);', 'emit Changed(msg.sender, count - 1, count); emit Changed(msg.sender, count - 1, count);')
  ]) {
    const result = await check(bad, p)
    assert.equal(result.compile.success, true)
    assert.equal(result.allChecksPassed, false)
    assert.equal(result.scenarios[0].steps[0].reason, 'EVENT_SEQUENCE_MISMATCH')
  }
  const none = structuredClone(p); none.scenarios[0].steps[0].events = []
  assert.equal((await check(code, none)).allChecksPassed, false)
})

test('specific revert reason distinguishes expected guard from unrelated failure', async () => {
  const p = structuredClone(plan); p.requirements = [p.requirements[2]]; p.scenarios = [p.scenarios[2]]
  p.scenarios[0].steps[0].revertReason = 'owner only'
  assert.equal((await check(source, p)).allChecksPassed, true)
  const wrong = await check(source.replace('"owner only"', '"unrelated"'), p)
  assert.equal(wrong.allChecksPassed, false)
  assert.equal(wrong.scenarios[0].steps[0].observedReason, 'unrelated')
  for (const change of [s => { s.events = [] }, s => { s.revertReason = '' }]) {
    const bad = structuredClone(p); change(bad.scenarios[0].steps[0]); assert.throws(() => validatePlan(bad))
  }
})

test('balance assertions reject sender gas ambiguity, external addresses, duplicates and unbounded deltas', () => {
  for (const changes of [
    [{ account: '$account0', deltaWei: '1' }],
    [{ account: '0x0000000000000000000000000000000000000001', deltaWei: '1' }],
    [{ account: '$account1', deltaWei: '1' }, { account: '$account1', deltaWei: '1' }],
    [{ account: '$contract', deltaWei: '1000000000000000001' }],
    [{ account: '$contract', deltaWei: '-1000000000000000001' }]
  ]) {
    const p = structuredClone(plan); p.scenarios[1].steps[0].balanceChanges = changes
    assert.throws(() => validatePlan(p))
  }
  const valid = structuredClone(plan)
  valid.scenarios[1].steps[0].balanceChanges = [{ account: '$contract', deltaWei: '0' }]
  assert.doesNotThrow(() => validatePlan(valid))
})

test('uint256 aliases remain exact across constructor, call, read and event, without rewriting strings', async () => {
  const code = `pragma solidity ^0.8.20; contract AliasCheck {
    uint public value; string public label;
    event Set(uint value, string label);
    constructor(uint initial, string memory text) { value = initial; label = text; }
    function set(uint next, string memory text) external { value = next; label = text; emit Set(next, text); }
  }`
  const p = {
    contractName: 'AliasCheck', constructorArgs: ['$uint256max', 'maxUint'],
    requirements: [{ id: 'exact', description: 'Exact uint256 boundaries and literal strings' }],
    scenarios: [{ id: 'aliases', requirementId: 'exact', steps: [
      { kind: 'read', method: 'value', args: [], from: 0, expect: 'equal', expected: 'type(uint256).max' },
      { kind: 'read', method: 'label', args: [], from: 0, expect: 'equal', expected: 'maxUint' },
      { kind: 'write', method: 'set', args: ['maxUint', 'type(uint256).max'], from: 0, expect: 'success', events: [{ name: 'Set', args: ['$uint256max', 'type(uint256).max'] }] },
      { kind: 'read', method: 'value', args: [], from: 0, expect: 'equal', expected: String(2n ** 256n - 1n) },
      { kind: 'read', method: 'label', args: [], from: 0, expect: 'equal', expected: 'type(uint256).max' }
    ] }]
  }
  const result = await check(code, p)
  assert.equal(result.allChecksPassed, true, JSON.stringify(result))
})

test('deployer and other account placeholders never alias the literal zero address', async () => {
  const code = `pragma solidity ^0.8.20; contract AddressCheck {
    function isZero(address who) external pure returns (bool) { return who == address(0); }
  }`
  const p = { contractName: 'AddressCheck', constructorArgs: [],
    requirements: [{ id: 'addresses', description: 'Named signers and zero address remain distinct' }],
    scenarios: [{ id: 'identities', requirementId: 'addresses', steps:
      ['$account0', '$account1', '$account2', '0x0000000000000000000000000000000000000000'].map((address, index) =>
        ({ kind: 'read', method: 'isZero', args: [address], from: 0, expect: 'equal', expected: index === 3 }))
    }]
  }
  assert.equal((await check(code, p)).allChecksPassed, true)
})

test('unfrozen plan diagnostics identify uncovered requirements without weakening checks', () => {
  const p = structuredClone(plan)
  p.scenarios = p.scenarios.filter(s => s.requirementId !== 'permission')
  assert.throws(() => validatePlan(p), /Requirements without scenarios: permission/)
  assert.equal(p.requirements.length, 3)
})

test('analysis obligations remain visible and unexecuted, never counted as passing behavioral requirements', async () => {
  const p = structuredClone(plan)
  p.analysisObligations = ['Explain receiver callback limits from source; no callback test or proof was performed.']
  assert.notEqual(planHash(p), planHash(plan))
  const receipt = await check(source, p)
  assert.equal(receipt.allChecksPassed, true)
  assert.equal(receipt.requirementCoverage, 1)
  assert.equal(receipt.requirements.length, 3)
  assert.deepEqual(receipt.unexecutedAnalysisObligations, p.analysisObligations)
  const empty = structuredClone(p); empty.requirements = []; empty.scenarios = []
  assert.throws(() => validatePlan(empty), /requirements/)
  const malformed = structuredClone(p); malformed.analysisObligations = [{ script: 'execute' }]
  assert.throws(() => validatePlan(malformed), /Invalid plan text/)
})

test('write return assertions use pre-transaction simulation and cannot be overwritten by matching events', async () => {
  const code = `pragma solidity ^0.8.20; contract ReturnCounter {uint public value; event Changed(uint value); function add(uint n) external returns(uint){value+=n;emit Changed(value);return value;}}`
  const p = {contractName:'ReturnCounter',constructorArgs:[],requirements:[{id:'return',description:'return and state'}],scenarios:[{id:'return',requirementId:'return',steps:[
    {kind:'write',method:'add',args:['3'],from:0,expect:'success',expectedReturn:'3',events:[{name:'Changed',args:['3']}]},
    {kind:'read',method:'value',args:[],from:0,expect:'equal',expected:'3'}
  ]}]}
  assert.equal((await check(code,p)).allChecksPassed,true)
  const bad = await check(code.replace('return value;', 'return 0;'),p)
  assert.equal(bad.allChecksPassed,false)
  assert.equal(bad.scenarios[0].steps[0].reason,'RETURN_VALUE_MISMATCH')
  assert.equal(bad.scenarios[0].steps[0].returnCheck.scope,'PRE_TRANSACTION_SIMULATION')
  const invalid=structuredClone(p);invalid.scenarios[0].steps[1].expectedReturn='3'
  assert.throws(()=>validatePlan(invalid),/successful write/)
})

test('oracle-free boundary exploration reveals a legal-endpoint overflow without changing visible check scores', async () => {
  const code = 'pragma solidity ^0.8.20; contract Bounds { function size(uint256 start,uint256 n) external pure returns(uint256) {uint256 end=start+n;return end-start;} }'
  const p = {contractName:'Bounds',constructorArgs:[],requirements:[{id:'basic',description:'basic sample'}],scenarios:[{id:'sample',requirementId:'basic',steps:[{kind:'read',method:'size',args:['2','3'],from:0,expect:'equal',expected:'3'}]}]}
  const receipt = await check(code,p)
  assert.equal(receipt.allChecksPassed,true)
  assert.ok(receipt.boundaryProbes.some(p => p.args[0] === String(2n ** 256n - 1n) && p.args[1] === '1' && p.status === 'REVERTED'))
  assert.ok(receipt.boundaryProbes.every(p => p.scope === 'ORACLE_FREE_FRESH_STATE_READ_ONLY'))
  assert.equal(receipt.requirementCoverage,1)
})

test('expanded compact-plan capacity remains bounded at twelve scenarios and sixty-four steps', () => {
  const p = {contractName:'Counter',constructorArgs:['$account0'],requirements:[{id:'R',description:'Repeated checks'}],scenarios:Array.from({length:12},(_,i)=>({id:'S'+i,requirementId:'R',steps:[{kind:'read',method:'count',args:[],from:0,expect:'equal',expected:'0'}]}))}
  assert.doesNotThrow(()=>validatePlan(p))
  p.scenarios.push({...p.scenarios[0],id:'overflow'})
  assert.throws(()=>validatePlan(p),/12 scenarios/)
  p.scenarios=p.scenarios.slice(0,7).map(s=>({...s,steps:Array.from({length:10},()=>s.steps[0])}))
  assert.throws(()=>validatePlan(p),/maximum 64/)
})
