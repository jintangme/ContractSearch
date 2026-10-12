const { test } = require('node:test')
const assert = require('node:assert/strict')
const { ParamType } = require('ethers')
const { integerExpression: calc } = require('../src/integer-expression')
const { typedValue } = require('../src/abi-values')
const { validateCandidate } = require('../src/candidate-validation')
const { planHash } = require('../src/candidate-plan')
const { preflightPlan } = require('../src/candidate-preflight')
const constants={'$int256max':2n**255n-1n,'$int256min':-(2n**255n),'$uint256max':2n**256n-1n}
test('exact large integer arithmetic avoids float rounding and preserves division conventions',()=>{
  assert.equal(calc('($int256max + $int256max) / 2',constants),constants.$int256max)
  assert.equal(calc('($int256min + 1) / 2',constants),-(2n**254n)+1n)
  assert.equal(calc('-3 / 2'),-1n);assert.equal(calc('-3 // 2'),-2n)
  assert.equal(calc('3 // -2'),-2n);assert.equal(calc('-3 // -2'),1n)
  assert.equal(calc('(2 ** 85 - 1) ** 3'),(2n**85n-1n)**3n)
  assert.equal(calc('1 << 255'),2n**255n)
  assert.equal(calc('0xFF << 4 | 0X03'),4083n)
  assert.throws(()=>calc('0xGG'))
  assert.equal(calc('2 ** 3 ** 2'),512n)
})
test('expression parser rejects code execution, unknown names and resource exhaustion',()=>{
  for(const s of ['process.exit(0)','1;2','1/0','1<<513','2**512**512','(1','1)','__proto__','$unknown','2**-1','1e9','('.repeat(21)+'1'+')'.repeat(21)])assert.throws(()=>calc(s,constants),s)
  assert.throws(()=>calc('1'.repeat(257)),/256/)
})
test('expression literals are only interpreted for integer ABI fields',()=>{
  const value={integer:'($int256min + 1) / 2'}
  assert.equal(typedValue(value,ParamType.from('int256'),[]),String(-(2n**254n)+1n))
  assert.throws(()=>typedValue({...value,script:'bad'},ParamType.from('int256'),[]))
  assert.equal(typedValue('1 + 2',ParamType.from('string'),[]),'1 + 2')
})
test('real EVM checks accept exact expressions as inputs and expectations',async()=>{
  const sourceCode='pragma solidity ^0.8.20; contract Exact { function identity(int256 x) public pure returns(int256) { return x; } }'
  const expression={integer:'($int256min + 1) / 2'}
  const plan={contractName:'Exact',constructorArgs:[],requirements:[{id:'edge',description:'exact signed boundary'}],scenarios:[{id:'edge',requirementId:'edge',steps:[{kind:'read',method:'identity',args:[expression],from:0,expect:'equal',expected:expression}]}]}
  const receipt=await validateCandidate({sourceCode,plan,planHash:planHash(plan)})
  assert.equal(receipt.allChecksPassed,true)
  assert.equal(receipt.scenarios[0].steps[0].observed,String(-(2n**254n)+1n))
})

test('integer expression strings stay exact while literals and aliases retain their integer value',()=>{
  const int=ParamType.from('int256'),small=ParamType.from('int8')
  assert.equal(typedValue('$int256max - 1',int,[]),String(2n**255n-2n))
  assert.equal(typedValue('9007199254740993 + 1',int,[]),'9007199254740994')
  assert.equal(typedValue('-3 / 2',int,[]),'-1')
  assert.equal(typedValue('-3 // 2',int,[]),'-2')
  assert.equal(typedValue('0xff & 0x7f',small,[]),'127')
  assert.equal(typedValue('$typeMax - 1',small,[]),'126')
  for(const [literal,canonical] of [['0007','7'],['0x10','16'],['-3','-3'],[' +7 ','7']])assert.equal(typedValue(literal,int,[]),canonical)
  assert.equal(typedValue('type(int256).max',int,[]),String(2n**255n-1n))
  assert.equal(typedValue('maxInt',int,[]),String(2n**255n-1n))
  assert.equal(typedValue('$account0',ParamType.from('address'),['0x'+'1'.repeat(40)]),'0x'+'1'.repeat(40))
})

