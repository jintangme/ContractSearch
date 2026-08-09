<template>
  <div class="contract-check-page">
    <div class="page-header">
      <div class="page-header__top">
        <div>
          <p class="page-eyebrow">AI CONTRACT REVIEW</p>
          <h1 class="page-title">智能预审</h1>
        </div>
        <button class="history-trigger" @click="recordsOpen = true" aria-label="查看预审记录">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M8 6h13" />
            <path d="M8 12h13" />
            <path d="M8 18h13" />
            <path d="M3 6h.01" />
            <path d="M3 12h.01" />
            <path d="M3 18h.01" />
          </svg>
          <strong>查看记录</strong>
          <span v-if="historyList.length">{{ historyList.length }}</span>
        </button>
      </div>
      <p class="page-subtitle">
        评估源码结构、风险线索与收录价值。
      </p>
    </div>

    <section v-if="reviewStage === 'input'" class="panel panel--editor">
      <div class="review-input-head">
        <div>
          <h2>提交合约</h2>
          <p>选择来源并提交 Solidity 源码。</p>
        </div>
        <span v-if="importNotice">{{ importNotice }}</span>
      </div>

      <div class="source-bar">
        <div class="input-tabs" role="tablist" aria-label="源码来源">
          <button :class="{ active: sourceMode === 'paste' }" @click="setSourceMode('paste')">粘贴源码</button>
          <button :class="{ active: sourceMode === 'upload' }" @click="setSourceMode('upload')">上传源码</button>
          <button
            :class="{ active: sourceMode === 'workspace' }"
            :disabled="labWorkspaceFiles.length === 0"
            @click="setSourceMode('workspace')"
          >
            工作台文件
          </button>
        </div>

        <span v-if="sourceCurrentText" class="source-current">{{ sourceCurrentText }}</span>

        <label v-if="sourceMode === 'workspace'" class="source-select-field">
          <select
            v-model="selectedLabFileId"
            class="field-input"
            :disabled="labWorkspaceFiles.length === 0"
            @change="handleLabFileSelect"
          >
            <option value="">{{ labWorkspaceFiles.length ? '工作台文件' : '暂无文件' }}</option>
            <option v-for="file in labWorkspaceFiles" :key="file.id" :value="file.id">
              {{ file.name }}
            </option>
          </select>
        </label>

        <input ref="fileInput" type="file" accept=".sol,.txt" @change="handleFileUpload" />
      </div>

      <div class="contract-name-line">
        <span>合约</span>
        <strong>{{ detectedContractName || '未识别' }}</strong>
      </div>

      <div
        v-if="sourceMode === 'upload' && !form.sourceCode.trim()"
        class="upload-card"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="handleFileDrop"
        :class="{ 'is-dragging': dragging }"
      >
        <div class="upload-card__copy">
          <strong>上传源码文件</strong>
          <span>.sol / .txt</span>
        </div>
        <button class="source-action-btn" type="button" @click="openFilePicker">选择文件</button>
      </div>

      <label v-if="sourceMode !== 'upload' || form.sourceCode.trim()" class="source-label">源码内容</label>

      <div v-if="sourceMode !== 'upload' || form.sourceCode.trim()" class="field">
        <textarea
          v-model="form.sourceCode"
          class="code-area"
          :readonly="sourceMode === 'workspace'"
          :placeholder="sourceMode === 'paste' ? '粘贴完整 Solidity 源码' : '源码内容将在这里预览'"
        ></textarea>
      </div>

      <div class="action-row action-row--review">
        <AiModelSelect v-model="selectedModel" @update:model-value="persistSelectedModel" />
        <button class="btn btn--primary" @click="handleCheck" :disabled="checking || !canStartReview">
          <span v-if="checking" class="spinner spinner--light"></span>
          {{ checking ? '预审中...' : '开始预审' }}
        </button>
      </div>
    </section>

    <section v-if="error" class="panel">
      <p class="error-text">{{ error }}</p>
    </section>

    <section v-if="result && reviewStage === 'result'" class="panel panel--result review-result-shell">
      <div class="panel-head">
        <div>
          <h2 class="panel-title">预审结果</h2>
          <p class="panel-desc">
            {{ form.contractName || '未命名合约' }} · {{ result.saved ? `记录 #${result.recordId}` : '已生成结果' }}
            · 由 {{ result.modelUsed || selectedModel }} 预审
          </p>
        </div>

        <div class="head-actions">
          <div class="head-actions__left">
            <button type="button" class="btn btn--agent" @click="continueInAgent">
              在 Agent 对话中继续
            </button>
            <button v-if="showFixAction" type="button" class="btn btn--ghost" @click="returnToContractLabFix">
              返回实验室
            </button>
          </div>
          <div class="head-actions__right">
            <button v-if="showSaveDraftAction" type="button" class="btn btn--secondary" :disabled="Boolean(recordActionLoading)" @click="handleSaveDraft">
              {{ recordActionLoading === 'draft' ? '保存中...' : '保存草稿' }}
            </button>
            <button v-if="showSubmitAction" type="button" class="btn btn--primary" :disabled="Boolean(recordActionLoading)" @click="handleSubmitForReview">
              {{ recordActionLoading === 'submission' ? '提交中...' : '申请收录' }}
            </button>
          </div>
        </div>
      </div>

      <p v-if="recordActionMessage" class="action-feedback action-feedback--success">{{ recordActionMessage }}</p>
      <p v-if="recordActionError" class="action-feedback action-feedback--error">{{ recordActionError }}</p>

      <div class="review-diagnostics-layout">
        <section class="source-review-panel" aria-label="只读源码预览">
          <div class="source-review-head">
            <strong>{{ fileName || `${form.contractName || 'Contract'}.sol` }}</strong>
            <span>只读预览</span>
          </div>
          <div class="source-code-view">
            <div
              v-for="line in sourcePreviewLines"
              :id="`review-line-${line.number}`"
              :key="line.number"
              class="source-code-line"
              :class="sourceLineClass(line.number)"
              :title="sourceLineTooltip(line.number)"
            >
              <span class="source-line-number">{{ line.number }}</span>
              <code>{{ line.text || ' ' }}</code>
            </div>
          </div>
        </section>

        <aside class="review-summary-panel">
          <section class="review-summary-block">
            <div class="review-summary-kicker">预审结论</div>
            <strong>{{ reviewConclusionTitle }}</strong>
            <p>{{ reviewConclusionDesc }}</p>
          </section>

          <div class="risk-overview-line">
            <span>风险概览</span>
            <strong>{{ riskOverviewText }}</strong>
          </div>

          <section class="diagnostic-list-section">
            <h3>问题列表</h3>
            <div v-if="visibleDiagnostics.length" class="diagnostic-list">
              <article
                v-for="item in visibleDiagnostics"
                :key="item.id"
                class="diagnostic-row"
                :class="`diagnostic-card--${item.severity}`"
                :title="item.suggestion || item.message"
                @click="scrollToDiagnosticLine(item)"
              >
                <div class="diagnostic-row-meta">
                  <span>{{ severityLabel(item.severity) }}</span>
                  <small>{{ diagnosticLocationLabel(item) }}</small>
                </div>
                <strong>{{ item.title }}</strong>
                <p>{{ item.message }}</p>
              </article>
            </div>
            <p v-else class="empty-text">暂无问题</p>
          </section>
        </aside>
      </div>
    </section>

    <div v-if="recordsOpen" class="records-backdrop" @click="recordsOpen = false">
      <section class="records-modal" @click.stop>
        <div class="records-modal__head">
          <div>
            <p class="page-eyebrow">REVIEW HISTORY</p>
            <h2>预审记录</h2>
          </div>
          <button class="records-close" @click="recordsOpen = false" aria-label="关闭预审记录">×</button>
        </div>

        <div v-if="historyList.length" class="history-list">
          <article
            v-for="(item, index) in historyList"
            :key="index"
            class="history-item"
          >
            <div class="history-item__main">
              <div class="history-item__title">{{ item.contractName || '未命名合约' }}</div>
              <div class="history-item__meta">
                {{ item.checkedAt }} · {{ item.inputMode === 'upload' ? '上传源码' : '粘贴源码' }}
                <template v-if="item.fileName"> · {{ item.fileName }}</template>
              </div>
            </div>
            <div class="history-item__right">
              <span class="status-pill" :class="riskPillClass(item.result?.riskLevel)">
                {{ riskText(item.result?.riskLevel) }}
              </span>
              <span class="status-pill" :class="recommendationPillClass(item.result?.recommendation)">
                {{ recommendationText(item.result?.recommendation) }}
              </span>
              <button
                class="record-copy-btn"
                @click="copyHistoryReport(item, index)"
                :aria-label="copiedHistoryIndex === index ? '已复制' : '复制记录文本'"
                :title="copiedHistoryIndex === index ? '已复制' : '复制文本'"
              >
                <svg v-if="copiedHistoryIndex === index" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 6 9 17l-5-5" />
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="9" y="9" width="13" height="13" rx="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
              </button>
            </div>
          </article>
        </div>

        <p v-else class="records-empty">暂无预审记录</p>

        <div class="records-modal__foot" v-if="historyList.length">
          <button class="btn btn--ghost" @click="clearHistory">清空记录</button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { contractApi } from '@/api'
