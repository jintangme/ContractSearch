# Contract Runtime Service

独立的 Contract Lab runtime service，用于本地 Solidity 编译、本地 EVM 沙盒部署和合约函数调用。

服务只使用内存型 Local Sandbox，不连接真实主网或测试网，不需要 MetaMask，不会保存用户源码到磁盘或数据库。部署交易只发生在本地内存链中，不产生真实链上交易。

## 依赖

- `express`: HTTP API 服务
- `cors`: 允许本地前端跨域调用
- `solc`: Solidity standard JSON 编译
- `ethers`: 合约部署与交易结果解析
- `ganache`: 内存型本地 EVM 沙盒

## 安装

```bash
cd contract-runtime-service
npm install
```

## 启动

```bash
npm run dev
```

默认端口为 `19090`，也可以通过环境变量覆盖：

```bash
PORT=19090 npm run dev
```

启动后会初始化 Local Sandbox，并使用 Ganache 默认测试账户作为 deployer。

## 编译接口

```text
POST http://localhost:19090/api/runtime/compile
```

请求体：

```json
{
  "sourceCode": "pragma solidity ^0.8.20; contract A {}",
  "fileName": "Contract.sol"
}
```

`fileName` 可省略，默认使用 `Contract.sol`。

编译服务会在编译前识别源码中的 `pragma solidity`。对于常见的 `0.4.x`、`0.5.x`、`0.6.x`、`0.7.x` 和 `0.8.x` 合约，会优先选择兼容的编译器版本。老版本编译器通过 `solc.loadRemoteVersion` 按需加载，因此首次编译老合约时可能需要 runtime service 所在环境可以访问 Solidity 官方编译器列表。

### 编译 curl 示例

```bash
curl -X POST http://localhost:19090/api/runtime/compile \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "Contract.sol",
    "sourceCode": "// SPDX-License-Identifier: MIT\npragma solidity ^0.8.20;\n\ncontract SimpleStorage {\n    uint256 private value;\n\n    event ValueChanged(uint256 newValue);\n\n    function setValue(uint256 newValue) public {\n        value = newValue;\n        emit ValueChanged(newValue);\n    }\n\n    function getValue() public view returns (uint256) {\n        return value;\n    }\n}"
  }'
```

### 编译成功返回结构

```json
{
  "success": true,
  "contractName": "SimpleStorage",
  "detectedPragma": {
    "expression": "^0.8.20",
    "versions": ["0.8.20"],
    "preferredVersion": "0.8.20",
    "majorMinor": "0.8"
  },
  "compilerVersion": "0.8.30",
  "selectedCompilerVersion": "0.8.30",
  "versionMismatch": false,
  "contracts": [
    {
      "contractName": "SimpleStorage",
      "abi": [],
      "bytecode": "0x..."
    }
  ],
  "errors": [],
  "warnings": []
}
```

如果单个文件中包含多个 contract，`contracts` 会返回所有合约。`contractName` 默认选择第一个包含 bytecode 的合约。

### 编译错误返回结构

```json
{
  "success": false,
  "contractName": null,
  "contracts": [],
  "detectedPragma": {
    "expression": "^0.4.15",
    "versions": ["0.4.15"],
    "preferredVersion": "0.4.15",
    "majorMinor": "0.4"
  },
  "compilerVersion": "0.8.30",
  "selectedCompilerVersion": "0.8.30",
  "targetCompilerRelease": "v0.4.26+commit.4563c3fc",
  "versionMismatch": true,
  "errors": [
    {
      "severity": "error",
      "message": "...",
      "formattedMessage": "...",
      "line": 5,
      "column": 12
    }
  ],
  "warnings": []
}
```

只有 warning、没有 error 时，`success` 仍然为 `true`，warning 会放在 `warnings` 数组中。

如果返回 `versionMismatch: true`，通常表示源码声明的 Solidity 版本与当前可用编译器不兼容，或匹配的远程编译器加载失败。这种情况不一定代表源码本身有语法问题。

## 部署接口

```text
POST http://localhost:19090/api/runtime/deploy
```

请求体：

```json
{
  "contractName": "SimpleStorage",
  "abi": [],
  "bytecode": "0x...",
  "constructorArgs": []
}
```

`constructorArgs` 可省略，默认使用空数组。`bytecode` 如果缺少 `0x` 前缀，服务会自动补齐；如果不是十六进制字符串，会返回结构化错误。

### 部署成功返回结构

```json
{
  "success": true,
  "sessionId": "runtime_xxx",
  "contractName": "SimpleStorage",
  "contractAddress": "0x...",
  "deployer": "0x...",
  "transactionHash": "0x...",
  "gasUsed": "123456",
  "blockNumber": 1,
  "network": "Local Sandbox"
}
```

