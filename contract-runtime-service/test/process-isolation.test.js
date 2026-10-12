const { test, mock } = require('node:test')
const assert = require('node:assert/strict')
const childProcess = require('node:child_process')
const originalFork = childProcess.fork
let spawned, failNextSpawn = false
mock.method(childProcess, 'fork', (...args) => {
  if (failNextSpawn) { args[2]={...args[2],execPath:'/nonexistent-node-for-isolation-test'};failNextSpawn=false }
  spawned=originalFork(...args);return spawned
})
const { validateCandidate } = require('../src/candidate-validation')
const { planHash } = require('../src/candidate-plan')
const sourceCode='pragma solidity ^0.8.20; contract Healthy { function value() external pure returns(uint256){return 7;} }'
const plan={contractName:'Healthy',constructorArgs:[],requirements:[{id:'value',description:'return seven'}],scenarios:[{id:'value',requirementId:'value',steps:[{kind:'read',method:'value',args:[],from:0,expect:'equal',expected:'7'}]}]}
test('a killed checker process cannot kill its caller and releases validation capacity',async()=>{
  const first=validateCandidate({sourceCode,plan,planHash:planHash(plan)})
  const ownedChild=spawned
  assert.ok(ownedChild.pid)
  await new Promise(resolve=>ownedChild.once('spawn',resolve))
  ownedChild.kill('SIGKILL') // only the child just created by this test
  const failed=await first
  assert.ok(['WORKER_EXIT','WORKER_ERROR'].includes(failed.status),failed.status)
  assert.equal(failed.allChecksPassed,false)
  assert.equal(failed.isolation,'DISPOSABLE_PROCESS')
  const second=await validateCandidate({sourceCode,plan,planHash:planHash(plan)})
  assert.equal(second.allChecksPassed,true)
  assert.equal(second.isolation,'DISPOSABLE_PROCESS')
})
test('a failed spawn returns a transport failure without hanging or leaking a slot',async()=>{
  failNextSpawn=true
  const failed=await validateCandidate({sourceCode,plan,planHash:planHash(plan)})
  assert.equal(failed.status,'WORKER_ERROR')
  assert.equal(failed.allChecksPassed,false)
  const healthy=await validateCandidate({sourceCode,plan,planHash:planHash(plan)})
  assert.equal(healthy.allChecksPassed,true)
})
test('repeated concurrent validation keeps process capacity usable',async()=>{
  for(let batch=0;batch<6;batch++) {
    const rows=await Promise.all([0,1].map(()=>validateCandidate({sourceCode,plan,planHash:planHash(plan)})))
    assert.ok(rows.every(row=>row.allChecksPassed && row.isolation==='DISPOSABLE_PROCESS'))
  }
})