import AiModelSelect from '@/components/AiModelSelect.vue'
import { getStoredAiModel, persistAiModel } from '@/config/aiModels'

const STORAGE_KEY = 'contract_check_history_v2'
const PRECHECK_TRANSFER_KEY = 'contractLabPrecheckSource'
const PRECHECK_REPORT_TRANSFER_KEY = 'contractLabPrecheckReport'
const LAB_WORKSPACE_KEY = 'contractLabWorkspaceV1'

const router = useRouter()

const form = reactive({
  contractName: '',
  sourceCode: '',
})

const fileName = ref('')
const selectedFile = ref(null)
const fileInput = ref(null)
const inputMode = ref('paste')
const sourceMode = ref('paste')
const dragging = ref(false)
const checking = ref(false)
const error = ref('')
const result = ref(null)
const reviewStage = ref('input')
const historyList = ref([])
const recordsOpen = ref(false)
const copiedHistoryIndex = ref(null)
const recordActionLoading = ref('')
const recordActionMessage = ref('')
const recordActionError = ref('')
const importNotice = ref('')
const selectedModel = ref(getStoredAiModel())
const selectedLabFileId = ref('')
const labWorkspaceFiles = ref([])

const detectedContractName = computed(() => (
  extractContractName(form.sourceCode) || form.contractName.trim()
))

const recognizedContractLabel = computed(() => (
  detectedContractName.value ? `识别合约：${detectedContractName.value}` : '未识别合约名'
))

const currentSourceFileName = computed(() => (
  labWorkspaceFiles.value.find(item => item.id === selectedLabFileId.value)?.name
  || fileName.value
  || '未选择文件'
))

const sourceCurrentText = computed(() => {
  if (sourceMode.value === 'workspace') {
    return selectedLabFileId.value || fileName.value
      ? currentSourceFileName.value
      : ''
  }
  if (sourceMode.value === 'upload') {
    return fileName.value || ''
  }
  return ''
})

const overallConclusion = computed(() => {
  if (!result.value) return ''
  return conclusionText(result.value)
})

const reviewStatusTitle = computed(() => {
  if (!result.value) return ''
  if (result.value.recommendation === 'yes') return '通过预审'
  if (result.value.recommendation === 'review') return '需要复核'
  return '未通过预审'
})

const codeStateText = computed(() => {
  if (!result.value) return ''
  if (result.value.validSolidity && result.value.structurallyComplete) return '有效且完整'
  if (result.value.validSolidity) return '结构待确认'
  return '未通过'
})

const highRiskIssues = computed(() => {
  if (!result.value?.issues?.length) return []
  return result.value.issues.filter(item => {
    const text = String(item).toLowerCase()
    return (
      text.includes('tx.origin') ||
      text.includes('delegatecall') ||
      text.includes('selfdestruct') ||
      text.includes('suicide') ||
      text.includes('安全风险')
    )
  })
})

const riskScoreRows = computed(() => {
  const scores = result.value?.riskScores
  if (!scores) return []
  return [
    { label: '权限风险', value: normalizeScore(scores.permissionRisk) },
    { label: '逻辑复杂度', value: normalizeScore(scores.logicComplexityRisk) },
    { label: '资金流转风险', value: normalizeScore(scores.fundFlowRisk) },
  ]
})

const annotationRows = computed(() => {
  const annotations = result.value?.annotations || []
  if (!annotations.length) return []
  const source = form.sourceCode || ''
  return annotations.map(item => buildAnnotationRow(source, item))
})

const canStartReview = computed(() => (
  sourceMode.value === 'upload'
    ? Boolean(selectedFile.value)
    : Boolean(form.sourceCode.trim())
))

const sourcePreviewLines = computed(() => (
  String(form.sourceCode || '').split('\n').map((text, index) => ({
    number: index + 1,
    text
  }))
))

const reviewDiagnostics = computed(() => normalizeReviewDiagnostics())

const locatedDiagnostics = computed(() => (
  reviewDiagnostics.value.filter(item => Number.isFinite(Number(item.line)) && Number(item.line) > 0)
))

const unlocatedDiagnostics = computed(() => (
  reviewDiagnostics.value.filter(item => !Number.isFinite(Number(item.line)) || Number(item.line) <= 0)
))

const diagnosticCountRows = computed(() => {
  const counts = { critical: 0, high: 0, medium: 0, low: 0 }
  reviewDiagnostics.value.forEach(item => {
    if (counts[item.severity] !== undefined) counts[item.severity] += 1
  })
  return [
    { key: 'critical', label: 'Critical', value: counts.critical },
    { key: 'high', label: 'High', value: counts.high },
    { key: 'medium', label: 'Medium', value: counts.medium },
    { key: 'low', label: 'Low', value: counts.low }
  ].filter(item => item.value > 0)
})

const visibleDiagnostics = computed(() => {
  const priority = { critical: 0, high: 1, medium: 2, low: 3, info: 4 }
  return [...reviewDiagnostics.value].sort((a, b) => {
    const severityDiff = (priority[a.severity] ?? 4) - (priority[b.severity] ?? 4)
    if (severityDiff !== 0) return severityDiff
    const aLine = Number(a.line) || Number.MAX_SAFE_INTEGER
    const bLine = Number(b.line) || Number.MAX_SAFE_INTEGER
    return aLine - bLine
  })
})