test('integer strings and expression objects obey the target signed width and reject ambiguous numeric syntax',()=>{
  const small=ParamType.from('int8'),unsigned=ParamType.from('uint8')
  for(const expression of ['127 + 1','-128 - 1','$uint256max - 1']){
    assert.throws(()=>typedValue(expression,small,[]),/outside int8/)
    assert.throws(()=>typedValue({integer:expression},small,[]),/outside int8/)
  }
  assert.throws(()=>typedValue('0 - 1',unsigned,[]),/outside uint8/)
  assert.throws(()=>typedValue({integer:'256'},unsigned,[]),/outside uint8/)
  for(const expression of ['1e3','1.5','1_000','1 2','process.exit(0)','1;2','$unknown + 1','1 / 0','1 << 513','2 ** -1','type(uint256).max - 1'])
    assert.throws(()=>typedValue(expression,ParamType.from('uint256'),[]),expression)
  assert.throws(()=>typedValue('1+'+'0'.repeat(255),unsigned,[]),/256/)
})

test('arithmetic applies only to nested integer leaves, never literal text, addresses or booleans',()=>{
  const tuple=ParamType.from('tuple(uint8[],string,address)')
  const address='0x'+'1'.repeat(40)
  assert.deepEqual(typedValue([['1 + 2','$typeMax - 1'],'1 + 2','$account0'],tuple,[address]),[['3','254'],'1 + 2',address])
  assert.equal(typedValue('$int256max - 1',ParamType.from('string'),[]),'$int256max - 1')
  for(const type of ['address','bool','bytes32']){
    assert.throws(()=>typedValue('1 + 2',ParamType.from(type),[]),/integer ABI field/)
    assert.throws(()=>typedValue({integer:'1 + 2'},ParamType.from(type),[]),/integer ABI field/)
  }
  assert.throws(()=>typedValue({integer:'1 + 2'},ParamType.from('string'),[]),/integer ABI field/)
  assert.equal(typedValue(false,ParamType.from('bool'),[]),false)
  assert.equal(typedValue('0x12',ParamType.from('bytes'),[]),'0x12')
})

test('preflight and real EVM share string arithmetic for deployment, reads, write returns and events',async()=>{
  const sourceCode=`pragma solidity ^0.8.20; contract StringArithmetic {
    uint256 public immutable initial;int256 public last;event Set(int256 value);
    constructor(uint256 n){initial=n;}
    function identity(int256 x) external pure returns(int256){return x;}
    function set(int256 x) external returns(int256){last=x;emit Set(x);return x;}
  }`
  const expression='$int256min + 1'
  const plan={contractName:'StringArithmetic',constructorArgs:['$uint256max - 1'],requirements:[{id:'exact',description:'Exact integer strings across ABI value sites'}],scenarios:[{id:'exact',requirementId:'exact',steps:[
    {kind:'read',method:'initial',args:[],from:0,expect:'equal',expected:'$uint256max - 1'},
    {kind:'read',method:'identity',args:['$int256max - 1'],from:0,expect:'equal',expected:'$int256max - 1'},
    {kind:'write',method:'set',args:[expression],from:0,expect:'success',expectedReturn:expression,events:[{name:'Set',args:[expression]}]},
    {kind:'read',method:'last',args:[],from:0,expect:'equal',expected:expression}
  ]}]}
  assert.equal((await preflightPlan(plan,sourceCode,true)).checkedSteps,4)
  const receipt=await validateCandidate({sourceCode,plan,planHash:planHash(plan)})
  assert.equal(receipt.allChecksPassed,true,JSON.stringify(receipt))
  assert.equal(receipt.scenarios[0].steps[0].observed,String(2n**256n-2n))
  assert.equal(receipt.scenarios[0].steps[3].observed,String(-(2n**255n)+1n))
})
