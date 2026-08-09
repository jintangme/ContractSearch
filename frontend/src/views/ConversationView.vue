<template>
  <main class="conversation-page">
    <section class="conversation-shell">
      <header class="conversation-card-head">
        <div class="conversation-title-group">
          <button class="new-chat-button" type="button" @click="resetConversation">新对话</button>
          <h1 v-if="currentConversationTitle !== '新对话'">{{ currentConversationTitle }}</h1>
        </div>
        <div class="conversation-mode-switch" aria-label="对话模式">
          <button type="button" :class="{ active: conversationMode === 'agent' }" @click="setConversationMode('agent')">
            Agent
          </button>
          <button type="button" :class="{ active: conversationMode === 'chat' }" @click="setConversationMode('chat')">
            普通对话
          </button>
        </div>
        <button class="history-button" type="button" @click="openHistory">
          对话记录
          <span v-if="sessions.length">{{ sessions.length }}</span>
        </button>
      </header>

      <div ref="messageListRef" class="message-list">
        <div v-if="!messages.length" class="empty-state">
          <h2>直接告诉 Agent 你想完成什么</h2>
          <p>它会自动判断是否检索合约库、领域知识库，或调用编译与安全工具，并展示执行过程和证据。</p>
          <div class="suggestions">
            <button type="button" @click="useSuggestion('找一个适合积分系统、普通用户不能随意 mint 的 ERC20 合约，并给出证据')">查找可复用 ERC20</button>
            <button type="button" @click="useSuggestion('比较合约库中 ERC20 与 ERC721 的实现和安全差异，并说明依据')">比较合约与标准</button>
            <button type="button" @click="useSuggestion('找一个多签钱包合约，检查源码、编译结果和主要风险')">检索并验证多签合约</button>
          </div>
        </div>

        <article
          v-for="message in messages"
          :key="message.localId || message.id"
          class="message-row"
          :class="`message-row--${message.role}`"
        >
          <div class="message-bubble">
            <div v-if="message.role === 'assistant' && message.loading" class="assistant-loading">
              <span>{{ message.agentLoading ? 'Agent 正在检索与验证' : '正在分析' }}</span>
              <i></i>
              <i></i>
              <i></i>
            </div>
            <MarkdownMessage v-else-if="message.role === 'assistant'" :content="message.content" />
            <p v-else class="message-content">{{ message.content }}</p>
            <section v-if="message.agentRun" class="agent-run-card" :class="`agent-run-card--${String(message.agentRun.state || '').toLowerCase()}`">
              <div class="agent-run-head">
                <div>
                  <strong>{{ agentRunTitle(message.agentRun.state) }}</strong>
                  <span>{{ message.agentRun.steps }} 次决策 · {{ message.agentRun.toolCalls }} 次工具 · {{ message.agentRun.retrievalRounds }} 轮检索</span>
                </div>
                <code>{{ message.agentRun.model }}</code>
              </div>
              <ol v-if="message.agentRun.timeline?.length" class="agent-step-list">
                <li v-for="step in message.agentRun.timeline" :key="step.key" :class="`agent-step--${step.status}`">
                  <span>{{ step.label }}</span>
                  <small>{{ timelineStatusLabel(step.status) }}</small>
                  <p v-if="step.detail">{{ step.detail }}</p>
                </li>
              </ol>
              <details v-if="message.agentRun.evidence?.length" class="agent-evidence">
                <summary>查看 Evidence（{{ message.agentRun.evidence.length }}）</summary>
                <article v-for="item in message.agentRun.evidence" :key="item.id">
                  <div>
                    <strong>{{ item.title || item.sourceType }}</strong>
                    <span :class="`trust-badge trust-badge--${String(item.trustLevel || '').toLowerCase()}`">{{ item.trustLevel }}</span>
                  </div>
                  <p>{{ item.excerpt || item.sourceRef }}</p>
                  <code>{{ item.id }}</code>
                </article>
              </details>
              <button v-if="message.agentRun.state === 'FAILED'" type="button" class="agent-fallback" @click="retryWithStandardChat(message)">
                切换普通对话重试
              </button>
            </section>
            <div v-if="message.attachments?.length" class="message-attachments">
              <span v-for="attachment in message.attachments" :key="attachment.localId || attachment.id" class="attachment-chip">
                {{ attachment.fileName }}
              </span>
            </div>
            <p v-if="message.role === 'assistant' && message.modelUsed" class="model-used">
              由 {{ message.modelUsed }} 生成
            </p>
          </div>
          <div v-if="message.role === 'assistant' && !message.loading" class="message-actions">
            <button
              type="button"
              :title="copiedMessageId === (message.localId || message.id) ? '已复制' : '复制'"
              :aria-label="copiedMessageId === (message.localId || message.id) ? '已复制' : '复制'"
              @click="copyAssistantMessage(message)"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="9" y="9" width="10" height="10" rx="2" />
                <path d="M5 15H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1" />
              </svg>
            </button>
            <button
              v-if="!message.agentRun"
              type="button"
              :disabled="isSending"
              title="重新生成"
              aria-label="重新生成"
              @click="regenerateAssistantMessage(message)"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M21 12a9 9 0 0 1-15.5 6.2" />
                <path d="M3 12a9 9 0 0 1 15.5-6.2" />
                <path d="M18 3v4h-4" />
                <path d="M6 21v-4h4" />
              </svg>
            </button>
          </div>
        </article>
      </div>

      <div class="composer-wrap">
        <div class="composer">
        <div v-if="attachments.length" class="attachment-strip">
          <button
            v-for="attachment in attachments"
            :key="attachment.localId"
            class="attachment-chip attachment-chip--active"
            type="button"
            @click="previewAttachment(attachment)"
          >
            {{ attachment.fileName }}
            <span @click.stop="removeAttachment(attachment.localId)">×</span>
          </button>
        </div>

        <div v-if="attachmentPreview" class="attachment-preview">
          <strong>{{ attachmentPreview.fileName }}</strong>
          <span>{{ sourceTypeLabel(attachmentPreview.sourceType) }} · {{ attachmentPreview.fileType || 'text' }}</span>
          <button type="button" @click="attachmentPreview = null">收起</button>
        </div>

        <div class="composer-main">
          <textarea
            v-model="inputText"
            placeholder="询问合约、链上逻辑或安全问题..."
            :disabled="isSending"
            @keydown="handleInputKeydown"
          ></textarea>
        </div>

        <div v-if="errorMessage" class="inline-error">{{ errorMessage }}</div>

        <div class="composer-actions">
          <div class="attachment-menu-wrap">
            <button class="icon-button" type="button" aria-label="添加附件" @click="attachmentMenuOpen = !attachmentMenuOpen">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21.44 11.05 12.25 20.24a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
              </svg>
            </button>
            <div v-if="attachmentMenuOpen" class="attachment-menu">
              <button type="button" @click="openContractPicker">选择合约</button>
              <button v-if="conversationMode === 'chat'" type="button" @click="openWorkspacePicker">工作台文件</button>
              <button v-if="conversationMode === 'chat'" type="button" @click="openLocalFilePicker">本地文件</button>
            </div>
          </div>
          <label v-if="conversationMode === 'chat'" class="model-inline">
            <span>模型</span>
            <AiModelSelect v-model="selectedModel" label="" @update:model-value="persistSelectedModel" />
          </label>
          <span v-else class="agent-model-inline">Agent · glm-4.5-air</span>
          <button class="send-button" type="button" :disabled="!canSend" @click="sendMessage">
            {{ isSending ? '生成中...' : '发送' }}
          </button>
        </div>
        </div>
      </div>
    </section>

    <aside v-if="historyOpen" class="drawer-backdrop" @click.self="historyOpen = false">
      <div class="history-drawer">
        <div class="drawer-head">
          <h2>对话记录</h2>
          <div class="drawer-actions">
            <button v-if="sessions.length" class="clear-history-button" type="button" @click="askClearSessions">清空</button>
            <button type="button" aria-label="关闭" @click="historyOpen = false">×</button>
          </div>
        </div>
        <div v-if="!sessions.length" class="drawer-empty">暂无对话记录</div>
        <div v-else class="session-list">
          <article
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: session.id === currentSessionId }"
            @click="loadSession(session.id)"
          >
            <div>
              <strong>{{ session.title || '未命名对话' }}</strong>
              <span>{{ formatTime(session.updatedAt) }} · {{ session.model || '-' }}</span>
            </div>
            <button type="button" @click.stop="askDeleteSession(session)">删除</button>
          </article>
        </div>
      </div>
    </aside>

    <div v-if="pickerOpen" class="picker-backdrop" @click.self="closePicker">
      <section class="picker">
        <header>
          <h2>{{ pickerType === 'contract' ? '选择合约' : '工作台文件' }}</h2>
          <button type="button" @click="closePicker">×</button>
        </header>
        <input
          v-if="pickerType === 'contract'"
          v-model="contractQuery"
          class="picker-search"
          placeholder="搜索合约名或地址"
        />
        <div class="picker-list">
          <button
            v-for="item in pickerItems"
            :key="item.id"
            type="button"
            @click="pickerType === 'contract' ? attachContract(item) : attachWorkspaceFile(item)"
          >
            <strong>{{ item.contractName || item.name }}</strong>
            <span>{{ item.address || item.fileType || 'solidity' }}</span>
          </button>
          <p v-if="!pickerItems.length">暂无可选内容</p>
        </div>
      </section>
    </div>

    <div v-if="deleteConfirm.open" class="confirm-backdrop" @click.self="deleteConfirm.open = false">
      <section class="confirm-dialog">
        <h2>{{ deleteConfirm.mode === 'clear' ? '清空对话记录？' : '删除对话？' }}</h2>
        <p>{{ deleteConfirm.mode === 'clear' ? '将删除全部历史对话，当前对话也会重置。' : '删除后无法恢复。' }}</p>
        <div>
          <button type="button" @click="deleteConfirm.open = false">取消</button>
          <button type="button" class="danger" @click="deleteConfirm.mode === 'clear' ? confirmClearSessions() : confirmDeleteSession()">
            {{ deleteConfirm.mode === 'clear' ? '清空' : '删除' }}
          </button>
        </div>
      </section>
    </div>

    <input ref="localFileInput" type="file" accept=".txt,.sol,.pdf,.docx" hidden @change="handleLocalFile" />
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { agentApi, conversationApi, contractApi } from '@/api'
import AiModelSelect from '@/components/AiModelSelect.vue'
import MarkdownMessage from '@/components/MarkdownMessage.vue'
import { getStoredAiModel, persistAiModel } from '@/config/aiModels'

