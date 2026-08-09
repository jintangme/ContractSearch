const express = require('express')
const cors = require('cors')
const { compileSolidity } = require('./compiler')
const { parseSolidityAst } = require('./ast-parser')
const { callContract, deployContract, initRuntime } = require('./runtime')

const PORT = Number(process.env.PORT || 19090)
const HOST = process.env.HOST || '127.0.0.1'

const app = express()

app.use(cors({
  origin: true
}))
app.use(express.json({
  limit: '1mb'
}))

app.post('/api/runtime/compile', async (req, res) => {
  const result = await compileSolidity({
    sourceCode: req.body?.sourceCode,
    fileName: req.body?.fileName,
    compilerVersion: req.body?.compilerVersion
  })

  res.status(result.success ? 200 : 400).json(result)
})

app.post('/api/runtime/parse', (req, res) => {
  const result = parseSolidityAst({ sources: req.body?.sources })
  res.status(result.success ? 200 : 400).json(result)
})

app.post('/api/runtime/deploy', async (req, res) => {
  const result = await deployContract({
    contractName: req.body?.contractName,
    abi: req.body?.abi,
    bytecode: req.body?.bytecode,
    constructorArgs: req.body?.constructorArgs
  })

  res.status(result.success ? 200 : 400).json(result)
})

app.post('/api/runtime/call', async (req, res) => {
  const result = await callContract({
    sessionId: req.body?.sessionId,
    functionName: req.body?.functionName,
    args: req.body?.args,
    value: req.body?.value
  })

  res.status(result.success ? 200 : 400).json(result)
})

app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: '接口不存在',
    errorType: 'NOT_FOUND'
  })
})

app.use((err, req, res, next) => {
  if (res.headersSent) {
    next(err)
    return
  }

  const message = err.type === 'entity.too.large'
    ? '请求体超过 1MB 限制'
    : err.message || 'Runtime service error'

  res.status(400).json({
    success: false,
    message,
    errorType: err.type === 'entity.too.large' ? 'REQUEST_TOO_LARGE' : 'BAD_REQUEST'
  })
})

initRuntime()
  .then(() => {
    const server = app.listen(PORT, HOST, () => {
      console.log(`Contract runtime service listening on http://${HOST}:${PORT}`)
      console.log('Local EVM sandbox initialized')
    })

    server.on('error', (err) => {
      const message = err.code === 'EADDRINUSE'
        ? `端口 ${PORT} 已被占用，请关闭已有服务或设置 PORT 使用其他端口`
        : err.message || 'Runtime service listen error'

      console.error(message)
      process.exitCode = 1
    })
  })
  .catch((err) => {
    console.error(err.message || err)
    process.exitCode = 1
  })
