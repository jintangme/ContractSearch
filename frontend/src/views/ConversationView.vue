<template>
  <main class="conversation-page">
    <section class="conversation-shell">
      <header class="conversation-card-head">
        <div class="conversation-title-group">
          <button class="new-chat-button" type="button" @click="resetConversation">新对话</button>
          <h1 v-if="currentConversationTitle !== '新对话'">{{ currentConversationTitle }}</h1>
        </div>
        <button class="history-button" type="button" @click="openHistory">
          对话记录
          <span v-if="sessions.length">{{ sessions.length }}</span>
        </button>
      </header>

      <div ref="messageListRef" class="message-list">
        <div v-if="!messages.length" class="empty-state">
          <h2>有什么想了解的？</h2>
          <p>直接描述你的需求，系统会自动检索合约库和知识库，并在需要时检查源码、编译结果与安全风险。</p>
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
            <section v-if="message.role === 'assistant' && message.agentRun?.timeline?.length" class="activity-stream">
              <ol>
                <li v-for="step in message.agentRun.timeline" :key="step.key" :class="`activity-line--${step.status}`">
                  <i aria-hidden="true"></i>
                  <span>{{ step.label }}</span>
                </li>
              </ol>
            </section>
            <div v-else-if="message.role === 'assistant' && message.loading" class="assistant-loading">
              <span>正在理解你的问题</span><i></i><i></i><i></i>
            </div>
            <MarkdownMessage v-if="message.role === 'assistant' && !message.loading" :content="message.content" />
            <p v-if="message.role !== 'assistant'" class="message-content">{{ message.content }}</p>
            <section v-if="message.agentRun?.state === 'COMPLETE' && message.agentRun?.evidence?.length && !message.loading" class="answer-sources">
              <details>
                <summary title="查看参考来源" aria-label="查看参考来源">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                    <path d="M10.6 13.4a4.5 4.5 0 0 0 6.36 0l2.12-2.12a4.5 4.5 0 0 0-6.36-6.36L11.5 6.14" />
                    <path d="M13.4 10.6a4.5 4.5 0 0 0-6.36 0l-2.12 2.12a4.5 4.5 0 0 0 6.36 6.36l1.22-1.22" />
                  </svg>
                </summary>
                <div class="source-list">
                  <details v-for="(item, index) in message.agentRun.evidence" :key="item.id" class="source-item">
                    <summary>
                      <i>{{ index + 1 }}</i>
                      <strong>{{ item.title || item.sourceType }}</strong>
                      <span :class="`trust-badge trust-badge--${String(item.trustLevel || '').toLowerCase()}`">{{ trustLevelLabel(item.trustLevel) }}</span>
                    </summary>
                    <div class="source-detail">
                      <p>{{ item.excerpt || '暂无摘要' }}</p>
                      <small v-if="item.relatedCount > 1">已合并 {{ item.relatedCount }} 条相关依据</small>
                      <small>{{ item.sourceRef }}</small>
                    </div>
                  </details>
                  <p v-if="message.agentRun.hiddenEvidenceCount" class="source-more">
                    另有 {{ message.agentRun.hiddenEvidenceCount }} 个来源已收起
                  </p>
                </div>
              </details>
            </section>
            <div v-if="message.attachments?.length" class="message-attachments">
              <span v-for="attachment in message.attachments" :key="attachment.localId || attachment.id" class="attachment-chip">
                {{ attachment.fileName }}
              </span>
            </div>
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
            </div>
          </div>
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
            :class="{ active: session.id === currentAgentSessionId }"
            @click="loadSession(session.id)"
          >
            <div>
              <strong :title="session.title || '未命名对话'">{{ summarizeSessionTitle(session.title) }}</strong>
              <span>{{ formatTime(session.updatedAt) }}</span>
            </div>
            <button type="button" @click.stop="askDeleteSession(session)">删除</button>
          </article>
        </div>
      </div>
    </aside>

    <div v-if="pickerOpen" class="picker-backdrop" @click.self="closePicker">
      <section class="picker">
        <header>
          <h2>选择合约</h2>
          <button type="button" @click="closePicker">×</button>
        </header>
        <input
          v-model="contractQuery"
          class="picker-search"
          placeholder="搜索合约名或地址"
        />
        <div class="picker-list">
          <button
            v-for="item in pickerItems"
            :key="item.id"
            type="button"
            @click="attachContract(item)"
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

  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { agentApi, contractApi } from '@/api'
import MarkdownMessage from '@/components/MarkdownMessage.vue'

const DETAIL_TO_AGENT_KEY = 'contractDetailToAgentContext'