const LAB_WORKSPACE_KEY = 'contractLabWorkspaceV1'
const route = useRoute()
const router = useRouter()

const selectedModel = ref(getStoredAiModel())
const conversationMode = ref('agent')
const currentAgentSessionId = ref(null)
const inputText = ref('')
const messages = ref([])
const sessions = ref([])
const currentSessionId = ref(null)
const attachments = ref([])
const attachmentPreview = ref(null)
const attachmentMenuOpen = ref(false)
const historyOpen = ref(false)
const pickerOpen = ref(false)
const pickerType = ref('')
const contractQuery = ref('')
const contractOptions = ref([])
const workspaceOptions = ref([])
const isSending = ref(false)
const errorMessage = ref('')
const localFileInput = ref(null)
const messageListRef = ref(null)
const deleteConfirm = ref({ open: false, mode: 'delete', session: null })
const copiedMessageId = ref('')

const canSend = computed(() => inputText.value.trim().length > 0 && !isSending.value)
const currentConversationTitle = computed(() => {
  if (conversationMode.value === 'agent') return messages.value.length ? 'Agent 对话' : '新对话'
  if (!currentSessionId.value) return '新对话'
  return sessions.value.find(item => item.id === currentSessionId.value)?.title || '新对话'
})
const pickerItems = computed(() => {
  if (pickerType.value === 'workspace') return workspaceOptions.value
  const query = contractQuery.value.trim().toLowerCase()
  const list = contractOptions.value
  if (!query) return list.slice(0, 20)
  return list.filter(item => {
    return String(item.contractName || '').toLowerCase().includes(query)
      || String(item.address || '').toLowerCase().includes(query)
  }).slice(0, 20)
})

