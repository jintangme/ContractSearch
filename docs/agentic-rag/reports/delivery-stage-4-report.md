# Delivery Stage 4 Report：AI 对话即 Agent Workspace

> 状态：`READY_FOR_SMOKE`
> 日期：2026-08-09

## 交付结果

现有 `/conversation` 是唯一主要 Agent 入口，不新增独立页面。用户直接提出目标，Supervisor 自动判断是否检索合约数据库、检索权威知识库、读取源码、编译或预审。Agent Run 创建后立即返回，前端按约 700ms 轮询运行快照，持续展示规划、模型决策摘要、Tool 执行、Evidence 产生、综合与终态；完成后展示最终答案和可展开证据来源。

合约详情、搜索和智能预审中的按钮仅负责把当前上下文带入 AI 对话，不再被描述为主要“验证入口”。普通 AI Chat 保留为显式 fallback，不会静默重复调用模型。

## 本次修改

- `cloud-agent-service`
  - Agent Run 从同步阻塞改为受限线程池异步执行；`POST /runs` 立即返回可轮询的 `runId`。
  - Mini Agent Loop 在每个状态变化时发布 `AgentEvent`，运行中的 `GET /runs/{id}` 可看到新增事件及动态计数。
  - 终态统一写入 archive；异步崩溃转换为可见的 `FAILED / INTERNAL_ERROR`。
  - 增加安全的 Tool alias：模型返回 `retrieve_knowledge` 等无版本名时，仅可解析到已注册的同名 `.v1` Tool，之后仍使用 canonical definition 做 Tool Policy 校验。未知名称继续拒绝。
- `frontend`
  - Agent 模式创建 Run 后持续轮询，不再等待整个多轮模型/工具链完成才一次性显示结果。
  - 消息内时间线显示：接收与规划、每轮可审核 decision summary、Tool 进行中/完成、证据数、综合和失败原因。
  - 最终卡片保留决策/Tool/检索轮次、模型、Evidence ID、来源摘要和 Trust Level。
  - 空状态示例改为检索、比较、编译/预审等目标型任务，明确 Agent 会自动选择知识库和工具。
  - 详情、搜索、预审入口统一弱化为“在 AI 对话中继续/深入分析”。
- Contract test 的目录定位同步到整理后的 `backend/`、`frontend/` 项目结构。

## 自动验证

- `cloud-agent-service`：52 tests，0 failures，2 个需真实外部环境的 integration tests skipped。
- Tool alias 单测确认 `mock_echo` 只能解析到已注册 `mock_echo.v1`，随机未知 Tool 仍失败。
- Agent Loop event sink 单测确认事件按 `plan → decision → synthesis → complete` 顺序发布。
- 前端 production build：809 modules transformed，构建成功。
- 本检查未调用 GLM，不增加模型费用。

## 人工测试清单（只需 1 次真实任务）

1. 重启 `cloud-agent-service`；Gateway、`cloud-service`、数据库和 runtimes 保持运行。
2. Vite 正在运行时等待 hot reload；否则启动 `frontend`。
3. 打开 `/conversation`，保持默认 `Agent` 模式。
4. 输入：`找一个适合积分系统、普通用户不能随意 mint 的 ERC20 合约，并给出证据`。
5. 发送后应立即出现 `Agent 正在执行`，随后逐步看到规划、检索知识/搜索合约、读取源码等步骤，而不是长时间只有 loading。
6. 最终应为 `Agent 任务完成`，显示 Tool/检索轮次，并可展开 Evidence 查看来源、Trust Level 和 Evidence ID。
7. 重点确认不再出现 `Unknown tool: retrieve_knowledge`；步骤应显示为“检索领域知识”。
8. 可选：切换 `普通对话`，确认原有聊天仍正常。无需再运行更多 Agent 任务。

## 需要重启的服务

- 必须：`cloud-agent-service`（后端异步逻辑和 Tool alias 变更）。
- 条件性：`frontend`（Vite 未自动 hot reload 时）。
- 不需要：Gateway、`cloud-service`、PostgreSQL/ParadeDB、`contract-runtime-service`、`lean-runtime-service`。

## 回滚

- UI：切换到 `普通对话`，旧 Chat 与历史记录不变。
- Agent：关闭 `AGENT_ROUTE_ENABLED` 或 `AGENT_ENABLED`；旧搜索、详情、预审和 Contract Lab 不受影响。
- 本次无数据库 migration、无 schema 变更、无知识库重建。