const currentAgentSessionId = ref(null)
const inputText = ref('')
const messages = ref([])
const sessions = ref([])
const attachments = ref([])
const attachmentPreview = ref(null)
const attachmentMenuOpen = ref(false)
const historyOpen = ref(false)
const pickerOpen = ref(false)
const pickerType = ref('')
const contractQuery = ref('')
const contractOptions = ref([])
const isSending = ref(false)
const errorMessage = ref('')
const messageListRef = ref(null)
const deleteConfirm = ref({ open: false, mode: 'delete', session: null })
const copiedMessageId = ref('')

const canSend = computed(() => inputText.value.trim().length > 0 && !isSending.value)
const currentConversationTitle = computed(() => {
  if (messages.value.length && !currentAgentSessionId.value) return 'AI 对话'
  if (!currentAgentSessionId.value) return '新对话'
  return sessions.value.find(item => item.id === currentAgentSessionId.value)?.title || 'AI 对话'
})
const pickerItems = computed(() => {
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
  await consumeContractDetailTransfer()
  window.addEventListener('conversation:new', resetConversation)
})

onBeforeUnmount(() => {
  window.removeEventListener('conversation:new', resetConversation)
})

function resetConversation() {
  currentAgentSessionId.value = null
  messages.value = []
  attachments.value = []
  inputText.value = ''
  errorMessage.value = ''
}

function useSuggestion(text) {
  inputText.value = text
}

async function loadSessions() {
  try {
    const response = await agentApi.listSessions()
    sessions.value = Array.isArray(response) ? response : []
  } catch {
    sessions.value = []
  }
}

async function loadSession(id) {
  try {
    const [session, runs] = await Promise.all([
      agentApi.getSession(id),
      agentApi.getSessionRuns(id),
    ])
    currentAgentSessionId.value = session?.id || id
    messages.value = (Array.isArray(runs) ? runs : []).flatMap(run => [
      {
        localId: `history-user-${run.id}`,
        role: 'user',
        content: run.task,
        createdAt: run.createdAt,
        attachments: [],
      },
      {
        localId: `history-assistant-${run.id}`,
        role: 'assistant',
        content: run.state === 'COMPLETE' ? (run.answer || '分析已完成。') : friendlyRunFailure(run),
        createdAt: run.updatedAt,
        attachments: [],
        agentRun: summarizeAgentRun(run),
      },
    ])
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
    await agentApi.deleteSession(session.id)
    if (currentAgentSessionId.value === session.id) resetConversation()
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
    await Promise.all(list.map(session => agentApi.deleteSession(session.id)))
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
  const workspaceContext = buildWorkspaceContext(attachments.value)
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
    createdAt: new Date().toISOString(),
    attachments: []
  })
  inputText.value = ''
  attachments.value = []
  attachmentPreview.value = null
  try {
    await sendAgentMessage({ text, assistantLocalId, workspaceContext })
  } finally {
    isSending.value = false
  }
}