const riskOverviewText = computed(() => (
  diagnosticCountRows.value.length
    ? `${hasBlockingIssues.value ? '阻断问题' : '优化建议'} · ${diagnosticCountRows.value.map(item => `${item.label} ${item.value}`).join(' · ')}`
    : '无问题'
))

const reviewConclusionTitle = computed(() => {
  const counts = Object.fromEntries(diagnosticCountRows.value.map(item => [item.key, item.value]))
  if (hasBlockingIssues.value) return '需修复'
  if ((counts.medium || 0) > 0 || (counts.low || 0) > 0) return '通过，建议优化'
  return '通过'
})

const reviewConclusionDesc = computed(() => {
  const counts = Object.fromEntries(diagnosticCountRows.value.map(item => [item.key, item.value]))
  if (hasBlockingIssues.value) {
    return '该源码存在阻断性问题，建议修复后再申请收录。'
  }
  if ((counts.medium || 0) > 0 || (counts.low || 0) > 0) {
    return '未发现阻断性问题，可保存草稿或申请收录。'
  }
  return '该源码未发现明显风险，可保存草稿或申请收录。'
})

const sourceStructureUnrecognized = computed(() => Boolean(
  result.value && !extractContractName(form.sourceCode) && !form.contractName.trim()
))
const hasBlockingIssues = computed(() => (
  !String(form.sourceCode || '').trim()
  || result.value?.validSolidity === false
  || sourceStructureUnrecognized.value
  || visibleDiagnostics.value.some(item => ['critical', 'high'].includes(item.severity))
))
const hasAnyIssues = computed(() => visibleDiagnostics.value.length > 0)
const showFixAction = computed(() => hasAnyIssues.value)
const showSaveDraftAction = computed(() => result.value && !hasBlockingIssues.value)
const showSubmitAction = computed(() => result.value && !hasBlockingIssues.value)

watch(
  () => form.sourceCode,
  (newCode) => {
    if (!form.contractName.trim() && newCode?.trim()) {
      const detectedName = extractContractName(newCode)
      if (detectedName) {
        form.contractName = detectedName
      }
    }
  }
)

function extractContractName(code) {
  if (!code || typeof code !== 'string') return ''

  const patterns = [
    /\babstract\s+contract\s+([A-Za-z_][A-Za-z0-9_]*)/i,
    /\bcontract\s+([A-Za-z_][A-Za-z0-9_]*)/i,
    /\binterface\s+([A-Za-z_][A-Za-z0-9_]*)/i,
    /\blibrary\s+([A-Za-z_][A-Za-z0-9_]*)/i,
  ]

  for (const pattern of patterns) {
    const match = code.match(pattern)
    if (match?.[1]) return match[1]
  }

  return ''
}

function fileNameToContractName(name) {
  return String(name || '')
    .replace(/\.(sol|txt)$/i, '')
    .replace(/[^\w\u4e00-\u9fa5-]/g, '')
}

function importSourceFromContractLab() {
  let payload = null

  try {
    const raw = sessionStorage.getItem(PRECHECK_TRANSFER_KEY)
    if (!raw) return
    sessionStorage.removeItem(PRECHECK_TRANSFER_KEY)
    payload = JSON.parse(raw)
  } catch (err) {
    sessionStorage.removeItem(PRECHECK_TRANSFER_KEY)
    return
  }

  if (payload?.source !== 'contract-lab' || typeof payload.sourceCode !== 'string' || !payload.sourceCode.trim()) {
    return
  }

  if (form.sourceCode.trim()) {
    return
  }

  sourceMode.value = 'workspace'
  inputMode.value = 'paste'
  form.sourceCode = payload.sourceCode
  form.contractName = fileNameToContractName(payload.fileName) || extractContractName(payload.sourceCode) || form.contractName
  fileName.value = payload.fileName || ''
  selectedLabFileId.value = findLabFileIdByPayload(payload)
  selectedFile.value = null
  if (fileInput.value) fileInput.value.value = ''
  error.value = ''
  result.value = null
  importNotice.value = '已从合约实验室导入当前源码'
}

function loadLabWorkspaceFiles() {
  try {
    const raw = localStorage.getItem(LAB_WORKSPACE_KEY)
    const parsed = raw ? JSON.parse(raw) : null
    const files = Array.isArray(parsed?.files) ? parsed.files : []
    labWorkspaceFiles.value = files
      .filter(file => (
        file
        && !file.readonly
        && file.folderId !== 'templates'
        && /\.sol$/i.test(String(file.name || ''))
        && typeof file.content === 'string'
      ))
      .map(file => ({
        id: file.id,
        name: file.name,
        content: file.content,
        meta: file.meta || {}
      }))
  } catch {
    labWorkspaceFiles.value = []
  }
}

function handleLabFileSelect() {
  const file = labWorkspaceFiles.value.find(item => item.id === selectedLabFileId.value)
  if (!file) return

  sourceMode.value = 'workspace'
  inputMode.value = 'paste'
  form.sourceCode = file.content || ''
  form.contractName = extractContractName(file.content) || fileNameToContractName(file.name) || form.contractName
  fileName.value = file.name
  selectedFile.value = null
  if (fileInput.value) fileInput.value.value = ''
  error.value = ''
  result.value = null
  reviewStage.value = 'input'
  importNotice.value = '已选择实验室工作台文件'
}

function findLabFileIdByPayload(payload) {
  const fileNameValue = String(payload?.fileName || '')
  const sourceValue = String(payload?.sourceCode || '')
  return labWorkspaceFiles.value.find(file => (
    (fileNameValue && file.name === fileNameValue)
    || (sourceValue && file.content === sourceValue)
  ))?.id || ''
}

function handleFileUpload(event) {
  const file = event.target.files?.[0]
  handleSelectedFile(file)
}

function handleFileDrop(event) {
  dragging.value = false
  const file = event.dataTransfer?.files?.[0]
  handleSelectedFile(file)
}

function handleSelectedFile(file) {
  if (!file) return

  if (!/\.(sol|txt)$/i.test(file.name)) {
    error.value = '仅支持上传 .sol 或 .txt 源码文件'
    return
  }

  selectedFile.value = file
  fileName.value = file.name
  error.value = ''

  const reader = new FileReader()
  reader.onload = (e) => {
    const content = String(e.target?.result || '')
    form.sourceCode = content

    const detectedName = extractContractName(content)
    if (detectedName) {
      form.contractName = detectedName
    } else {
      form.contractName = file.name.replace(/\.(sol|txt)$/i, '')
    }
  }

  reader.readAsText(file, 'utf-8')
}

function openFilePicker() {
  fileInput.value?.click()
}

function removeFile() {
  selectedFile.value = null
  fileName.value = ''
  form.sourceCode = ''
  if (fileInput.value) fileInput.value.value = ''
}

function setSourceMode(mode) {
  sourceMode.value = mode
  inputMode.value = mode === 'upload' ? 'upload' : 'paste'
  error.value = ''
  result.value = null

  if (mode === 'paste') {
    selectedFile.value = null
    selectedLabFileId.value = ''
    fileName.value = ''
    if (fileInput.value) fileInput.value.value = ''
  } else if (mode === 'upload') {
    selectedLabFileId.value = ''
  } else if (mode === 'workspace') {
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
  }
}

function persistSelectedModel() {
  selectedModel.value = persistAiModel(selectedModel.value)
}

function returnToInput() {
  reviewStage.value = 'input'
}