watch(messages, () => scrollToBottom(), { deep: true })

onMounted(async () => {
  await loadSessions()
  await consumeAgentRouteContext()
  window.addEventListener('conversation:new', resetConversation)
})

onBeforeUnmount(() => {
  window.removeEventListener('conversation:new', resetConversation)
})

function persistSelectedModel() {
  selectedModel.value = persistAiModel(selectedModel.value)
}

function resetConversation() {
  currentSessionId.value = null
  currentAgentSessionId.value = null
  messages.value = []
  attachments.value = []
  inputText.value = ''
  errorMessage.value = ''
}

function setConversationMode(mode) {
  if (!['agent', 'chat'].includes(mode) || isSending.value) return
  conversationMode.value = mode
  errorMessage.value = ''
}

function useSuggestion(text) {
  inputText.value = text
}

async function loadSessions() {
  try {
    const response = await conversationApi.getSessions()
    sessions.value = Array.isArray(response?.data) ? response.data : []
  } catch {
    sessions.value = []
  }
}

async function loadSession(id) {
  try {
    const response = await conversationApi.getSession(id)
    const session = response?.data
    currentSessionId.value = session?.id || null
    conversationMode.value = 'chat'
    messages.value = Array.isArray(session?.messages) ? session.messages : []
    attachments.value = []
    historyOpen.value = false
  } catch {
    errorMessage.value = '加载失败，请稍后重试'
  }
}

function openHistory() {
  loadSessions()
  historyOpen.value = true
}

function askDeleteSession(session) {
  deleteConfirm.value = { open: true, mode: 'delete', session }
}

function askClearSessions() {
  deleteConfirm.value = { open: true, mode: 'clear', session: null }
}

async function confirmDeleteSession() {
  const session = deleteConfirm.value.session
  if (!session) return
  try {
    await conversationApi.deleteSession(session.id)
    if (currentSessionId.value === session.id) resetConversation()
    deleteConfirm.value = { open: false, mode: 'delete', session: null }
    await loadSessions()
  } catch {
    errorMessage.value = '删除失败，请重试'
  }
}

async function confirmClearSessions() {
  const list = [...sessions.value]
  if (!list.length) {
    deleteConfirm.value = { open: false, mode: 'clear', session: null }
    return
  }
  try {
    await Promise.all(list.map(session => conversationApi.deleteSession(session.id)))
    resetConversation()
    sessions.value = []
    historyOpen.value = false
    deleteConfirm.value = { open: false, mode: 'delete', session: null }
  } catch {
    errorMessage.value = '清空失败，请重试'
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isSending.value) return
  isSending.value = true
  errorMessage.value = ''
  const outgoingAttachments = attachments.value.map(toPayload)
  const workspaceContext = buildWorkspaceContext(attachments.value)
  if (outgoingAttachments.some(item => !String(item.contentText || '').trim())) {
    errorMessage.value = '附件内容未读取'
    isSending.value = false
    return
  }
  const totalAttachmentChars = outgoingAttachments.reduce((sum, item) => sum + String(item.contentText || '').length, 0)
  console.info(`[conversation] attachments=${outgoingAttachments.length} contentLength=${totalAttachmentChars}`)
  const userMessage = {
    localId: `user-${Date.now()}`,
    role: 'user',
    content: text,
    attachments: [...attachments.value],
    createdAt: new Date().toISOString()
  }
  messages.value.push(userMessage)
  const assistantLocalId = `assistant-loading-${Date.now()}`
  messages.value.push({
    localId: assistantLocalId,
    role: 'assistant',
    content: '正在分析...',
    loading: true,
    agentLoading: conversationMode.value === 'agent',
    createdAt: new Date().toISOString(),
    attachments: []
  })
  inputText.value = ''
  attachments.value = []
  attachmentPreview.value = null
  try {
    if (conversationMode.value === 'agent') {
      await sendAgentMessage({ text, assistantLocalId, workspaceContext })
      return
    }
    const response = await conversationApi.chat({
      sessionId: currentSessionId.value,
      model: selectedModel.value,
      message: text,
      attachments: outgoingAttachments
    })
    if (response?.status === false || response?.data?.success === false) {
      throw new Error(response?.data?.errorCode || response?.message || 'AI_PROVIDER_ERROR')
    }
    const payload = response?.data
    currentSessionId.value = payload?.sessionId || currentSessionId.value
    replaceMessage(assistantLocalId, {
      id: payload?.messageId,
      localId: assistantLocalId,
      role: 'assistant',
      content: payload?.content || '生成失败，请重试',
      modelUsed: payload?.modelUsed || selectedModel.value,
      requestedModel: payload?.requestedModel,
      createdAt: payload?.createdAt || new Date().toISOString(),
      attachments: []
    })
    await loadSessions()
  } catch (error) {
    const text = String(error?.message || error || '')
    const friendlyMessage = text.includes('ATTACHMENT_CONTENT_MISSING') || text.includes('附件内容缺失')
      ? '附件内容缺失'
      : '生成失败，请重试'
    replaceMessage(assistantLocalId, {
      localId: assistantLocalId,
      role: 'assistant',
      content: friendlyMessage,
      modelUsed: selectedModel.value,
      createdAt: new Date().toISOString(),
      attachments: []
    })
  } finally {
    isSending.value = false
  }
}

