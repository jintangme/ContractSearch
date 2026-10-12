const { test } = require('node:test')
const assert = require('node:assert/strict')
const { ParamType } = require('ethers')
const { typedValue } = require('../src/abi-values')
const { preflightPlan } = require('../src/candidate-preflight')
const { validateCandidate } = require('../src/candidate-validation')
const { planHash } = require('../src/candidate-plan')

test('integer literals normalize equivalent decimal, hex, sign and zero spellings with exact bounds',()=>{
  const int=ParamType.from('int8'),uint=ParamType.from('uint8')
  for(const [literal,expected] of [['0016','16'],['0x10','16'],['+00016','16'],[' +0X10 ','16'],['-0000','0'],['-0x00','0'],['0008','8']]) {
    assert.equal(typedValue(literal,int,[]),expected)
    assert.equal(typedValue(literal,uint,[]),expected)
  }
  assert.equal(typedValue('-0x80',int,[]),'-128')
  assert.equal(typedValue('+0x7f',int,[]),'127')
  assert.equal(typedValue('+000255',uint,[]),'255')
  assert.equal(typedValue('0x'+'f'.repeat(64),ParamType.from('uint256'),[]),String(2n**256n-1n))
  assert.equal(typedValue('0009007199254740993',ParamType.from('uint256'),[]),'9007199254740993')
  for(const value of ['128','0x80','-129','-0x81'])assert.throws(()=>typedValue(value,int,[]),/outside int8/)
  for(const value of ['256','0x100','-1','-0x1'])assert.throws(()=>typedValue(value,uint,[]),/outside uint8/)
  assert.throws(()=>typedValue('0x1'+'0'.repeat(64),ParamType.from('uint256'),[]),/outside uint256/)
})

test('nested normalization preserves string, bytes and address leaves',()=>{
  const address='0x'+'aB'.repeat(20)
  const type=ParamType.from('tuple(uint8[2][],int8,string,bytes2,address)')
  const input=[[['0x10','+0017'],['0001','0x02']],'-0007','00016','0x0010',address]
  const original=structuredClone(input)
  assert.deepEqual(typedValue(input,type,[]),[[['16','17'],['1','2']],'-7','00016','0x0010',address])
  assert.deepEqual(input,original,'normalization must not mutate the source plan')
  for(const value of ['0x10','+00016',' -0x80 '])assert.equal(typedValue(value,ParamType.from('string'),[]),value)
  assert.equal(typedValue('0x0010',ParamType.from('bytes2'),[]),'0x0010')
  assert.equal(typedValue(address,ParamType.from('address'),[]),address)
})

const sourceCode=`pragma solidity ^0.8.20;
contract CanonicalValues {
 struct Packet { uint256[] values; int256 delta; bytes2 tag; string label; address owner; }
 uint256 public immutable initial;
 event Recorded(uint256 indexed code,Packet packet);
 constructor(uint256 n){initial=n;}
 function inspect(uint256[] calldata values,int256 delta,bytes2 tag,string calldata label,address owner) external pure returns(Packet memory){return Packet(values,delta,tag,label,owner);}
 function record(uint256[] calldata values,int256 delta,bytes2 tag,string calldata label,address owner) external returns(Packet memory packet,uint256 code){packet=Packet(values,delta,tag,label,owner);code=16;emit Recorded(code,packet);}
}`
const argumentsForPacket=()=>[['0x10','0009007199254740993'],'-0007','0x0010','00016','$account1']
const expectedPacket=()=>[['+0016','0x20000000000001'],'-0x07','0x0010','00016','$account1']
const makePlan=()=>({contractName:'CanonicalValues',constructorArgs:['+00016'],requirements:[{id:'equivalence',description:'Equivalent integer spellings across all ABI assertions'}],scenarios:[{id:'equivalence',requirementId:'equivalence',steps:[
 {kind:'read',method:'initial',args:[],from:0,expect:'equal',expected:'0x10'},
 {kind:'read',method:'inspect',args:argumentsForPacket(),from:0,expect:'equal',expected:expectedPacket()},
 {kind:'write',method:'record',args:argumentsForPacket(),from:0,expect:'success',expectedReturn:[expectedPacket(),'0x10'],events:[{name:'Recorded',args:['+0016',expectedPacket()]}]}
]}]})

test('real EVM read, write return and nested event assertions accept equivalent integer literals',async()=>{
  const plan=makePlan(),before=JSON.stringify(plan),hash=planHash(plan)
  assert.equal((await preflightPlan(plan,sourceCode,true)).checkedSteps,3)
  const result=await validateCandidate({sourceCode,plan,planHash:hash})
  assert.equal(result.allChecksPassed,true,JSON.stringify(result))
  assert.equal(result.scenarios[0].steps[0].expected,'16')
  assert.equal(result.scenarios[0].steps[2].returnCheck.expected[0][0][1],'9007199254740993')
  assert.equal(result.scenarios[0].steps[2].events.expected[0].args[1][3],'00016')
  assert.equal(JSON.stringify(plan),before)
  assert.equal(planHash(plan),hash,'canonical comparison does not rewrite plan hashes or historical assertions')
})

test('canonicalization never makes a different numeric value or literal text compare equal',async()=>{
  const wrongNumber=makePlan()
  wrongNumber.scenarios[0].steps[0].expected='0x11'
  const numeric=await validateCandidate({sourceCode,plan:wrongNumber,planHash:planHash(wrongNumber)})
  assert.equal(numeric.allChecksPassed,false)
  assert.equal(numeric.scenarios[0].steps[0].expected,'17')
  assert.equal(numeric.scenarios[0].steps[0].observed,'16')
  const wrongText=makePlan()
  wrongText.scenarios[0].steps[2].events[0].args[1][3]='16'
  const textual=await validateCandidate({sourceCode,plan:wrongText,planHash:planHash(wrongText)})
  assert.equal(textual.allChecksPassed,false)
  assert.equal(textual.scenarios[0].steps[2].reason,'EVENT_SEQUENCE_MISMATCH')
})
