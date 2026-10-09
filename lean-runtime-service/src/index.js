const express = require('express')
const cors = require('cors')
const { checkLeanSource } = require('./leanChecker')

const PORT = Number(process.env.LEAN_PORT || process.env.PORT || 19100)
const HOST = process.env.LEAN_HOST || process.env.HOST || '127.0.0.1'
const allowedOrigins = new Set((process.env.LEAN_ALLOWED_ORIGINS
  || 'http://localhost:5173,http://127.0.0.1:5173').split(',').map(value => value.trim()).filter(Boolean))

const app = express()

// Browser-origin checks reduce accidental access to a local checker. They are
// not authentication and do not isolate the Lean process from the host.
app.use((req, res, next) => {
  if (req.get('Origin') && !allowedOrigins.has(req.get('Origin'))) {
    return res.status(403).json({ success: false, status: 'failed', errorType: 'ORIGIN_NOT_ALLOWED',
      message: 'This browser origin is not allowed to access the local Lean checker.', errors: [], warnings: [] })
  }
  next()
})
app.use(cors({ origin: (origin, callback) => callback(null, !origin || allowedOrigins.has(origin)) }))
app.use(express.json({
  limit: '1mb'
}))

app.post('/api/lean/check', async (req, res) => {
  const result = await checkLeanSource({
    sourceCode: req.body?.sourceCode,
    fileName: req.body?.fileName
  })

  res.status(result.success ? 200 : 400).json(result)
})

app.use((req, res) => {
  res.status(404).json({
    success: false,
    status: 'failed',
    message: '接口不存在',
    errorType: 'NOT_FOUND',
    errors: [],
    warnings: []
  })
})

app.use((err, req, res, next) => {
  if (res.headersSent) {
    next(err)
    return
  }

  const message = err.type === 'entity.too.large'
    ? '请求体超过 1MB 限制'
    : err.message || 'Lean runtime service error'

  res.status(400).json({
    success: false,
    status: 'failed',
    message,
    errorType: err.type === 'entity.too.large' ? 'REQUEST_TOO_LARGE' : 'BAD_REQUEST',
    errors: [],
    warnings: []
  })
})

const server = app.listen(PORT, HOST, () => {
  console.log(`Lean runtime service listening on http://${HOST}:${PORT}`)
  console.log(`Using Lean binary: ${process.env.LEAN_BIN || 'lean'}`)
})

server.on('error', (err) => {
  const message = err.code === 'EADDRINUSE'
    ? `端口 ${PORT} 已被占用，请关闭已有服务或设置 LEAN_PORT 使用其他端口`
    : err.message || 'Lean runtime service listen error'

  console.error(message)
  process.exitCode = 1
})