async function sendAgentMessage({ text, assistantLocalId, workspaceContext }) {
  try {
    if (!currentAgentSessionId.value) {
      const session = await agentApi.createSession({
        title: text.slice(0, 120),
        workspaceContext,
      })
      currentAgentSessionId.value = session?.id || null
    }
    const createdRun = await agentApi.createRun({
      sessionId: currentAgentSessionId.value,
      task: text,
      workspaceContext,
    })
    updateAgentProgress(assistantLocalId, createdRun)
    const run = await waitForAgentRun(createdRun?.id, assistantLocalId)
    const agentRun = summarizeAgentRun(run)
    const completed = run?.state === 'COMPLETE'
    replaceMessage(assistantLocalId, {
      localId: assistantLocalId,
      role: 'assistant',
      content: completed
        ? (run?.answer || 'Agent 已完成，但未返回答案。')
        : `Agent 未能完成任务：${run?.failureMessage || run?.errorCode || '未知错误'}`,
      modelUsed: run?.model || 'glm-4.5-air',
      createdAt: run?.updatedAt || new Date().toISOString(),
      attachments: [],
      agentRun,
    })
  } catch (error) {
    replaceMessage(assistantLocalId, {
      localId: assistantLocalId,
      role: 'assistant',
      content: `Agent 服务暂时不可用：${friendlyAgentError(error)}`,
      modelUsed: 'glm-4.5-air',
      createdAt: new Date().toISOString(),
      attachments: [],
      agentRun: {
        state: 'FAILED',
        model: 'glm-4.5-air',
        steps: 0,
        toolCalls: 0,
        retrievalRounds: 0,
        timeline: [],
        evidence: [],
      },
    })
  }
}

function summarizeAgentRun(run = {}) {
  const events = Array.isArray(run.events) ? run.events : []
  return {
    id: run.id,
    state: run.state || 'FAILED',
    errorCode: run.errorCode,
    failureMessage: run.failureMessage,
    model: run.model || 'glm-4.5-air',
    steps: Number(run.steps || 0),
    toolCalls: Number(run.toolCalls || 0),
    retrievalRounds: Number(run.retrievalRounds || 0),
    timeline: buildAgentTimeline(events, run.state),
    evidence: Array.isArray(run.evidence)
      ? run.evidence.slice(0, 20).map(item => ({
          ...item,
          excerpt: String(item.excerpt || '').slice(0, 900),
        }))
      : [],
  }
}

async function waitForAgentRun(runId, assistantLocalId) {
  if (!runId) throw new Error('Agent Run ID missing')
  const deadline = Date.now() + 300000
  while (Date.now() < deadline) {
    await delay(700)
    const run = await agentApi.getRun(runId)
    updateAgentProgress(assistantLocalId, run)
    if (['COMPLETE', 'FAILED'].includes(run?.state)) return run
  }
  throw new Error('Agent 执行超时')
}

function updateAgentProgress(assistantLocalId, run = {}) {
  replaceMessage(assistantLocalId, {
    localId: assistantLocalId,
    role: 'assistant',
    content: 'Agent 正在执行任务…',
    loading: !['COMPLETE', 'FAILED'].includes(run?.state),
    agentLoading: true,
    modelUsed: run?.model || 'glm-4.5-air',
    createdAt: run?.updatedAt || new Date().toISOString(),
    attachments: [],
    agentRun: summarizeAgentRun(run),
  })
}

function buildAgentTimeline(events = [], runState = 'RECEIVED') {
  const timeline = []
  const toolRows = new Map()
  const add = (key, label, status = 'complete', detail = '') => {
    const existing = timeline.find(item => item.key === key)
    if (existing) Object.assign(existing, { label, status, detail })
    else timeline.push({ key, label, status, detail })
  }
  if (!events.length) add('received', '接收问题并准备规划', runState === 'FAILED' ? 'failed' : 'running')
  events.forEach(event => {
    const payload = event?.data?.payload || {}
    if (event.type === 'run.plan') add('plan', '理解问题并制定执行计划')
    if (event.type === 'model.action') {
      const action = payload.actionType === 'TOOL_CALL' ? '判断下一步需要补充信息' : '判断证据是否足够'
      add(`decision-${event.sequence}`, `第 ${payload.step || '?'} 次决策：${action}`, 'complete', payload.reasonSummary || payload.evidenceGap || '')
    }
    if (event.type === 'tool.requested') {
      const id = String(payload.toolCallId || event.sequence)
      const row = { key: `tool-${id}`, label: toolLabel(payload.tool || '未知工具'), status: 'running', detail: '正在调用工具并等待结果' }
      toolRows.set(id, row)
      timeline.push(row)
    }
    if (event.type === 'tool.completed') {
      const id = String(payload.toolCallId || '')
      const row = toolRows.get(id)
      if (row) Object.assign(row, { status: 'complete', detail: `已生成 ${(payload.evidenceIds || []).length} 条证据` })
      else add(`tool-complete-${event.sequence}`, toolLabel(payload.tool || '未知工具'))
    }
    if (event.type === 'run.synthesizing') add('synthesis', '整理证据并生成最终回答', 'running')
    if (event.type === 'run.completed') add('synthesis', '整理证据并生成最终回答', 'complete')
    if (event.type === 'run.failed') add('failed', 'Agent 执行终止', 'failed', payload.message || payload.errorCode || '')
  })
  return timeline
}

function delay(ms) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}

function agentRunTitle(state) {
  if (state === 'COMPLETE') return 'Agent 任务完成'
  if (state === 'FAILED') return 'Agent 任务失败'
  return 'Agent 正在执行'
}

