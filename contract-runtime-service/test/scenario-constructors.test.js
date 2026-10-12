const { test } = require('node:test')
const assert = require('node:assert/strict')
const { validatePlan, planHash } = require('../src/candidate-plan')
const { preflightPlan } = require('../src/candidate-preflight')
const { validateCandidate } = require('../src/candidate-validation')

const source = `pragma solidity ^0.8.20;
contract DeploymentLimit {
 uint256 public immutable limit; uint256 public value;
 constructor(uint256 n) { limit=n; }
 function set(uint256 n) external { require(n<=limit); value=n; }
}`
const read = (method, expected) => ({kind:'read',method,args:[],from:0,expect:'equal',expected})
const write = value => ({kind:'write',method:'set',args:[value],from:0,expect:'success'})
const makePlan = () => ({
 contractName:'DeploymentLimit',constructorArgs:['2'],
 requirements:[{id:'state',description:'Legal initial states remain isolated'}],
 scenarios:[
  {id:'ordinary',requirementId:'state',steps:[read('limit','2'),write('2'),read('value','2')]},
  {id:'maximum',requirementId:'state',constructorArgs:['$uint256max'],steps:[read('limit','$uint256max'),write('$uint256max'),read('value','$uint256max')]},
  {id:'fresh_default',requirementId:'state',steps:[read('limit','2'),read('value','0')]}
 ]
})
const execute = (plan, candidate=source) => validateCandidate({sourceCode:candidate,plan,planHash:planHash(plan)})

test('scenario constructor overrides are bounded input and part of the frozen plan hash', () => {
 const p=makePlan()
 assert.doesNotThrow(()=>validatePlan(p))
 const original=planHash(p)
 p.scenarios[1].constructorArgs=['7']
 assert.notEqual(planHash(p),original)
 for(const invalid of [null,'7',{},[Number.MAX_SAFE_INTEGER+1],Array(33).fill('0'),[[[[[['0']]]]]],[{integer:'1'.repeat(257)}]]) {
  const bad=makePlan();bad.scenarios[1].constructorArgs=invalid
  assert.throws(()=>validatePlan(bad))
 }
 const empty=makePlan();empty.scenarios[1].constructorArgs=[]
 assert.doesNotThrow(()=>validatePlan(empty),'ABI arity is a separate preflight check')
})

test('ABI preflight validates each effective constructor and identifies the offending scenario', async () => {
 const p=makePlan()
 const receipt=await preflightPlan(p,source,true)
 assert.equal(receipt.checkedSteps,8)
 const missing=makePlan();missing.scenarios[1].constructorArgs=[]
 await assert.rejects(preflightPlan(missing,source,true),/maximum constructor/)
 const narrowSource=source.replace('constructor(uint256 n)','constructor(uint8 n)')
 await assert.rejects(preflightPlan(p,narrowSource,true),/maximum constructor:.*outside uint8/)
 const signed=makePlan();signed.scenarios[1].constructorArgs=['-1']
 await assert.rejects(preflightPlan(signed,source,true),/maximum constructor/)
})

test('ordinary, full uint256, and inherited-default deployments execute in separate fresh states', async () => {
 const receipt=await execute(makePlan())
 assert.equal(receipt.status,'CHECKS_PASSED',JSON.stringify(receipt))
 assert.equal(receipt.scenarios[1].steps[2].observed,String(2n**256n-1n))
 assert.equal(receipt.scenarios[2].steps[1].observed,'0')
})

test('boundary probes identify the actual first successful deployment and resolved constructor values', async () => {
 const candidate=source.replace('constructor(uint256 n) { limit=n; }',
  'constructor(uint256 n) { require(n>0); limit=n; } function within(uint256 n) external view returns(bool) { return n<=limit; }')
 for(const initial of ['7','0']) {
  const p=makePlan()
  p.scenarios[0].constructorArgs=[initial]
  p.scenarios[0].steps[0].expected=initial
  p.scenarios[0].steps.push({kind:'read',method:'within',args:['7'],from:0,expect:'equal',expected:true})
  const receipt=await execute(p,candidate)
  const maximum=String(2n**256n-1n)
  const expectedId=initial==='0'?'maximum':'ordinary'
  const expectedArgs=initial==='0'?['$uint256max']:['7']
  const expectedResolvedArgs=initial==='0'?[maximum]:['7']
  assert.equal(receipt.scenarios[0].passed,initial!=='0')
  assert.equal(receipt.scenarios[1].passed,true)
  assert.ok(receipt.boundaryProbes.length>0)
  for(const probe of receipt.boundaryProbes) {
   assert.deepEqual(probe.deployment,{scenarioId:expectedId,constructorArgs:expectedArgs,resolvedConstructorArgs:expectedResolvedArgs,scope:'FRESH_DEPLOYMENT_BEFORE_SCENARIO_STEPS'})
  }
  const upper=receipt.boundaryProbes.find(probe=>probe.method==='within(uint256)'&&probe.args[0]===maximum)
  assert.equal(upper.status,'RETURNED')
  assert.equal(upper.observed,initial==='0')
 }
})

test('an explicit empty override selects a no-argument deployment instead of the unused default', async () => {
 const p={contractName:'NoArguments',constructorArgs:['unused default'],requirements:[{id:'value',description:'No-argument construction'}],scenarios:[{id:'empty_override',requirementId:'value',constructorArgs:[],steps:[read('value','7')]}]}
 const noArgs='pragma solidity ^0.8.20; contract NoArguments { function value() external pure returns(uint256) { return 7; } }'
 validatePlan(p)
 assert.equal((await preflightPlan(p,noArgs,true)).checkedSteps,1)
 assert.equal((await execute(p,noArgs)).allChecksPassed,true)
 const inherited=structuredClone(p);delete inherited.scenarios[0].constructorArgs
 await assert.rejects(preflightPlan(inherited,noArgs,true),/empty_override constructor/)
})

test('unchanged setup-prefix fixtures retain and execute their own deployment override', async () => {
 const full=makePlan()
 full.scenarios[1].setupSteps=2
 full.scenarios[1].steps=[write('$uint256max'),read('value','$uint256max'),read('limit','$uint256max')]
 // Mirrors PlanReuseTool's scenario.deepCopy() before keeping setupSteps.
 const fixture=structuredClone(full)
 fixture.scenarios=fixture.scenarios.filter(s=>s.setupSteps>0).map(s=>{
  s.steps=s.steps.slice(0,s.setupSteps);delete s.setupSteps;return s
 })
 validatePlan(fixture)
 assert.equal((await preflightPlan(fixture,source,true)).checkedSteps,2)
 assert.equal((await execute(fixture)).allChecksPassed,true)
 const lost=structuredClone(fixture);delete lost.scenarios[0].constructorArgs
 const failed=await execute(lost)
 assert.equal(failed.allChecksPassed,false,'falling back to limit=2 would reject the legal boundary fixture')
 assert.equal(failed.scenarios[0].steps[0].passed,false)
})