async function sendAgentMessage({ text, assistantLocalId, workspaceContext }) {
  try {
    if (!currentAgentSessionId.value) {
      const session = await agentApi.createSession({
        title: summarizeSessionTitle(text),
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
        ? (run?.answer || '分析已完成，但暂时没有可展示的答案。')
        : friendlyRunFailure(run),
      createdAt: run?.updatedAt || new Date().toISOString(),
      attachments: [],
      agentRun,
    })
    await loadSessions()
  } catch (error) {
    replaceMessage(assistantLocalId, {
      localId: assistantLocalId,
      role: 'assistant',
      content: `AI 助手暂时不可用：${friendlyAgentError(error)}`,
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
  const evidenceSummary = summarizeEvidence(run.evidence)
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
    evidence: evidenceSummary.items,
    evidenceTotal: evidenceSummary.total,
    hiddenEvidenceCount: evidenceSummary.hidden,
  }
}

function summarizeEvidence(rawEvidence) {
  const trustPriority = { VERIFIED: 0, AUTHORITATIVE: 1, CURATED: 2, TOOL_GENERATED: 3, COMMUNITY: 4, USER_PROVIDED: 5, UNTRUSTED: 6 }
  const grouped = new Map()
  const evidence = Array.isArray(rawEvidence) ? rawEvidence : []
  evidence.forEach(item => {
    const title = String(item?.title || item?.sourceType || '参考资料').trim()
    const key = `${item?.sourceType || ''}:${title}`
    const existing = grouped.get(key)
    if (existing) {
      existing.relatedCount += 1
      return
    }
    grouped.set(key, {
      ...item,
      title,
      excerpt: String(item?.excerpt || '').replace(/\s+/g, ' ').trim().slice(0, 360),
      sourceRef: String(item?.sourceRef || ''),
      relatedCount: 1,
    })
  })
  const unique = [...grouped.values()].sort((a, b) =>
    (trustPriority[a.trustLevel] ?? 9) - (trustPriority[b.trustLevel] ?? 9))
  const items = unique.slice(0, 5)
  return { items, total: unique.length, hidden: Math.max(0, unique.length - items.length) }
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
    content: '正在处理你的问题…',
    loading: !['COMPLETE', 'FAILED'].includes(run?.state),
    createdAt: run?.updatedAt || new Date().toISOString(),
    attachments: [],
    agentRun: summarizeAgentRun(run),
  })
}

function buildAgentTimeline(events = [], runState = 'RECEIVED') {
  const timeline = []
  const toolRows = new Map()
  const add = (key, label, status = 'complete') => {
    const existing = timeline.find(item => item.key === key)
    if (existing) Object.assign(existing, { label, status })
    else timeline.push({ key, label, status })
  }
  if (!events.length) add('received', '正在理解你的问题', runState === 'FAILED' ? 'failed' : 'running')
  events.forEach(event => {
    const payload = event?.data?.payload || {}
    if (event.type === 'run.plan') add('plan', '已理解问题，正在查找相关信息')
    if (event.type === 'tool.requested') {
      const id = String(payload.toolCallId || event.sequence)
      const key = `tool-${String(payload.tool || 'other').replace(/\.v\d+$/, '')}`
      const row = timeline.find(item => item.key === key)
        || { key, label: toolActivityLabel(payload.tool, false), status: 'running' }
      Object.assign(row, { label: toolActivityLabel(payload.tool, false), status: 'running' })
      toolRows.set(id, row)
      if (!timeline.includes(row)) timeline.push(row)
    }
    if (event.type === 'tool.completed') {
      const id = String(payload.toolCallId || '')
      const row = toolRows.get(id)
      if (row) Object.assign(row, { status: 'complete', label: toolActivityLabel(payload.tool, true) })
      else add(`tool-complete-${event.sequence}`, toolActivityLabel(payload.tool, true))
    }
    if (event.type === 'run.synthesizing') add('synthesis', '正在整理资料并生成回答', 'running')
    if (event.type === 'run.completed') add('synthesis', '已完成资料整理', 'complete')
    if (event.type === 'run.failed') add('failed', '处理过程中遇到问题', 'failed')
  })
  if (runState === 'PLAN') add('current-work', '正在查找与问题相关的信息', 'running')
  if (runState === 'OBSERVE') add('current-work', '正在整理刚刚找到的资料', 'running')
  return timeline
}

function delay(ms) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}

function buildWorkspaceContext(items = []) {
  const selectedContract = items.find(item => item.sourceType === 'contract' && Number(item.contractId) > 0)
  const context = { origin: 'conversation' }
  if (selectedContract) context.selectedContractId = Number(selectedContract.contractId)
  return context
}

function friendlyAgentError(error) {
  const text = String(error?.message || error || '')
  if (text.includes('404') || text.includes('AGENT_DISABLED')) return '智能问答服务尚未启用'
  if (text.includes('timeout') || text.includes('超时')) return '处理超时，请稍后重试'
  return '请检查智能问答服务是否正常运行'
}

function friendlyRunFailure(run = {}) {
  return {
    LOOP_DETECTED: '资料已经找到，但整理过程出现了重复。请重新发送一次问题。',
    BUDGET_EXCEEDED: '问题涉及的范围较广，本次未能在限定时间内完成。请缩小范围后重试。',
    PROVIDER_ERROR: '回答生成暂时出现问题，请稍后重试。',
    TOOL_TIMEOUT: '检查相关资料时响应超时，请稍后重试。',
    UNKNOWN_TOOL: '当前无法完成所需的检查，请稍后重试。',
  }[run?.errorCode] || '本次未能完成回答，请稍后重试。'
}

function toolActivityLabel(tool, completed) {
  const labels = {
    'retrieve_knowledge.v1': ['正在检索知识库', '已检索知识库'],
    retrieve_knowledge: ['正在检索知识库', '已检索知识库'],
    'search_contracts.v1': ['正在搜索合约库', '已搜索合约库'],
    search_contracts: ['正在搜索合约库', '已搜索合约库'],
    'get_contract.v1': ['正在读取合约源码', '已读取合约源码'],
    get_contract: ['正在读取合约源码', '已读取合约源码'],
    'compile_contract.v1': ['正在检查合约能否编译', '已检查合约编译结果'],
    compile_contract: ['正在检查合约能否编译', '已检查合约编译结果'],
    'precheck_contract.v1': ['正在检查合约安全风险', '已完成基础安全检查'],
    precheck_contract: ['正在检查合约安全风险', '已完成基础安全检查'],
  }
  return labels[tool]?.[completed ? 1 : 0] || (completed ? '已完成一项检查' : '正在检查相关信息')
}