function timelineStatusLabel(status) {
  if (status === 'running') return '进行中'
  if (status === 'failed') return '失败'
  return '已完成'
}

function buildWorkspaceContext(items = []) {
  const selectedContract = items.find(item => item.sourceType === 'contract' && Number(item.contractId) > 0)
  const workspaceFile = items.find(item => item.sourceType === 'workspace')
  const context = { origin: 'conversation' }
  if (selectedContract) context.selectedContractId = Number(selectedContract.contractId)
  if (workspaceFile?.fileName) context.activeLabFile = workspaceFile.fileName
  if (Number(currentSessionId.value) > 0) context.conversationSessionId = Number(currentSessionId.value)
  return context
}

function friendlyAgentError(error) {
  const text = String(error?.message || error || '')
  if (text.includes('404') || text.includes('AGENT_DISABLED')) return 'Agent 模式未启用，可切换普通对话'
  if (text.includes('timeout') || text.includes('超时')) return 'Agent 执行超时，可稍后重试'
  return '请检查 Agent Service 和 Gateway'
}

async function retryWithStandardChat(message) {
  const index = messages.value.findIndex(item => item === message)
  const userMessage = [...messages.value.slice(0, index)].reverse().find(item => item.role === 'user')
  if (!userMessage?.content || isSending.value) return
  conversationMode.value = 'chat'
  inputText.value = userMessage.content
  attachments.value = Array.isArray(userMessage.attachments) ? [...userMessage.attachments] : []
  await nextTick()
  sendMessage()
}

function toolLabel(tool) {
  return {
    'retrieve_knowledge.v1': '检索领域知识',
    'search_contracts.v1': '搜索合约',
    'get_contract.v1': '读取合约源码',
    'compile_contract.v1': '沙箱编译',
    'precheck_contract.v1': '安全预审',
  }[tool] || tool
}

async function copyAssistantMessage(message) {
  const content = String(message?.content || '')
  if (!content.trim()) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(content)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = content
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    copiedMessageId.value = message.localId || message.id || ''
    window.setTimeout(() => {
      if (copiedMessageId.value === (message.localId || message.id)) copiedMessageId.value = ''
    }, 1400)
  } catch {
    errorMessage.value = '复制失败，请重试'
  }
}

async function regenerateAssistantMessage(message) {
  if (isSending.value) return
  const assistantIndex = messages.value.findIndex(item => (item.localId || item.id) === (message.localId || message.id))
  if (assistantIndex <= 0) return
  let userMessage = null
  for (let index = assistantIndex - 1; index >= 0; index -= 1) {
    if (messages.value[index]?.role === 'user') {
      userMessage = messages.value[index]
      break
    }
  }
  if (!userMessage?.content) return

  isSending.value = true
  errorMessage.value = ''
  const assistantLocalId = message.localId || `assistant-regenerate-${Date.now()}`
  replaceMessage(assistantLocalId, {
    localId: assistantLocalId,
    role: 'assistant',
    content: '正在分析...',
    loading: true,
    createdAt: new Date().toISOString(),
    attachments: []
  })

  const outgoingAttachments = Array.isArray(userMessage.attachments)
    ? userMessage.attachments.map(toPayload).filter(item => String(item.contentText || '').trim())
    : []

  try {
    const response = await conversationApi.chat({
      sessionId: currentSessionId.value,
      model: selectedModel.value,
      message: userMessage.content,
      attachments: outgoingAttachments
    })
    if (response?.status === false || response?.data?.success === false) {
      throw new Error(response?.data?.errorCode || response?.message || 'AI_PROVIDER_ERROR')
    }
    const payload = response?.data
    currentSessionId.value = payload?.sessionId || currentSessionId.value
    replaceMessage(assistantLocalId, {
      id: payload?.messageId,
      localId: assistantLocalId,
      role: 'assistant',
      content: payload?.content || '生成失败，请重试',
      modelUsed: payload?.modelUsed || selectedModel.value,
      requestedModel: payload?.requestedModel,
      createdAt: payload?.createdAt || new Date().toISOString(),
      attachments: []
    })
    await loadSessions()
  } catch {
    replaceMessage(assistantLocalId, {
      localId: assistantLocalId,
      role: 'assistant',
      content: '生成失败，请重试',
      modelUsed: selectedModel.value,
      createdAt: new Date().toISOString(),
      attachments: []
    })
  } finally {
    isSending.value = false
  }
}

function replaceMessage(localId, nextMessage) {
  const index = messages.value.findIndex(item => (item.localId || item.id) === localId)
  if (index >= 0) {
    messages.value.splice(index, 1, nextMessage)
  } else {
    messages.value.push(nextMessage)
  }
}

function handleInputKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey) return
  event.preventDefault()
  sendMessage()
}

function openLocalFilePicker() {
  attachmentMenuOpen.value = false
  localFileInput.value?.click()
}

async function handleLocalFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    errorMessage.value = '附件不能超过 10MB'
    return
  }
  try {
    const extension = file.name.split('.').pop()?.toLowerCase() || ''
    let parsed
    if (extension === 'txt' || extension === 'sol') {
      parsed = { fileName: file.name, fileType: extension, contentText: await file.text() }
    } else {
      const formData = new FormData()
      formData.append('file', file)
      const response = await conversationApi.parseAttachment(formData)
      parsed = response?.data
    }
    if (!parsed?.contentText) throw new Error('parse failed')
    addAttachment({
      fileName: parsed.fileName || file.name,
      fileType: parsed.fileType || extension,
      sourceType: 'local',
      contentText: parsed.contentText
    })
  } catch {
    errorMessage.value = '附件解析失败'
  }
}