### 部署失败返回结构

```json
{
  "success": false,
  "message": "部署失败：...",
  "errorType": "DEPLOY_FAILED"
}
```

常见 `errorType`：

- `INVALID_ABI`
- `INVALID_BYTECODE`
- `INVALID_CONSTRUCTOR_ARGS`
- `PROVIDER_INIT_FAILED`
- `DEPLOY_FAILED`

## compile 后部署

推荐流程：

1. 调用 `/api/runtime/compile` 编译 Solidity 源码。
2. 从返回的 `contracts` 中选择目标合约。
3. 将目标合约的 `contractName`、`abi`、`bytecode` 传给 `/api/runtime/deploy`。

示例部署请求：

```bash
curl -X POST http://localhost:19090/api/runtime/deploy \
  -H "Content-Type: application/json" \
  -d '{
    "contractName": "SimpleStorage",
    "abi": [
      {
        "anonymous": false,
        "inputs": [
          {
            "indexed": false,
            "internalType": "uint256",
            "name": "newValue",
            "type": "uint256"
          }
        ],
        "name": "ValueChanged",
        "type": "event"
      },
      {
        "inputs": [],
        "name": "getValue",
        "outputs": [
          {
            "internalType": "uint256",
            "name": "",
            "type": "uint256"
          }
        ],
        "stateMutability": "view",
        "type": "function"
      },
      {
        "inputs": [
          {
            "internalType": "uint256",
            "name": "newValue",
            "type": "uint256"
          }
        ],
        "name": "setValue",
        "outputs": [],
        "stateMutability": "nonpayable",
        "type": "function"
      }
    ],
    "bytecode": "0x...",
    "constructorArgs": []
  }'
```

上面的 `bytecode` 需要替换为编译接口实际返回的 bytecode。

## Session

部署成功后，服务会在内存 `Map` 中创建 runtime session，后续函数调用接口会通过 `sessionId` 找到合约实例。

session 保存字段包括：

- `sessionId`
- `contractName`
- `abi`
- `bytecode`
- `contractAddress`
- `deployer`
- `deployedAt`
- `lastUsedAt`

session 过期时间为 30 分钟。服务会定时清理过期 session。session 不会写入磁盘或数据库。

## 函数调用接口

```text
POST http://localhost:19090/api/runtime/call
```

请求体：

```json
{
  "sessionId": "runtime_xxx",
  "functionName": "setValue",
  "args": [123],
  "value": "0"
}
```

`sessionId` 来自部署接口返回值。所有函数调用都发生在 Local Sandbox 中，不是真实链上交易。

### view / pure 函数返回结构

```json
{
  "success": true,
  "type": "call",
  "functionName": "getValue",
  "returnValue": "123",
  "decodedReturnValue": ["123"]
}
```

### nonpayable / payable 函数返回结构

```json
{
  "success": true,
  "type": "transaction",
  "functionName": "setValue",
  "transactionHash": "0x...",
  "gasUsed": "45678",
  "blockNumber": 3,
  "events": [
    {
      "eventName": "ValueChanged",
      "args": {
        "newValue": "123"
      }
    }
  ]
}
```

### 调用失败返回结构

```json
{
  "success": false,
  "message": "调用失败：...",
  "errorType": "CALL_FAILED"
}
```

常见 `errorType`：

- `SESSION_NOT_FOUND`
- `FUNCTION_NOT_FOUND`
- `FUNCTION_OVERLOADED`
- `INVALID_ARGS`
- `INVALID_VALUE`
- `CALL_FAILED`

### SimpleStorage 测试流程

1. 调用 `/api/runtime/compile` 编译 SimpleStorage。
2. 使用编译结果里的 `abi` 和 `bytecode` 调用 `/api/runtime/deploy`。
3. 使用部署返回的 `sessionId` 调用写入函数：

```bash
curl -X POST http://localhost:19090/api/runtime/call \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "runtime_xxx",
    "functionName": "setValue",
    "args": ["123"],
    "value": "0"
  }'
```

4. 调用读取函数：

```bash
curl -X POST http://localhost:19090/api/runtime/call \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "runtime_xxx",
    "functionName": "getValue",
    "args": [],
    "value": "0"
  }'
```

`setValue(123)` 会返回交易结果和 `ValueChanged` 事件；随后 `getValue()` 应返回 `123`。

## 当前限制

- 编译只支持单文件 Solidity 源码。
- 暂不处理外部 `import`。
- 请求体和源码大小限制为 1MB。
- 目前提供编译、部署、函数调用接口。
- 暂不提供多文件 import 支持。
- 部署和调用只发生在 Local Sandbox，不是真实链上交易。