function trustLevelLabel(level) {
  return {
    VERIFIED: '已验证',
    AUTHORITATIVE: '权威来源',
    CURATED: '精选资料',
    COMMUNITY: '社区资料',
    USER_PROVIDED: '用户提供',
    TOOL_GENERATED: '工具结果',
    UNTRUSTED: '待核验',
  }[level] || '参考资料'
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

async function consumeContractDetailTransfer() {
  const raw = sessionStorage.getItem(DETAIL_TO_AGENT_KEY)
  if (!raw) return
  sessionStorage.removeItem(DETAIL_TO_AGENT_KEY)
  try {
    const transfer = JSON.parse(raw)
    const contractId = Number(transfer?.contractId)
    if (!Number.isInteger(contractId) || contractId <= 0) return
    const response = await contractApi.getContractDetail(contractId)
    const detail = response?.data
    if (!detail) throw new Error('Contract not found')
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
    inputText.value = `请解读 ${detail.contractName || '当前合约'}，说明其核心功能、权限结构、主要风险和适用场景。`
  } catch {
    errorMessage.value = '无法引用该合约，请重新选择'
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

function sourceTypeLabel(type) {
  return {
    contract: '合约库'
  }[type] || '附件'
}

function summarizeSessionTitle(value) {
  const original = String(value || '')
    .replace(/```[\s\S]*?```/g, '代码')
    .replace(/[#*_`>\[\]()]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!original) return '未命名对话'

  const concise = original
    .replace(/^(请问|请帮我|请你|帮我|我想知道|我想了解|我想确认|能不能|是否可以)\s*/u, '')
    .split(/[。！？?!；;\n]/u)[0]
    .trim() || original
  const characters = Array.from(concise)
  return characters.length > 18 ? `${characters.slice(0, 18).join('')}…` : concise
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

.activity-stream {
  margin-bottom: 14px;
}

.activity-stream ol {
  margin: 0;
  padding: 0;
  list-style: none;
}

.activity-stream li {
  min-height: 26px;
  display: flex;
  align-items: center;
  gap: 9px;
  color: #667085;
  font-size: 13px;
}

.activity-stream li i {
  width: 7px;
  height: 7px;
  flex: 0 0 7px;
  border-radius: 50%;
  background: #98a2b3;
}

.activity-stream li.activity-line--running {
  color: #344054;
  font-weight: 650;
}

.activity-stream li.activity-line--running i {
  background: #111827;
  box-shadow: 0 0 0 4px rgba(17, 24, 39, 0.1);
  animation: activity-pulse 1.15s ease-in-out infinite;
}

.activity-stream li.activity-line--failed {
  color: #475467;
}

.activity-stream li.activity-line--failed i {
  background: #667085;
}

@keyframes activity-pulse {
  50% { opacity: 0.45; transform: scale(0.82); }
}

.answer-sources {
  margin-top: 7px;
}

.answer-sources > details > summary {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #98a2b3;
  cursor: pointer;
  list-style: none;
  transition: color 0.16s ease, background 0.16s ease;
}

.answer-sources > details > summary::-webkit-details-marker {
  display: none;
}

.answer-sources > details > summary::marker {
  content: '';
}

.answer-sources > details > summary:hover {
  color: #344054;
  background: #f3f4f6;
}

.answer-sources > details > summary svg {
  width: 16px;
  height: 16px;
}

.source-list {
  margin-top: 8px;
  border-top: 1px solid #eef1f5;
}

.source-item {
  border-bottom: 1px solid #eef1f5;
}

.source-item > summary {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #344054;
  cursor: pointer;
  list-style: none;
}

.source-item > summary::-webkit-details-marker {
  display: none;
}

.source-item > summary i {
  width: 20px;
  height: 20px;
  flex: 0 0 20px;
  border-radius: 6px;
  background: #f3f5f7;
  color: #667085;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-style: normal;
}

.source-item > summary strong {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #344054;
  font-size: 12px;
  font-weight: 650;
}

.source-detail {
  padding: 0 4px 10px 28px;
}

.source-detail p {
  margin: 0 0 6px;
  color: #667085;
  font-size: 12px;
  line-height: 1.55;
}

.source-detail small {
  display: block;
  margin-top: 3px;
  color: #98a2b3;
  font-size: 10px;
  word-break: break-all;
}

.source-more {
  margin: 9px 0 0;
  color: #98a2b3;
  font-size: 11px;
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
  background: #e9ecef;
  color: #344054;
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
  color: #475467;
  font-size: 13px;
}

.composer-actions {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.send-button {
  margin-left: auto;
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

.session-item > div {
  flex: 1;
  min-width: 0;
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item span {
  color: #98a2b3;
  font-size: 12px;
}

.session-item button {
  flex: 0 0 auto;
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

  .conversation-card-head {
    flex-wrap: wrap;
    height: auto;
    padding-block: 10px;
  }
}
</style>
