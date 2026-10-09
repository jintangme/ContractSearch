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
npm ci
```

## 启动

```bash
npm run dev
```

默认端口为 `19090`，也可以通过环境变量覆盖：

```bash
RUNTIME_PORT=19090 npm run dev
```

启动后会初始化 Local Sandbox，并使用 Ganache 默认测试账户作为 deployer。

`RUNTIME_HOST` 默认 `127.0.0.1`，`RUNTIME_PORT` 默认 `19090`；兼容旧的
`HOST` / `PORT` 作为后备值。Node 服务读取进程环境变量，不会自动加载 `.env`。
`GET /health` 返回服务状态、运行模式及 bundled compiler 版本。

Ganache 使用 Shanghai hardfork；Solidity 0.8.20 及以上编译器显式以 Shanghai
为目标，避免更新编译器默认产生沙盒不支持的指令。老版本 solc 保留其默认目标。
这是兼容本地运行的配置，不代表目标主网或其他链的部署配置。

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
  "compilerVersion": "0.8.35",
  "selectedCompilerVersion": "0.8.35",
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
  "compilerVersion": "0.8.35",
  "selectedCompilerVersion": "0.8.35",
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

## AST 解析接口（Knowledge Ingestion）

```text
POST http://localhost:19090/api/runtime/parse
```

请求使用 Solidity Standard JSON 风格的多文件 source map：

```json
{
  "sources": {
    "Vault.sol": "pragma solidity ^0.8.20; contract Vault { function deposit() public {} }"
  }
}
```

该接口固定使用 lockfile 中的 bundled `solc 0.8.35`，不会按请求下载远程编译器。响应不返回完整 AST，而只返回 ingestion 所需的 function/modifier/constructor/receive/fallback declaration metadata，包括 AST ID、contract、visibility、mutability、参数类型、modifier、file/line/char/byte span、signature、原文和 hash。总源码上限 1MB、文件数上限 100。

若源码 pragma 与固定编译器不兼容，接口返回结构化 diagnostics；Knowledge ingestion 会明确记录 AST fallback，不会把 lexical fallback 宣称为 AST 结果。

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


## 运行边界与验证

这个服务使用共享内存链和共享 signer，没有租户隔离、用户鉴权或操作系统级
编译沙箱。Session 过期只删除引用，不回滚链状态；重启服务会清空所有链数据。
把 Node 服务拆为独立进程不等于隔离不可信源码。不要将整个 runtime API 匿名
发布到公网；公开演示应禁用此入口，或另行设计鉴权、资源限制与执行隔离。

```bash
npm test
```

测试使用 bundled solc 和本地内存链，不下载远程编译器或连接真实区块链，覆盖
AST metadata、错误源码 diagnostics，以及字符串合约的编译、部署、写入、读取
和 session 过期。部分 Node/平台组合下 Ganache 原生 µWS 扩展不可用，会回退
到 JavaScript 实现；可用性与性能需按实际平台验证。

服务首次编译旧版本时可能下载远程 compiler。当前映射只包含 0.4.26、0.5.17、
0.6.12、0.7.6、0.8.20；它不是任意版本解析器，精确 pragma 未必有对应版本。
远程 compiler 的可达性和版本许可需按实际使用版本检查。