function buildPrecheckReport() {
  const contractName = form.contractName.trim() || extractContractName(form.sourceCode) || fileNameToContractName(fileName.value) || '未命名合约'
  return {
    id: result.value?.recordId ? String(result.value.recordId) : `precheck-${Date.now()}`,
    contractName,
    fileName: fileName.value || `${contractName}.sol`,
    sourceCode: form.sourceCode,
    conclusion: reviewConclusionTitle.value,
    conclusionText: reviewConclusionDesc.value,
    riskLevel: inferOverallRiskLevel(),
    issueCount: visibleDiagnostics.value.length,
    issues: visibleDiagnostics.value.map(item => ({
      id: item.id,
      severity: item.severity,
      title: item.title,
      message: item.message,
      line: item.line || null,
      column: item.column || null,
      suggestion: item.suggestion || '',
      source: item.source || 'ai'
    })),
    createdAt: Date.now()
  }
}

function buildRecordPayload() {
  const report = buildPrecheckReport()
  return {
    contractName: report.contractName,
    sourceCode: report.sourceCode,
    precheckConclusion: report.conclusion,
    riskLevel: report.riskLevel || inferOverallRiskLevel(),
    issueCount: report.issueCount,
    issues: report.issues
  }
}

function inferOverallRiskLevel() {
  if (visibleDiagnostics.value.some(item => ['critical', 'high'].includes(item.severity))) return 'high'
  if (visibleDiagnostics.value.some(item => item.severity === 'medium')) return 'medium'
  if (visibleDiagnostics.value.some(item => item.severity === 'low')) return 'low'
  return result.value?.riskLevel || 'low'
}

async function persistContractRecord(kind) {
  if (!result.value) return
  if (hasBlockingIssues.value) {
    recordActionError.value = '存在阻断问题，修复后再继续。'
    return
  }
  recordActionLoading.value = kind
  recordActionError.value = ''
  recordActionMessage.value = ''
  try {
    const payload = buildRecordPayload()
    const res = kind === 'draft'
      ? await contractApi.saveDraftContract(payload)
      : await contractApi.submitContractForReview(payload)
    if (!res.status) {
      throw new Error(res.message || (kind === 'draft' ? '保存失败' : '提交失败'))
    }
    recordActionMessage.value = kind === 'draft' ? '已保存草稿' : '已提交申请'
  } catch (err) {
    console.warn(kind === 'draft' ? '保存草稿失败' : '提交申请失败', err)
    recordActionError.value = kind === 'draft' ? '保存失败，请重试' : '提交失败，请重试'
  } finally {
    recordActionLoading.value = ''
  }
}

function handleSaveDraft() {
  persistContractRecord('draft')
}

function handleSubmitForReview() {
  persistContractRecord('submission')
}

function returnToContractLabFix() {
  const report = buildPrecheckReport()
  sessionStorage.setItem(PRECHECK_REPORT_TRANSFER_KEY, JSON.stringify(report))
  router.push('/contract-lab')
}

function continueInAgent() {
  if (!result.value) return
  const contractName = form.contractName.trim() || extractContractName(form.sourceCode) || '当前合约'
  const issueSummary = visibleDiagnostics.value.slice(0, 5)
    .map(item => `${item.severity}: ${item.title} - ${item.message}`)
    .join('\n')
  router.push({
    path: '/conversation',
    query: {
      agent: '1',
      prompt: `请结合权威知识库复核 ${contractName} 的预审方向。以下是现有预审摘要，仅作为任务背景，不得当作 Evidence：风险级别 ${inferOverallRiskLevel()}；${issueSummary || '未列出具体问题'}。请检索可引用依据，说明哪些结论可支持、哪些仍需源码或工具验证。`.slice(0, 1800)
    }
  })
}

async function handleCheck() {
  error.value = ''
  recordActionError.value = ''
  recordActionMessage.value = ''
  if (reviewStage.value === 'input') {
    result.value = null
  }
  if (sourceMode.value !== 'upload' && !form.sourceCode.trim()) {
    error.value = '请先选择或填写合约源码'
    return
  }

  if (sourceMode.value === 'upload' && !selectedFile.value) {
    error.value = '请先上传 .sol 或 .txt 源码文件'
    return
  }

  if (!form.contractName.trim()) {
    const detectedName = extractContractName(form.sourceCode)
    if (detectedName) {
      form.contractName = detectedName
    }
  }

  checking.value = true
  try {
    const res = sourceMode.value === 'upload'
      ? await submitUploadedFile()
      : await contractApi.checkContract({
          contractName: form.contractName.trim(),
          sourceCode: form.sourceCode,
          inputMode: 'paste',
          model: selectedModel.value,
        })

    if (!res.status) {
      throw new Error(res.message || '预审失败')
    }

    result.value = res.data
    console.info(`[precheck] 请求模型：${res.data?.modelUsed || selectedModel.value}`)
    reviewStage.value = 'result'
    saveHistory({
      contractName: form.contractName.trim() || '未命名合约',
      sourceCode: form.sourceCode,
      inputMode: sourceMode.value,
      fileName: fileName.value,
      result: res.data,
      checkedAt: new Date().toLocaleString(),
    })
  } catch (err) {
    console.warn('智能预审失败', err)
    error.value = '预审失败，请稍后重试'
  } finally {
    checking.value = false
  }
}

async function submitUploadedFile() {
  const data = new FormData()
  data.append('file', selectedFile.value)
  data.append('model', selectedModel.value)
  if (form.contractName.trim()) {
    data.append('contractName', form.contractName.trim())
  }
  return contractApi.checkContractFile(data)
}

function recommendationText(value) {
  if (value === 'yes') return '建议收录'
  if (value === 'review') return '建议复核'
  if (value === 'no') return '不建议收录'
  return '未知'
}

function conclusionText(r = {}) {
  if (r.conclusion) return r.conclusion
  if (r.recommendation === 'yes') {
    return '该合约通过基础预审，适合进入平台展示。'
  }
  if (r.recommendation === 'review') {
    return '该合约具备一定收录价值，但建议进入人工复核流程后再决定是否收录。'
  }
  return '该合约当前不建议直接收录，建议先修复明显问题或补充完整实现。'
}

function recommendClass(value) {
  if (value === 'yes') return 'is-good'
  if (value === 'review') return 'is-warn'
  if (value === 'no') return 'is-bad'
  return ''
}

function riskPillClass(level) {
  if (level === 'low') return 'pill--risk-low'
  if (level === 'medium') return 'pill--risk-medium'
  if (level === 'high') return 'pill--risk-high'
  return 'pill--neutral'
}

function recommendationPillClass(value) {
  if (value === 'yes') return 'pill--recommend-yes'
  if (value === 'review') return 'pill--recommend-review'
  if (value === 'no') return 'pill--recommend-no'
  return 'pill--neutral'
}

function riskText(level) {
  if (level === 'low') return '低'
  if (level === 'medium') return '中'
  if (level === 'high') return '高'
  return '未知'
}

function complexityText(level) {
  if (level === 'low') return '低复杂度'
  if (level === 'medium') return '中等复杂度'
  if (level === 'high') return '高复杂度'
  return '待确认'
}

function severityLabel(severity) {
  const labels = {
    critical: 'Critical',
    high: 'High',
    medium: 'Medium',
    low: 'Low',
    info: 'Info'
  }
  return labels[severity] || 'Info'
}

function diagnosticLocationLabel(item) {
  return item?.line ? `Line ${item.line}` : '未定位'
}

