const { test } = require('node:test')
const assert = require('node:assert/strict')
const { ParamType } = require('ethers')
const { preflightPlan } = require('../src/candidate-preflight')
const { typedValue } = require('../src/abi-values')
const { validatePlan } = require('../src/candidate-plan')
const source = `pragma solidity ^0.8.20;
contract Example {
  constructor(address owner) {}
  function echo(int256 x) external pure returns(int256) { return x; }
  function pair() external pure returns(uint256,uint256) { return (1,2); }
  function write(uint256 n) external returns(uint256) { return n; }
}`
const plan = () => ({ contractName:'Example', constructorArgs:['$account0'],
  requirements:[{id:'signed_bounds',description:'signed extrema'}],
  scenarios:[{id:'signed_bounds',requirementId:'signed_bounds',steps:[
    {kind:'read',method:'echo',args:['$typeMin'],from:0,expect:'equal',expected:'$int256min'}
  ]}] })

test('preflight accepts typed signed extrema and does not claim behavior correctness', async () => {
  const p = plan(); validatePlan(p)
  const receipt = await preflightPlan(p,source,true)
  assert.equal(receipt.checkedSteps,1)
  assert.equal(receipt.scope,'SHAPE_AND_TYPES_ONLY_NOT_BEHAVIOR_CORRECTNESS')
})
test('preflight rejects unsigned extrema for signed ABI and wrong operation kinds before freeze', async () => {
  let p = plan(); p.scenarios[0].steps[0].args=['$uint256max']
  await assert.rejects(preflightPlan(p,source,true),/ABI_PREFLIGHT_REJECTED.*outside int256/)
  p=plan(); Object.assign(p.scenarios[0].steps[0],{kind:'write',expect:'success'})
  delete p.scenarios[0].steps[0].expected
  await assert.rejects(preflightPlan(p,source,true),/use kind=read/)
  p=plan(); Object.assign(p.scenarios[0].steps[0],{method:'write',args:['1'],expected:'1'})
  await assert.rejects(preflightPlan(p,source,true),/use kind=write/)
})
test('preflight checks constructor types and multi-output return shapes', async () => {
  let p=plan(); p.constructorArgs=['not-an-address']
  await assert.rejects(preflightPlan(p,source,true),/ABI_PREFLIGHT_REJECTED.*constructor/)
  p=plan(); Object.assign(p.scenarios[0].steps[0],{method:'pair',args:[],expected:['1','2']})
  assert.equal((await preflightPlan(p,source,true)).checkedSteps,1)
  p.scenarios[0].steps[0].expected=['1']
  await assert.rejects(preflightPlan(p,source,true),/ABI_PREFLIGHT_REJECTED/)
})
test('new methods absent from base are reported as skipped but required in a complete candidate', async () => {
  const p=plan(); p.scenarios[0].steps[0].method='newFunction'
  const receipt=await preflightPlan(p,source,false)
  assert.equal(receipt.checkedSteps,0)
  assert.equal(receipt.skippedNewMethods.length,1)
  await assert.rejects(preflightPlan(p,source,true),/Function missing/)
  assert.equal((await preflightPlan(p,'not Solidity',true)).status,'ABI_UNAVAILABLE_COMPILE_ERROR')
})
test('typed aliases recurse through arrays and leave literal string ABI values unchanged', () => {
  assert.deepEqual(typedValue(['$typeMax','$typeMin'],ParamType.from('int8[]'),[]),['127','-128'])
  assert.deepEqual(typedValue(['$typeMax','$typeMin'],ParamType.from('tuple(uint8,int8)'),[]),['255','-128'])
  assert.equal(typedValue('$typeMax',ParamType.from('string'),[]),'$typeMax')
  assert.throws(()=>typedValue('$uint256max',ParamType.from('uint8'),[]),/outside uint8/)
})
test('schema accepts descriptive IDs up to the advertised bound', () => {
  const p=plan(), id='signed_boundary_'.repeat(4)
  p.requirements[0].id=id; p.scenarios[0].id=id; p.scenarios[0].requirementId=id
  assert.doesNotThrow(()=>validatePlan(p))
  p.scenarios[0].id='x'.repeat(97)
  assert.throws(()=>validatePlan(p),/96/)
})
