const test = require('node:test')
const assert = require('node:assert/strict')
const { parseSolidityAst } = require('../src/ast-parser')

test('extracts deterministic AST declarations with exact source mappings', () => {
  const content = `pragma solidity ^0.8.20;
contract Vault {
  modifier onlyOwner() { _; }
  function deposit(uint256 amount) public onlyOwner returns (uint256) {
    return amount;
  }
}`
  const result = parseSolidityAst({ sources: { 'Vault.sol': content } })

  assert.equal(result.success, true)
  assert.match(result.compilerVersion, /^0\.8\./)
  assert.deepEqual(result.declarations.map(item => item.kind), ['modifier', 'function'])
  assert.equal(result.declarations[1].name, 'deposit')
  assert.equal(result.declarations[1].contractName, 'Vault')
  assert.equal(result.declarations[1].lineStart, 4)
  assert.equal(result.declarations[1].lineEnd, 6)
  assert.equal(result.declarations[1].visibility, 'public')
  assert.deepEqual(result.declarations[1].parameterTypes, ['uint256'])
  assert.equal(content.slice(result.declarations[1].startChar, result.declarations[1].endChar),
    result.declarations[1].content)
})