function inferSeverity(text, fallback = 'medium') {
  const value = String(text || '').toLowerCase()
  if (value.includes('critical') || value.includes('严重') || value.includes('重大')) return 'critical'
  if (value.includes('high') || value.includes('高风险') || value.includes('重入') || value.includes('delegatecall') || value.includes('selfdestruct') || value.includes('tx.origin')) return 'high'
  if (value.includes('medium') || value.includes('中风险') || value.includes('权限') || value.includes('建议')) return 'medium'
  if (value.includes('low') || value.includes('低风险') || value.includes('风格')) return 'low'
  return fallback
}

function normalizeSeverityValue(value, fallback = 'medium') {
  const normalized = String(value || '').trim().toLowerCase()
  if (['critical', 'high', 'medium', 'low', 'info'].includes(normalized)) {
    return normalized
  }
  if (['error', 'fatal'].includes(normalized)) return 'high'
  if (['warning', 'warn'].includes(normalized)) return 'medium'
  return fallback
}

function normalizeDiagnosticLine(item) {
  const raw = item?.line ?? item?.lineNumber ?? item?.startLine
  const line = Number(raw)
  return Number.isFinite(line) && line > 0 ? line : null
}

function buildDiagnostic(id, payload) {
  const message = payload.message || payload.problem || payload.suggestion || payload.title || '需要进一步确认。'
  const severity = payload.severity
    ? normalizeSeverityValue(payload.severity)
    : inferSeverity(`${payload.title || ''} ${message}`, 'medium')
  return {
    id,
    severity,
    title: payload.title || payload.target || payload.problem || '预审问题',
    message,
    line: normalizeDiagnosticLine(payload),
    column: payload.column || null,
    suggestion: payload.suggestion || '',
    source: payload.source || 'ai'
  }
}

function normalizeReviewDiagnostics() {
  if (!result.value) return []
  const diagnostics = []

  ;(result.value.annotations || []).forEach((item, index) => {
    const row = buildAnnotationRow(form.sourceCode, item)
    diagnostics.push(buildDiagnostic(`annotation-${index}`, {
      ...item,
      line: Number.isFinite(Number(item.line || item.lineNumber || item.startLine))
        ? (item.line || item.lineNumber || item.startLine)
        : Number.isFinite(Number(row.line))
          ? row.line
          : null,
      title: item.target || row.target || '问题批注',
      message: item.problem || row.problem,
      suggestion: item.suggestion || row.suggestion,
      source: 'ai'
    }))
  })

  ;(result.value.issues || []).forEach((item, index) => {
    const text = String(item || '').trim()
    if (!text) return
    diagnostics.push(buildDiagnostic(`issue-${index}`, {
      title: '规则问题',
      message: text,
      severity: inferSeverity(text, 'medium'),
      source: 'rule'
    }))
  })

  ;(result.value.aiSuggestions || []).forEach((item, index) => {
    const text = String(item || '').trim()
    if (!text) return
    diagnostics.push(buildDiagnostic(`suggestion-${index}`, {
      title: '修改建议',
      message: text,
      severity: 'low',
      source: 'ai'
    }))
  })

  return diagnostics
}

function diagnosticsForLine(lineNumber) {
  return locatedDiagnostics.value.filter(item => Number(item.line) === Number(lineNumber))
}

function sourceLineClass(lineNumber) {
  const diagnostics = diagnosticsForLine(lineNumber)
  if (!diagnostics.length) return ''
  const priority = ['critical', 'high', 'medium', 'low', 'info']
  const severity = priority.find(item => diagnostics.some(diagnostic => diagnostic.severity === item)) || 'info'
  return `source-code-line--${severity}`
}

function sourceLineTooltip(lineNumber) {
  const diagnostics = diagnosticsForLine(lineNumber)
  return diagnostics.map(item => `${severityLabel(item.severity)}: ${item.message}`).join('\n')
}

function scrollToDiagnosticLine(item) {
  if (!item?.line) return
  document.getElementById(`review-line-${item.line}`)?.scrollIntoView({
    behavior: 'smooth',
    block: 'center'
  })
}

function normalizeScore(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return 0
  return Math.max(0, Math.min(100, Math.round(number)))
}

function buildAnnotationRow(source, annotation) {
  const target = String(annotation?.target || '相关代码')
  const candidates = target
    .split(/[\/、,\s]+/)
    .map(item => item.trim())
    .filter(item => item.length >= 3)

  const lines = String(source || '').split('\n')
  let matchedLineIndex = -1
  let matchedText = candidates[0] || target

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i]
    const found = candidates.find(candidate => line.toLowerCase().includes(candidate.toLowerCase()))
    if (found) {
      matchedLineIndex = i
      matchedText = found
      break
    }
  }

  if (matchedLineIndex < 0) {
    return {
      line: '-',
      target,
      before: '',
      match: target,
      after: '',
      problem: annotation?.problem || '需要进一步确认该位置的实现细节。',
      suggestion: annotation?.suggestion || '建议结合上下文进行人工复核。',
    }
  }

  const lineText = lines[matchedLineIndex]
  const lowerLine = lineText.toLowerCase()
  const lowerMatch = matchedText.toLowerCase()
  const start = lowerLine.indexOf(lowerMatch)
  const before = lineText.slice(0, start)
  const match = lineText.slice(start, start + matchedText.length)
  const after = lineText.slice(start + matchedText.length)

  return {
    line: matchedLineIndex + 1,
    target,
    before,
    match,
    after,
    problem: annotation?.problem || '需要进一步确认该位置的实现细节。',
    suggestion: annotation?.suggestion || '建议结合上下文进行人工复核。',
  }
}

function formatReport() {
  if (!result.value) return ''

  return formatRecordReport({
    contractName: form.contractName || '未命名合约',
    inputMode: inputMode.value,
    fileName: fileName.value,
    checkedAt: new Date().toLocaleString(),
    result: result.value,
  })
}

function formatRecordReport(record) {
  const r = record.result || {}
  return [
    `智能预审报告`,
    `合约名称：${record.contractName || '未命名合约'}`,
    `输入方式：${record.inputMode === 'upload' ? '上传源码' : '粘贴源码'}`,
    record.fileName ? `文件名称：${record.fileName}` : '',
    `预审时间：${record.checkedAt || new Date().toLocaleString()}`,
    `Solidity 有效性：${r.validSolidity ? '是' : '否'}`,
    `结构完整性：${r.structurallyComplete ? '基本完整' : '待确认'}`,
    `风险等级：${riskText(r.riskLevel)}`,
    `复杂度：${complexityText(r.complexityLevel)}`,
    `收录建议：${recommendationText(r.recommendation)}`,
    ``,
    `总评结论：${conclusionText(r)}`,
    ``,
    `规则检查摘要：`,
    `${r.summary || '暂无'}`,
    ``,
    `AI 预审结论：`,
    `${r.aiSummary || '暂无'}`,
    ``,
    `AI 判断类型：${r.contractType || 'unknown'}`,
    `源码长度：${r.codeLength || 0} 字符`,
    `保存状态：${r.saved ? `已保存 #${r.recordId}` : '未保存'}`,
    ``,
    `发现的问题：`,
    ...(r.issues?.length ? r.issues.map(item => `- ${item}`) : ['- 未发现明显规则问题']),
    ``,
    `AI 建议：`,
    ...(r.aiSuggestions?.length ? r.aiSuggestions.map(item => `- ${item}`) : ['- 暂无 AI 建议']),
    ``,
    `重点关注位置：`,
    ...(r.keyFocus?.length ? r.keyFocus.map(item => `- ${item}`) : ['- 暂无']),
    ``,
    `批注式修改建议：`,
    ...(r.annotations?.length ? r.annotations.map(item => `- ${item.target}：${item.problem} 建议：${item.suggestion}`) : ['- 暂无']),
  ].join('\n')
}