async function openContractPicker() {
  attachmentMenuOpen.value = false
  pickerType.value = 'contract'
  pickerOpen.value = true
  if (contractOptions.value.length) return
  try {
    const response = await contractApi.getAllContracts()
    contractOptions.value = Array.isArray(response?.data) ? response.data : []
  } catch {
    contractOptions.value = []
  }
}

function openWorkspacePicker() {
  attachmentMenuOpen.value = false
  pickerType.value = 'workspace'
  workspaceOptions.value = readWorkspaceFiles()
  pickerOpen.value = true
}

function closePicker() {
  pickerOpen.value = false
  pickerType.value = ''
}

async function attachContract(item) {
  try {
    const response = await contractApi.getContractDetail(item.id)
    const detail = response?.data || item
    addAttachment({
      contractId: Number(detail.id || item.id),
      fileName: `${detail.contractName || item.contractName || 'Contract'}.sol`,
      fileType: 'sol',
      sourceType: 'contract',
      contentText: [
        `合约名：${detail.contractName || ''}`,
        `地址：${detail.address || ''}`,
        `类型：${detail.category || ''}`,
        `摘要：${detail.description || ''}`,
        '源码：',
        detail.sourceCode || ''
      ].join('\n')
    })
    closePicker()
  } catch {
    errorMessage.value = '附件解析失败'
  }
}

async function consumeAgentRouteContext() {
  if (String(route.query.agent || '') !== '1') return
  conversationMode.value = 'agent'
  const prompt = String(route.query.prompt || '').trim()
  if (prompt) inputText.value = prompt.slice(0, 12000)
  const contractId = Number(route.query.contractId)
  if (Number.isInteger(contractId) && contractId > 0) {
    try {
      const response = await contractApi.getContractDetail(contractId)
      const detail = response?.data
      if (detail) {
        addAttachment({
          contractId,
          fileName: `${detail.contractName || 'Contract'}.sol`,
          fileType: 'sol',
          sourceType: 'contract',
          contentText: [
            `合约名：${detail.contractName || ''}`,
            `地址：${detail.address || ''}`,
            `类型：${detail.category || ''}`,
            `摘要：${detail.description || ''}`,
            '源码：',
            detail.sourceCode || ''
          ].join('\n')
        })
      }
    } catch {
      errorMessage.value = '无法读取来源合约，仍可直接向 Agent 提问'
    }
  }
  router.replace({ path: '/conversation' }).catch(() => {})
}

function attachWorkspaceFile(file) {
  const content = String(file.content || '')
  if (!content.trim()) {
    errorMessage.value = '附件内容未读取'
    return
  }
  addAttachment({
    fileName: file.name,
    fileType: 'sol',
    sourceType: 'workspace',
    contentText: content
  })
  closePicker()
}

function readWorkspaceFiles() {
  try {
    const parsed = JSON.parse(localStorage.getItem(LAB_WORKSPACE_KEY) || '{}')
    return Array.isArray(parsed.files)
      ? parsed.files.filter(file => !file.readonly && /\.sol$/i.test(file.name || '') && file.content)
      : []
  } catch {
    return []
  }
}

function addAttachment(attachment) {
  const next = {
    ...attachment,
    localId: `att-${Date.now()}-${Math.random().toString(16).slice(2)}`
  }
  attachments.value = [...attachments.value, next]
  errorMessage.value = ''
}

function removeAttachment(localId) {
  attachments.value = attachments.value.filter(item => item.localId !== localId)
  if (attachmentPreview.value?.localId === localId) attachmentPreview.value = null
}

function previewAttachment(attachment) {
  attachmentPreview.value = attachmentPreview.value?.localId === attachment.localId ? null : attachment
}

function toPayload(attachment) {
  return {
    fileName: attachment.fileName,
    fileType: attachment.fileType,
    sourceType: attachment.sourceType,
    contentText: String(attachment.contentText || '')
  }
}