async function handleCopyReport() {
  if (!result.value) return
  try {
    await navigator.clipboard.writeText(formatReport())
  } catch (e) {
    console.error('复制失败', e)
  }
}

async function copyHistoryReport(item, index) {
  try {
    await navigator.clipboard.writeText(formatRecordReport(item))
    copiedHistoryIndex.value = index
    setTimeout(() => {
      copiedHistoryIndex.value = null
    }, 1400)
  } catch (e) {
    console.error('复制失败', e)
  }
}

function handleExportTxt() {
  if (!result.value) return

  const content = formatReport()
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  const filenameBase = (form.contractName || 'contract-review').replace(/[^\w\u4e00-\u9fa5-]/g, '_')

  a.href = url
  a.download = `${filenameBase}-review-report.txt`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function saveHistory(record) {
  const history = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  history.unshift(record)
  const latest = history
  localStorage.setItem(STORAGE_KEY, JSON.stringify(latest))
  historyList.value = latest
}

function loadHistory() {
  historyList.value = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
}

function clearHistory() {
  localStorage.removeItem(STORAGE_KEY)
  historyList.value = []
  copiedHistoryIndex.value = null
}

onMounted(() => {
  loadHistory()
  loadLabWorkspaceFiles()
  importSourceFromContractLab()
})
</script>

<style scoped>
.contract-check-page {
  max-width: 1280px;
  color: #111111;
}

.page-header {
  margin-bottom: 24px;
}

.page-header__top {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
}

.page-eyebrow {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: #98a2b3;
}

.page-title {
  margin: 0;
  font-size: 36px;
  line-height: 1.02;
  font-weight: 700;
  letter-spacing: -0.05em;
  color: #111111;
}

.page-subtitle {
  margin: 12px 0 0;
  max-width: 760px;
  font-size: 15px;
  line-height: 1.8;
  color: #6b7280;
}

.history-trigger {
  height: 44px;
  padding: 0 15px;
  border: 1px solid #dedede;
  border-radius: 14px;
  background: #ffffff;
  color: #333333;
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(17, 17, 17, 0.045);
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.history-trigger svg {
  width: 16px;
  height: 16px;
}

.history-trigger strong {
  font-weight: 700;
}

.history-trigger:hover {
  transform: translateY(-1px);
  border-color: #cfcfcf;
  color: #111111;
  background: #f8f8f8;
}

.history-trigger span {
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: #111111;
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
}

.panel {
  margin-bottom: 20px;
  border: 1px solid #e6e6e6;
  border-radius: 22px;
  background: #ffffff;
  box-shadow:
    0 16px 40px rgba(17, 17, 17, 0.035),
    inset 0 1px 0 rgba(255, 255, 255, 0.85);
  padding: 22px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.panel-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #111111;
}

.panel-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #98a2b3;
}

.review-result-shell {
  padding: 18px;
}

.review-diagnostics-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
  gap: 16px;
  align-items: stretch;
}

.source-review-panel,
.review-summary-panel {
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: #ffffff;
  overflow: hidden;
}

.source-review-head {
  height: 42px;
  padding: 0 14px;
  border-bottom: 1px solid #edf0f2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.source-review-head strong {
  min-width: 0;
  color: #111827;
  font-size: 13px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-review-head span {
  color: #98a2b3;
  font-size: 11px;
  font-weight: 800;
}

.source-code-view {
  max-height: 640px;
  overflow: auto;
  padding: 8px 0;
  background: #fbfcfd;
}

.source-code-line {
  min-height: 24px;
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  align-items: start;
  border-left: 3px solid transparent;
  color: #1f2937;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 12.5px;
  line-height: 1.75;
}

.source-code-line:hover {
  background: #f3f4f6;
}

.source-line-number {
  padding: 0 12px 0 0;
  color: #98a2b3;
  text-align: right;
  user-select: none;
}

.source-code-line code {
  min-width: 0;
  padding-right: 14px;
  white-space: pre;
}

.source-code-line--critical,
.source-code-line--high {
  border-left-color: #991b1b;
  background: #fff5f5;
}

.source-code-line--medium {
  border-left-color: #b45309;
  background: #fffbeb;
}

.source-code-line--low,
.source-code-line--info {
  border-left-color: #64748b;
  background: #f8fafc;
}

.review-summary-panel {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.review-summary-block {
  display: grid;
  gap: 6px;
}

.review-summary-kicker,
.risk-overview-line span,
.diagnostic-list-section h3 {
  color: #98a2b3;
  font-size: 11px;
  font-weight: 850;
  letter-spacing: 0.04em;
}

.review-summary-block strong {
  color: #111827;
  font-size: 18px;
  font-weight: 850;
}

.review-summary-block p {
  margin: 0;
  color: #475467;
  font-size: 13px;
  line-height: 1.65;
}

.risk-overview-line {
  display: grid;
  gap: 5px;
  padding-top: 12px;
  border-top: 1px solid #edf0f2;
}

.risk-overview-line strong {
  color: #111827;
  font-size: 13px;
  font-weight: 850;
}

.diagnostic-list-section {
  display: grid;
  gap: 8px;
}

.diagnostic-list-section h3 {
  margin: 0;
}

.diagnostic-list {
  display: grid;
  gap: 0;
  border-top: 1px solid #edf0f2;
}

.diagnostic-row {
  padding: 10px 2px 11px 10px;
  border-bottom: 1px solid #edf0f2;
  border-left: 2px solid transparent;
  background: transparent;
  cursor: pointer;
}

.diagnostic-row:hover {
  background: #f8fafc;
}

.diagnostic-row-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 5px;
}

.diagnostic-row-meta span {
  color: #667085;
  font-size: 10px;
  font-weight: 900;
  text-transform: uppercase;
}

.diagnostic-row-meta small {
  color: #98a2b3;
  font-size: 11px;
  font-weight: 800;
}

.diagnostic-row strong {
  display: block;
  color: #111827;
  font-size: 13px;
  font-weight: 820;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.diagnostic-row p {
  margin: 5px 0 0;
  color: #475467;
  font-size: 12px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.diagnostic-card--critical,
.diagnostic-card--high {
  border-left-color: #991b1b;
}

.diagnostic-card--medium {
  border-left-color: #b45309;
}

.diagnostic-card--low,
.diagnostic-card--info {
  border-left-color: #64748b;
}

.review-input-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.review-input-head h2 {
  margin: 0;
  color: #111111;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.review-input-head p {
  margin: 5px 0 0;
  max-width: 660px;
  color: #98a2b3;
  font-size: 13px;
  line-height: 1.7;
}

.review-input-head > span {
  flex: 0 0 auto;
  margin-top: 1px;
  padding: 5px 9px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #f8fafc;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
}

.source-bar {
  min-height: 44px;
  margin-bottom: 14px;
  padding: 5px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #fbfcfd;
  display: flex;
  align-items: center;
  gap: 10px;
}

.source-current {
  min-width: 0;
  flex: 1;
  color: #667085;
  font-size: 12px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contract-name-line {
  height: 34px;
  margin: -4px 0 10px;
  padding: 0 2px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #98a2b3;
  font-size: 12px;
  font-weight: 700;
}

.contract-name-line strong {
  color: #475467;
  font-size: 13px;
  font-weight: 750;
}

.source-select-field {
  flex: 0 0 220px;
}

.source-bar > input[type="file"] {
  display: none;
}

.source-action-btn {
  height: 32px;
  padding: 0 11px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #ffffff;
  color: #475467;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.source-action-btn:hover {
  background: #f5f6f7;
  color: #111827;
}

.upload-card {
  min-height: 132px;
  margin-bottom: 14px;
  padding: 24px 28px;
  border: 1px dashed #d7dbe2;
  border-radius: 18px;
  background: #fbfcfd;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 14px;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.upload-card.is-dragging {
  border-color: #aeb6c2;
  background: #ffffff;
}

.upload-card__copy {
  min-width: 160px;
}

.upload-card__copy strong,
.upload-card__copy span {
  display: block;
}

.upload-card__copy strong {
  color: #111827;
  font-size: 15px;
  font-weight: 800;
}

.upload-card__copy span {
  margin-top: 6px;
  color: #98a2b3;
  font-size: 12px;
  font-weight: 650;
}

.input-tabs {
  display: inline-flex;
  width: fit-content;
  gap: 2px;
  padding: 3px;
  border: 1px solid #dedede;
  border-radius: 12px;
  background: #f7f7f8;
}

.input-tabs button {
  height: 32px;
  min-width: 86px;
  padding: 0 12px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: #667085;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.input-tabs button:disabled {
  color: #b0b8c4;
  cursor: default;
}

.input-tabs button.active {
  background: #ffffff;
  color: #111111;
  box-shadow: 0 1px 2px rgba(17, 24, 39, 0.08);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.source-label {
  display: inline-block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #667085;
}

.field-input {
  height: 40px;
  border: 1px solid #dedede;
  border-radius: 12px;
  padding: 0 12px;
  font-size: 13px;
  color: #111111;
  background: #ffffff;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

select.field-input {
  cursor: pointer;
  color: #475467;
}

select.field-input:disabled {
  color: #b0b8c4;
  background: #f8fafc;
  cursor: default;
}

.field-input:focus,
.code-area:focus {
  border-color: #d1d5db;
  box-shadow: 0 0 0 4px rgba(17, 17, 17, 0.04);
}

.code-area {
  width: 100%;
  min-height: 360px;
  border: 1px solid #dedede;
  border-radius: 18px;
  padding: 16px;
  font-size: 13px;
  line-height: 1.8;
  color: #111111;
  background: #f8f8f8;
  outline: none;
  resize: vertical;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

.code-area[readonly] {
  background: #fbfcfd;
  color: #374151;
}

.action-row {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.action-row--review {
  justify-content: flex-end;
  padding-top: 2px;
}

.head-actions {
  display: grid;
  grid-template-columns: auto auto;
  align-items: center;
  gap: 18px;
  flex-shrink: 0;
}

.head-actions__left,
.head-actions__right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.head-actions__right {
  justify-content: flex-end;
}

.panel-head .btn {
  height: 32px;
  padding: 0 11px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 800;
  gap: 7px;
  box-shadow: none;
}

.panel-head .btn--primary {
  box-shadow: none;
}

.action-feedback {
  margin: -8px 0 14px;
  font-size: 13px;
  font-weight: 700;
}

.action-feedback--success {
  color: #166534;
}

.action-feedback--error {
  color: #991b1b;
}

.btn {
  position: relative;
  height: 42px;
  padding: 0 16px;
  border-radius: 14px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.01em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease,
    color 0.18s ease;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.72) inset,
    0 8px 18px rgba(17, 17, 17, 0.04);
}

.btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.btn:active:not(:disabled) {
  transform: translateY(0);
}

.btn--primary {
  border: 1px solid #111111;
  background: #111111;
  color: #ffffff;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.08) inset,
    0 14px 28px rgba(15, 23, 42, 0.14);
}

.btn--secondary {
  border: 1px solid #d8d8d8;
  background: #ffffff;
  color: #333333;
}

.btn--agent {
  border: 1px solid #b9c7ff;
  background: #eef2ff;
  color: #3448a3;
}

.btn--ghost {
  border: 1px solid #e5e5e5;
  background: #f8f8f8;
  color: #667085;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.8) inset,
    0 6px 16px rgba(15, 23, 42, 0.03);
}

.btn:disabled {
  opacity: 0.64;
  cursor: not-allowed;
  transform: none;
}

.upload-feedback {
  margin: -6px 0 14px;
  padding: 10px 12px;
  border: 1px solid #d8d8d8;
  border-radius: 12px;
  background: #f8f8f8;
  color: #333333;
  font-size: 13px;
  line-height: 1.6;
}

.upload-feedback--error {
  background: #ffffff;
  color: #111111;
}

.conclusion-banner {
  margin-bottom: 16px;
  border-radius: 18px;
  padding: 18px 20px;
  border: 1px solid #dedede;
  background: linear-gradient(180deg, #f7f7f7 0%, #ffffff 100%);
}

.conclusion-banner__label {
  margin-bottom: 10px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #111111;
}

.conclusion-banner__text {
  font-size: 15px;
  line-height: 1.85;
  font-weight: 600;
  color: #111111;
}

.risk-alert {
  margin-bottom: 16px;
  border: 1px solid #d4d4d4;
  background: #f6f6f6;
  border-radius: 18px;
  padding: 16px 18px;
}

.risk-alert__title {
  font-size: 13px;
  font-weight: 700;
  color: #111111;
  margin-bottom: 10px;
}

.risk-alert__list {
  margin: 0;
  padding-left: 18px;
  color: #333333;
  font-size: 14px;
  line-height: 1.8;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.risk-score-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.risk-score-row {
  border: 1px solid #e6e6e6;
  border-radius: 16px;
  background: #f8f8f8;
  padding: 14px;
}

.risk-score-row__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 9px;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
}

.risk-score-row__meta strong {
  color: #111111;
}

.risk-score-track {
  height: 7px;
  border-radius: 999px;
  background: #e5e5e5;
  overflow: hidden;
}

.risk-score-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #333333;
}

.metric-card {
  border: 1px solid #e6e6e6;
  border-radius: 16px;
  padding: 14px 16px;
  background: #f8f8f8;
}

.metric-label {
  display: block;
  margin-bottom: 10px;
  font-size: 12px;
  color: #98a2b3;
}

.metric-value {
  font-size: 15px;
  font-weight: 700;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.content-grid--two {
  grid-template-columns: 1fr 1fr;
}

.info-card {
  border: 1px solid #e6e6e6;
  border-radius: 18px;
  padding: 16px;
  background: #ffffff;
}

.info-card__label {
  margin: 0 0 10px;
  font-size: 12px;
  color: #98a2b3;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.info-card__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: #111111;
  white-space: pre-wrap;
}

.plain-list {
  margin: 0;
  padding-left: 18px;
  font-size: 14px;
  line-height: 1.85;
  color: #111111;
}

.empty-text {
  margin: 0;
  font-size: 14px;
  color: #98a2b3;
}

.focus-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.focus-list span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid #e2e2e2;
  background: #f7f7f7;
  color: #475467;
  font-size: 12px;
  font-weight: 700;
}

.annotation-panel {
  margin-top: 14px;
  border: 1px solid #e2e2e2;
  border-radius: 20px;
  background: #ffffff;
  padding: 18px;
}

.annotation-panel__head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}

.annotation-panel__head h3 {
  margin: 0;
  color: #111111;
  font-size: 16px;
  letter-spacing: -0.02em;
}

.annotation-panel__head p {
  margin: 6px 0 0;
  color: #98a2b3;
  font-size: 13px;
  line-height: 1.7;
}

.annotation-thread {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.annotation-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) 46px minmax(260px, 0.8fr);
  align-items: stretch;
  gap: 0;
}

.annotation-source {
  position: relative;
  min-width: 0;
  border: 1px solid #e6e6e6;
  border-radius: 16px;
  background: #fafafa;
  padding: 13px 14px 13px 52px;
}

.annotation-line {
  position: absolute;
  left: 14px;
  top: 14px;
  color: #a3a3a3;
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

.annotation-source pre {
  margin: 0;
  overflow: auto;
  color: #222222;
  font-size: 12.5px;
  line-height: 1.75;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  white-space: pre;
}

.annotation-source mark {
  padding: 1px 3px;
  border-radius: 4px;
  background: rgba(127, 29, 29, 0.12);
  color: #7f1d1d;
  font-weight: 700;
}

.annotation-connector {
  position: relative;
}

.annotation-connector::before {
  content: "";
  position: absolute;
  left: 8px;
  right: 8px;
  top: 50%;
  height: 1px;
  background: #bdbdbd;
}

.annotation-connector::after {
  content: "";
  position: absolute;
  right: 8px;
  top: calc(50% - 4px);
  width: 8px;
  height: 8px;
  border-top: 1px solid #bdbdbd;
  border-right: 1px solid #bdbdbd;
  transform: rotate(45deg);
}

.annotation-note {
  border: 1px solid #d8d8d8;
  border-radius: 16px;
  background: #f7f7f7;
  padding: 13px 14px;
}

.annotation-note strong {
  display: block;
  margin-bottom: 7px;
  color: #111111;
  font-size: 13px;
}

.annotation-note p {
  margin: 0;
  color: #333333;
  font-size: 13px;
  line-height: 1.72;
}

.annotation-note span {
  display: block;
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px solid #e2e2e2;
  color: #667085;
  font-size: 12px;
  line-height: 1.7;
}

.records-backdrop {
  position: fixed;
  inset: 0;
  z-index: 260;
  padding: 28px;
  background: rgba(17, 17, 17, 0.32);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.records-modal {
  width: min(820px, 100%);
  max-height: min(760px, calc(100vh - 56px));
  display: flex;
  flex-direction: column;
  border: 1px solid #dedede;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 28px 80px rgba(17, 17, 17, 0.24);
  padding: 20px;
}

.records-modal__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eeeeee;
}

.records-modal__head h2 {
  margin: 0;
  color: #111111;
  font-size: 22px;
  letter-spacing: -0.03em;
}

.records-close {
  width: 34px;
  height: 34px;
  border: 1px solid #dedede;
  border-radius: 12px;
  background: #ffffff;
  color: #667085;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.records-close:hover {
  background: #f8f8f8;
  color: #111111;
}

.records-modal__foot {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
  border-top: 1px solid #eeeeee;
}

.records-empty {
  margin: 28px 0;
  color: #98a2b3;
  font-size: 14px;
  text-align: center;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
  padding: 14px 2px;
}

.history-item {
  width: 100%;
  border: 1px solid #e6e6e6;
  border-radius: 16px;
  background: #ffffff;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.history-item:hover {
  transform: translateY(-1px);
  border-color: #cfcfcf;
  background: #f9f9f9;
}

.history-item__main {
  flex: 1;
  min-width: 0;
}

.history-item__title {
  margin-bottom: 4px;
  font-size: 14px;
  font-weight: 700;
  color: #111111;
}

.history-item__meta {
  font-size: 12px;
  color: #98a2b3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-item__right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.record-copy-btn {
  width: 38px;
  height: 38px;
  border: 1px solid #d8d8d8;
  border-radius: 12px;
  background: #ffffff;
  color: #333333;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 10px 22px rgba(17, 17, 17, 0.045);
  transition: background 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
}

.record-copy-btn:hover {
  transform: translateY(-1px);
  border-color: #cfcfcf;
  background: #f8f8f8;
}

.record-copy-btn svg {
  width: 16px;
  height: 16px;
}

.btn--sm {
  height: 32px;
  padding: 0 11px;
  border-radius: 10px;
  font-size: 12px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.01em;
  border: 1px solid transparent;
}

.pill--risk-low {
  color: #111111;
  background: #ffffff;
  border-color: #cfcfcf;
}

.pill--risk-medium {
  color: #111111;
  background: #dcdcdc;
  border-color: #c7c7c7;
}

.pill--risk-high {
  color: #ffffff;
  background: #111111;
  border-color: #111111;
}

.pill--recommend-yes,
.pill--recommend-review,
.pill--recommend-no {
  color: #111111;
  background: #f2f2f2;
  border-color: #d8d8d8;
}

.pill--recommend-review {
  background: #e8e8e8;
}

.pill--recommend-no {
  background: #dcdcdc;
}

.pill--neutral {
  color: #475467;
  background: rgba(71, 84, 103, 0.06);
  border-color: rgba(71, 84, 103, 0.12);
}

.is-good {
  color: #111111;
}

.is-warn {
  color: #333333;
}

.is-bad {
  color: #111111;
}

.error-text {
  margin: 0;
  color: #111111;
  font-size: 14px;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(17, 17, 17, 0.18);
  border-top-color: #111111;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.spinner--light {
  border-color: rgba(255, 255, 255, 0.28);
  border-top-color: #ffffff;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 860px) {
  .form-grid,
  .review-input-top,
  .review-diagnostics-layout,
  .metrics-grid,
  .content-grid--two,
  .risk-score-strip {
    grid-template-columns: 1fr;
  }

  .page-title {
    font-size: 30px;
  }

  .page-header__top {
    flex-direction: column;
    align-items: flex-start;
  }

  .panel-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .head-actions {
    width: 100%;
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .head-actions__left,
  .head-actions__right,
  .action-row--review {
    width: 100%;
    justify-content: flex-start;
  }

  .action-row {
    justify-content: flex-start;
  }

  .source-bar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .source-current,
  .source-select-field,
  .source-action-btn,
  .upload-card {
    flex: 1 1 100%;
  }

  .upload-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .records-backdrop {
    padding: 0;
    align-items: stretch;
  }

  .records-modal {
    max-height: none;
    height: 100vh;
    border-radius: 0;
    border-left: none;
    border-right: none;
  }

  .history-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .history-item__right {
    justify-content: flex-start;
  }

  .annotation-row {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .annotation-connector {
    height: 18px;
  }

  .annotation-connector::before {
    left: 24px;
    right: auto;
    top: 0;
    bottom: 0;
    width: 1px;
    height: auto;
  }

  .annotation-connector::after {
    left: 20px;
    right: auto;
    top: auto;
    bottom: 1px;
    transform: rotate(135deg);
  }
}
</style>