function sourceTypeLabel(type) {
  return {
    contract: '合约库',
    workspace: '工作台',
    local: '本地文件'
  }[type] || '附件'
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const today = new Date()
  const sameDay = date.toDateString() === today.toDateString()
  return sameDay
    ? `今天 ${date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
    : date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function scrollToBottom() {
  nextTick(() => {
    const el = messageListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<style scoped>
.conversation-page {
  height: calc(100vh - 64px);
  min-height: 620px;
  padding: 0;
  background: #f7f8fa;
  color: #111827;
  display: flex;
}

.conversation-shell {
  flex: 1;
  max-width: 1120px;
  height: 100%;
  min-height: 0;
  margin: 0 auto;
  border: 1px solid #e3e7ed;
  border-radius: 18px;
  background: #fff;
  display: grid;
  grid-template-rows: 58px 1fr auto;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.06);
}

.conversation-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 0 18px 0 22px;
  border-bottom: 1px solid #eef1f5;
}

.conversation-title-group {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.conversation-mode-switch {
  display: inline-flex;
  align-items: center;
  padding: 3px;
  border: 1px solid #e1e5eb;
  border-radius: 11px;
  background: #f5f7fa;
}

.conversation-mode-switch button {
  height: 28px;
  padding: 0 11px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #667085;
  font-size: 13px;
  font-weight: 750;
  cursor: pointer;
}

.conversation-mode-switch button.active {
  background: #111827;
  color: #fff;
  box-shadow: 0 2px 7px rgba(15, 23, 42, 0.16);
}

.new-chat-button {
  height: 34px;
  padding: 0 12px;
  border: 1px solid #e1e5eb;
  border-radius: 10px;
  background: #111827;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.conversation-card-head h1 {
  margin: 0;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 17px;
  letter-spacing: 0;
}

.history-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid #e1e5eb;
  border-radius: 10px;
  background: #fff;
  color: #344054;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.history-button span {
  min-width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #111827;
  color: #fff;
  font-size: 11px;
}

.message-list {
  overflow-y: auto;
  padding: 28px clamp(24px, 5vw, 80px);
}

.empty-state {
  height: 100%;
  min-height: 280px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  gap: 18px;
}

.empty-state h2 {
  margin: 0;
  font-size: 20px;
  letter-spacing: 0;
}

.empty-state > p {
  max-width: 620px;
  margin: 8px auto 20px;
  color: #667085;
  line-height: 1.65;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.suggestions button,
.attachment-menu button,
.picker-list button,
.session-item button,
.confirm-dialog button {
  border: 1px solid #e1e5eb;
  background: #fff;
  color: #344054;
  border-radius: 10px;
  cursor: pointer;
}

.suggestions button {
  height: 36px;
  padding: 0 12px;
  font-weight: 600;
}

.message-row {
  max-width: 760px;
  margin-bottom: 24px;
}

.message-row--user {
  margin-left: auto;
}

.message-row--assistant {
  max-width: 790px;
}

.message-bubble {
  border: 1px solid #e4e7ec;
  border-radius: 14px;
  padding: 14px 16px;
  background: #fff;
}

.message-row--user .message-bubble {
  background: #f7f8fa;
}

.message-row--assistant .message-bubble {
  border-color: #e8ecf2;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcfd 100%);
  box-shadow: 0 12px 34px rgba(15, 23, 42, 0.045);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.message-row--assistant .message-bubble:hover {
  border-color: #d9dee8;
  box-shadow: 0 16px 44px rgba(15, 23, 42, 0.075);
}

.message-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
  color: #1f2937;
}

.message-attachments,
.attachment-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.attachment-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 180px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #dfe4ea;
  border-radius: 999px;
  background: #f8fafc;
  color: #475467;
  font-size: 13px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-chip--active {
  cursor: pointer;
}

.attachment-chip span {
  color: #98a2b3;
}

.model-used {
  margin: 10px 0 0;
  color: #98a2b3;
  font-size: 12px;
}

.agent-run-card {
  margin-top: 14px;
  padding: 13px;
  border: 1px solid #dfe5ec;
  border-radius: 13px;
  background: #f8fafc;
}

.agent-run-card--failed {
  border-color: #f0c7c3;
  background: #fff8f7;
}

.agent-run-head,
.agent-run-head > div,
.agent-evidence article > div {
  display: flex;
  align-items: center;
}

.agent-run-head {
  justify-content: space-between;
  gap: 12px;
}

.agent-run-head > div {
  align-items: flex-start;
  flex-direction: column;
  gap: 3px;
}

.agent-run-head strong {
  color: #111827;
  font-size: 14px;
}

.agent-run-head span,
.agent-run-head code {
  color: #667085;
  font-size: 12px;
}

.agent-step-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 7px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.agent-step-list li {
  padding: 8px 9px;
  border: 1px solid #e5e9ef;
  border-radius: 9px;
  background: #fff;
}

.agent-step-list span,
.agent-step-list small {
  display: block;
}

.agent-step-list li.agent-step--running {
  border-color: #b8cef8;
  background: #f5f8ff;
}

.agent-step-list li.agent-step--running small {
  color: #3569c8;
}

.agent-step-list li.agent-step--failed small {
  color: #d04438;
}

.agent-step-list p {
  margin: 5px 0 0;
  color: #667085;
  font-size: 11px;
  line-height: 1.45;
}

.agent-step-list span {
  color: #344054;
  font-size: 12px;
  font-weight: 700;
}

.agent-step-list small {
  margin-top: 2px;
  color: #12a56a;
  font-size: 11px;
}

.agent-evidence {
  margin-top: 11px;
}

.agent-evidence summary {
  color: #475467;
  font-size: 12px;
  font-weight: 750;
  cursor: pointer;
}

.agent-evidence article {
  margin-top: 8px;
  padding: 9px;
  border-radius: 9px;
  background: #fff;
}

.agent-evidence article > div {
  justify-content: space-between;
  gap: 10px;
}

.agent-evidence article strong {
  color: #344054;
  font-size: 12px;
}

.agent-evidence article p {
  margin: 5px 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.agent-evidence article code {
  color: #98a2b3;
  font-size: 10px;
}

.trust-badge {
  padding: 2px 6px;
  border-radius: 999px;
  background: #eef2f6;
  color: #475467;
  font-size: 10px;
  font-weight: 800;
}

.trust-badge--authoritative,
.trust-badge--verified {
  background: #e8f8f1;
  color: #087a50;
}

.agent-fallback {
  margin-top: 10px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #d0d5dd;
  border-radius: 9px;
  background: #fff;
  color: #344054;
  font-weight: 700;
  cursor: pointer;
}

.message-actions {
  margin-top: 7px;
  margin-left: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.message-actions button {
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #7a8493;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.message-actions button:hover:not(:disabled) {
  background: #f1f3f6;
  color: #111827;
  transform: translateY(-0.5px);
}

.message-actions button:active:not(:disabled) {
  transform: translateY(0);
}

.message-actions button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.message-actions svg {
  width: 16px;
  height: 16px;
}

.assistant-loading {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #667085;
  font-size: 14px;
  line-height: 1.7;
}

.assistant-loading span {
  color: #475467;
}

.assistant-loading i {
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: #98a2b3;
  animation: loadingDot 1.2s ease-in-out infinite;
}

.assistant-loading i:nth-child(3) {
  animation-delay: 0.15s;
}

.assistant-loading i:nth-child(4) {
  animation-delay: 0.3s;
}

.composer-wrap {
  border-top: 1px solid #eef1f5;
  padding: 14px clamp(18px, 4vw, 56px) 16px;
  background: #fff;
}

.composer {
  max-width: 840px;
  margin: 0 auto;
  border: 1px solid #dfe4ea;
  border-radius: 18px;
  padding: 10px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.04);
}

.attachment-strip {
  margin: 0 0 8px;
}

.attachment-preview {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #667085;
  font-size: 13px;
}

.attachment-preview strong {
  color: #111827;
}

.attachment-preview button {
  border: 0;
  background: transparent;
  color: #667085;
  cursor: pointer;
}

.composer-main {
  display: block;
}

.icon-button {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: #fff;
  color: #667085;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.icon-button svg {
  width: 18px;
  height: 18px;
}

.attachment-menu-wrap {
  position: relative;
}

.attachment-menu {
  position: absolute;
  left: 0;
  bottom: 40px;
  width: 150px;
  padding: 6px;
  border: 1px solid #e1e5eb;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.12);
  z-index: 10;
}

.attachment-menu button {
  width: 100%;
  height: 34px;
  text-align: left;
  padding: 0 10px;
  border: 0;
}

.attachment-menu button:hover,
.picker-list button:hover {
  background: #f5f7fa;
}

textarea {
  width: 100%;
  min-height: 66px;
  max-height: 180px;
  resize: vertical;
  border: 0;
  border-radius: 12px;
  padding: 8px 8px 6px;
  font: inherit;
  font-size: 14px;
  line-height: 1.6;
  outline: none;
}

textarea:focus {
  box-shadow: none;
}

.inline-error {
  margin-top: 10px;
  color: #b42318;
  font-size: 13px;
}

.composer-actions {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.model-inline {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #667085;
  font-size: 14px;
}

.agent-model-inline {
  margin-left: auto;
  color: #475467;
  font-size: 13px;
  font-weight: 750;
}

.send-button {
  height: 34px;
  min-width: 74px;
  padding: 0 14px;
  border: 0;
  border-radius: 10px;
  background: #111827;
  color: #fff;
  font-weight: 800;
  cursor: pointer;
}

.send-button:disabled {
  background: #d0d5dd;
  cursor: not-allowed;
}

@keyframes loadingDot {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.drawer-backdrop,
.picker-backdrop,
.confirm-backdrop {
  position: fixed;
  inset: 0;
  z-index: 300;
  background: rgba(15, 23, 42, 0.18);
}

.history-drawer {
  width: 360px;
  max-width: 92vw;
  height: 100%;
  margin-left: auto;
  background: #fff;
  border-left: 1px solid #e4e7ec;
  box-shadow: -20px 0 50px rgba(15, 23, 42, 0.1);
  display: flex;
  flex-direction: column;
}

.drawer-head {
  height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid #eef1f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.drawer-head h2 {
  margin: 0;
  font-size: 18px;
}

.drawer-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.drawer-head button,
.picker header button {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: #f5f7fa;
  color: #667085;
  cursor: pointer;
}

.drawer-head .clear-history-button {
  width: auto;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #e1e5eb;
  background: #fff;
  color: #667085;
  font-size: 13px;
  font-weight: 700;
}

.drawer-head .clear-history-button:hover {
  border-color: #d0d5dd;
  background: #f7f8fa;
  color: #111827;
}

.drawer-empty {
  margin: auto;
  color: #98a2b3;
  font-weight: 700;
}

.session-list {
  padding: 10px;
  overflow-y: auto;
}

.session-item {
  width: 100%;
  padding: 12px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  cursor: pointer;
}

.session-item:hover,
.session-item.active {
  background: #f6f7f9;
}

.session-item strong,
.session-item span {
  display: block;
}

.session-item strong {
  color: #111827;
  font-size: 14px;
  margin-bottom: 4px;
}

.session-item span {
  color: #98a2b3;
  font-size: 12px;
}

.session-item button {
  height: 30px;
  padding: 0 9px;
  color: #667085;
}

.picker {
  width: 520px;
  max-width: calc(100vw - 40px);
  margin: 14vh auto 0;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.16);
  overflow: hidden;
}

.picker header {
  height: 58px;
  padding: 0 16px 0 20px;
  border-bottom: 1px solid #eef1f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.picker header h2 {
  margin: 0;
  font-size: 17px;
}

.picker-search {
  width: calc(100% - 32px);
  height: 40px;
  margin: 14px 16px 8px;
  border: 1px solid #dfe4ea;
  border-radius: 12px;
  padding: 0 12px;
  outline: none;
}

.picker-list {
  max-height: 360px;
  overflow-y: auto;
  padding: 8px 10px 12px;
}

.picker-list button {
  width: 100%;
  min-height: 54px;
  padding: 8px 10px;
  display: block;
  text-align: left;
  border: 0;
}

.picker-list strong,
.picker-list span {
  display: block;
}

.picker-list strong {
  color: #111827;
  margin-bottom: 4px;
}

.picker-list span,
.picker-list p {
  color: #98a2b3;
  font-size: 13px;
}

.confirm-backdrop {
  display: flex;
  align-items: center;
  justify-content: center;
}

.confirm-dialog {
  width: 360px;
  max-width: calc(100vw - 40px);
  padding: 22px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.16);
}

.confirm-dialog h2 {
  margin: 0 0 10px;
  font-size: 18px;
}

.confirm-dialog p {
  margin: 0 0 18px;
  color: #667085;
}

.confirm-dialog div {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.confirm-dialog button {
  height: 38px;
  padding: 0 14px;
}

.confirm-dialog .danger {
  border-color: #111827;
  background: #111827;
  color: #fff;
}

@media (max-width: 760px) {
  .conversation-page {
    height: calc(100vh - 28px);
    min-height: 560px;
  }

  .composer-actions {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .conversation-shell {
    height: 100%;
  }

  .model-inline {
    margin-left: 0;
  }

  .conversation-mode-switch {
    order: 3;
  }

  .conversation-card-head {
    flex-wrap: wrap;
    height: auto;
    padding-block: 10px;
  }
}
</style>
