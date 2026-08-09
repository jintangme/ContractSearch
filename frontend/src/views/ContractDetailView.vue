<template>
  <div class="contract-detail-page">
    <button class="back-btn" @click="router.back()">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
        <polyline points="15 18 9 12 15 6" />
      </svg>
      返回
    </button>

    <div v-if="loading" class="loading-state">
      <span class="spinner"></span>
      <span>加载中...</span>
    </div>

    <p v-else-if="error" class="error-msg">{{ error }}</p>

    <template v-else-if="contract">
      <div class="detail-shell">
        <main class="detail-main">
          <section class="hero-panel">
            <div class="hero-content">
              <div class="title-row">
                <h1 class="page-title">{{ contract.contractName || '未命名合约' }}</h1>
                <span class="contract-category" v-if="contract.category">{{ contract.category }}</span>
              </div>
              <p class="detail-address">{{ contract.address }}</p>
              <div class="hero-summary">
                <p v-for="item in descriptionSummary" :key="item">{{ item }}</p>
              </div>
            </div>

            <div class="detail-actions">
              <button
                class="action-btn"
                :class="contract.isFavorited ? 'action-btn--primary' : 'action-btn--secondary'"
                @click="handleFavorite"
                :disabled="favoriteLoading"
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  width="16"
                  height="16"
                  :style="contract.isFavorited ? 'fill: currentColor' : ''"
                >
                  <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
                </svg>
                <span>{{ contract.isFavorited ? '已收藏' : '收藏' }}</span>
              </button>

              <a
                :href="`https://etherscan.io/address/${contract.address}`"
                target="_blank"
                rel="noopener noreferrer"
                class="action-btn action-btn--secondary"
              >
                在 Etherscan 查看
              </a>

              <button class="action-btn action-btn--primary" @click="aiDrawerOpen = true">
                AI 合约解读
              </button>
            </div>
          </section>

          <section class="section-card code-card">
            <div class="section-header">
              <div>
                <h2 class="section-title">合约源代码</h2>
                <p class="section-subtitle">合约完整实现与函数结构</p>
              </div>
              <div class="section-actions">
                <button class="action-btn action-btn--secondary" @click="openInContractLab">
                  在合约实验室中打开
                </button>
                <button class="action-btn action-btn--secondary action-btn--icon" @click="openCodeViewer('source')" aria-label="放大查看源码" title="放大查看">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M8 3H5a2 2 0 0 0-2 2v3" />
                    <path d="M16 3h3a2 2 0 0 1 2 2v3" />
                    <path d="M8 21H5a2 2 0 0 1-2-2v-3" />
                    <path d="M16 21h3a2 2 0 0 0 2-2v-3" />
                  </svg>
                </button>
                <button class="action-btn action-btn--secondary action-btn--icon" @click="copyText(contract.sourceCode, 'source')" :aria-label="copied.source ? '源码已复制' : '复制源码'" :title="copied.source ? '已复制' : '复制源码'">
                  <svg v-if="copied.source" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 6 9 17l-5-5" />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" />
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                  </svg>
                </button>
              </div>
            </div>
            <p v-if="labTransferMessage" class="lab-transfer-message">{{ labTransferMessage }}</p>
            <pre class="code-block"><code>{{ contract.sourceCode || '暂无源代码' }}</code></pre>
          </section>

          <section class="section-card code-card">
            <div class="section-header">
              <div>
                <h2 class="section-title">合约 ABI</h2>
                <p class="section-subtitle">接口结构与外部调用信息</p>
              </div>
              <div v-if="hasAbi" class="section-actions">
                <button class="action-btn action-btn--secondary action-btn--icon" @click="openCodeViewer('abi')" aria-label="放大查看 ABI" title="放大查看">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M8 3H5a2 2 0 0 0-2 2v3" />
                    <path d="M16 3h3a2 2 0 0 1 2 2v3" />
                    <path d="M8 21H5a2 2 0 0 1-2-2v-3" />
                    <path d="M16 21h3a2 2 0 0 0 2-2v-3" />
                  </svg>
                </button>
                <button class="action-btn action-btn--secondary action-btn--icon" @click="copyText(abiText, 'abi')" :aria-label="copied.abi ? 'ABI 已复制' : '复制 ABI'" :title="copied.abi ? '已复制' : '复制 ABI'">
                  <svg v-if="copied.abi" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 6 9 17l-5-5" />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" />
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                  </svg>
                </button>
              </div>
            </div>
            <pre v-if="hasAbi" class="code-block"><code>{{ abiText }}</code></pre>
            <div v-else class="abi-state-card" :class="`abi-state-card--${abiStatus}`">
              <div>
                <span class="abi-state-label">{{ abiStateLabel }}</span>
                <h3>{{ abiStateTitle }}</h3>
                <p>{{ abiStateDescription }}</p>
              </div>
              <p v-if="abiStateReason" class="abi-state-reason">{{ abiStateReason }}</p>
            </div>
          </section>

        </main>

        <div v-if="aiDrawerOpen" class="ai-backdrop" @click="aiDrawerOpen = false"></div>
        <aside class="ai-panel" :class="{ 'ai-panel--open': aiDrawerOpen }">
          <div class="ai-panel__head">
            <div>
              <p class="panel-eyebrow">SMART INSIGHT</p>
              <h2>AI 合约解读</h2>
            </div>
            <button class="drawer-close" @click="aiDrawerOpen = false" aria-label="关闭 AI 解读面板">
              ×
            </button>
          </div>
          <p class="ai-disclaimer">源码结构、权限关系与风险线索的智能整理。</p>

          <div class="ai-model-row">
            <AiModelSelect v-model="selectedModel" @update:model-value="persistSelectedModel" />
          </div>

          <button class="ai-run-btn" @click="handleAiExplain()" :disabled="aiLoading">
            <span v-if="aiLoading" class="spinner spinner--light"></span>
            {{ aiLoading ? '分析中...' : '生成分析' }}
          </button>

          <div class="ai-tags" v-if="aiMode === 'quick' && aiSummary && !aiLoading">
            <span v-for="tag in aiInsightTags" :key="tag">{{ tag }}</span>
          </div>

          <p v-if="aiError" class="error-msg">{{ aiError }}</p>

          <template v-else-if="showDeepOverview">
            <section class="deep-overview-card">
              <div class="deep-overview-card__head">
                <span>分析总览</span>
                <strong>{{ aiOverview.overallRiskLevel }}风险</strong>
              </div>
              <div class="deep-overview-grid">
                <div>
                  <span>合约类型</span>
                  <strong>{{ aiOverview.contractType }}</strong>
                </div>
                <div>
                  <span>复杂度</span>
                  <strong>{{ aiOverview.complexityLevel }}</strong>
                </div>
              </div>
              <p>{{ aiOverview.corePurpose }}</p>
            </section>

            <section class="risk-score-card">
              <div class="risk-score-card__head">
                <h3>风险维度</h3>
                <span>0-100</span>
              </div>
              <div class="risk-row" v-for="item in riskRows" :key="item.label">
                <div class="risk-row__meta">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                </div>
                <div class="risk-track">
                  <span :style="{ width: `${item.value}%` }"></span>
                </div>
              </div>
            </section>
          </template>

          <div v-if="!aiError && !aiLoading && aiSections.length > 0" class="ai-section-list">
            <article v-for="section in aiSections" :key="section.title" class="ai-result-card">
              <h3>{{ section.title }}</h3>
              <p>{{ section.content }}</p>
            </article>
            <p v-if="aiModelUsed" class="ai-generated-by">由 {{ aiModelUsed }} 生成</p>
          </div>

          <div v-else-if="!showDeepOverview && !aiError && !aiLoading" class="ai-empty">
            <p class="ai-empty-title">智能分析面板</p>
            <p class="ai-empty-text">选择模型后生成合约结构、权限关系与风险线索分析。</p>
          </div>
        </aside>
      </div>

      <div v-if="codeViewer.open" class="code-viewer-backdrop" @click="closeCodeViewer">
        <section class="code-viewer" @click.stop>
          <div class="code-viewer__header">
            <div>
              <p class="panel-eyebrow">FULLSCREEN READER</p>
              <h2>{{ codeViewer.title }}</h2>
              <p>{{ codeViewer.subtitle }}</p>
            </div>
            <div class="code-viewer__actions">
              <button class="action-btn action-btn--secondary action-btn--icon" @click="copyText(codeViewer.content, codeViewer.copyKey)" :aria-label="copied[codeViewer.copyKey] ? '内容已复制' : codeViewer.copyLabel" :title="copied[codeViewer.copyKey] ? '已复制' : codeViewer.copyLabel">
                <svg v-if="copied[codeViewer.copyKey]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 6 9 17l-5-5" />
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="9" y="9" width="13" height="13" rx="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
              </button>
              <button class="drawer-close" @click="closeCodeViewer" :aria-label="`关闭${codeViewer.title}`">
                ×
              </button>
            </div>
          </div>
          <pre class="code-viewer__body"><code>{{ codeViewer.content }}</code></pre>
        </section>
      </div>

      <div v-if="favoriteModalOpen" class="favorite-modal-backdrop" @click="closeFavoriteModal">
        <section class="favorite-modal" @click.stop>
          <div class="favorite-modal__head">
            <div>
              <p class="panel-eyebrow">SAVE TO FOLDER</p>
              <h2>添加到收藏夹</h2>
            </div>
            <button class="drawer-close" @click="closeFavoriteModal" aria-label="关闭收藏夹选择">
              ×
            </button>
          </div>

          <div v-if="favoriteFolderLoading" class="loading-state">
            <span class="spinner"></span>
            <span>加载收藏夹...</span>
          </div>

          <template v-else>
            <label class="favorite-field favorite-field--inline">
              <span>名称</span>
              <input :value="contract.contractName || '未命名合约'" type="text" readonly />
            </label>

            <div class="favorite-field favorite-field--inline favorite-folder-field">
              <span>文件夹</span>
              <button class="folder-select-btn" @click="folderPickerOpen = !folderPickerOpen">
                <strong>{{ selectedFavoriteFolder?.name || '默认文件夹' }}</strong>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </button>
            </div>

            <div v-if="folderPickerOpen" class="folder-picker-panel">
              <div class="folder-picker-list">
                <button
                  v-for="folder in favoriteFolders"
                  :key="folder.id"
                  class="folder-picker-item"
                  :class="{ active: selectedFavoriteFolderId === folder.id }"
                  @click="selectFavoriteFolder(folder.id)"
                >
                  <span class="folder-picker-check">
                    <svg v-if="selectedFavoriteFolderId === folder.id" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 6 9 17l-5-5" />
                    </svg>
                  </span>
                  <svg class="folder-picker-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M3 7h6l2 3h10v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />
                  </svg>
                  <span class="folder-picker-name">{{ folder.name }}</span>
                  <em>{{ folder.contractCount || 0 }}</em>
                </button>

                <div v-if="creatingFolderInline" class="folder-create-inline">
                  <svg class="folder-picker-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M3 7h6l2 3h10v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />
                  </svg>
                  <input
                    ref="inlineFolderInput"
                    v-model="favoriteNewFolderName"
                    type="text"
                    placeholder="新建文件夹"
                    @keydown.enter.prevent="createFolderFromModal"
                    @keydown.esc.prevent="cancelInlineFolderCreate"
                  />
                  <button class="mini-text-btn" :disabled="favoriteCreatingFolder" @click="createFolderFromModal">
                    {{ favoriteCreatingFolder ? '创建中' : '创建' }}
                  </button>
                </div>
              </div>

              <div class="folder-picker-foot">
                <button class="new-folder-btn" @click="startInlineFolderCreate">
                  新文件夹
                </button>
              </div>
            </div>

            <p v-if="favoriteError" class="error-msg">{{ favoriteError }}</p>

            <div class="favorite-modal__actions">
              <button class="action-btn action-btn--secondary" @click="closeFavoriteModal">取消</button>
              <button class="action-btn action-btn--primary" :disabled="favoriteLoading" @click="confirmFavorite">
                <span v-if="favoriteLoading" class="spinner spinner--light"></span>
                {{ favoriteLoading ? '收藏中...' : '确认收藏' }}
              </button>
            </div>
          </template>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { contractApi } from '@/api'
import AiModelSelect from '@/components/AiModelSelect.vue'
import { getStoredAiModel, persistAiModel } from '@/config/aiModels'

const DETAIL_TO_LAB_KEY = 'contractDetailToLabSource'
const router = useRouter()
const route = useRoute()
const id = route.params.id

const contract = ref(null)
const loading = ref(true)
const error = ref('')
const favoriteLoading = ref(false)
const favoriteModalOpen = ref(false)
const favoriteFolderLoading = ref(false)
const favoriteError = ref('')
const favoriteFolders = ref([])
const selectedFavoriteFolderId = ref(null)
const favoriteNewFolderName = ref('')
const folderPickerOpen = ref(false)
const creatingFolderInline = ref(false)
const favoriteCreatingFolder = ref(false)
const inlineFolderInput = ref(null)
const copied = ref({ source: false, abi: false })
const aiDrawerOpen = ref(false)
const codeViewer = ref({
  open: false,
  title: '',
  subtitle: '',
  content: '',
  copyKey: 'source',
  copyLabel: '复制内容',
})

const aiLoading = ref(false)
const aiError = ref('')
const aiMode = ref('deep')
const selectedModel = ref(getStoredAiModel())
const aiModelUsed = ref('')
const aiResults = ref({
  quick: { summary: '', overview: null },
  deep: { summary: '', overview: null },
})
const labTransferMessage = ref('')

const abiText = computed(() => {
  if (!contract.value?.abi) return ''
  try {
    const parsed = typeof contract.value.abi === 'string'
      ? JSON.parse(contract.value.abi)
      : contract.value.abi
    return JSON.stringify(parsed, null, 2)
  } catch {
    return contract.value.abi
  }
})

const hasAbi = computed(() => Boolean(String(contract.value?.abi || '').trim()))

const abiStatus = computed(() => {
  if (hasAbi.value) return 'generated'
  return contract.value?.abiStatus || 'unavailable'
})

const abiStateLabel = computed(() => {
  if (abiStatus.value === 'failed') return 'GENERATE FAILED'
  return 'ABI UNAVAILABLE'
})

const abiStateTitle = computed(() => {
  if (abiStatus.value === 'failed') return '未能自动生成 ABI'
  return 'ABI 暂不可用'
})

const abiStateDescription = computed(() => {
  if (abiStatus.value === 'failed') {
    return '该源码已成功入库，但后端编译阶段没有得到可用 ABI。'
  }
  return '该合约暂无 ABI 数据。个人上传合约会在入库时自动尝试编译生成。'
})

const abiStateReason = computed(() => contract.value?.abiErrorMessage || '')

const descriptionSummary = computed(() => buildDescriptionSummary(contract.value?.description))

const selectedFavoriteFolder = computed(() => {
  return favoriteFolders.value.find(folder => folder.id === selectedFavoriteFolderId.value)
    || favoriteFolders.value.find(folder => folder.isDefault)
    || favoriteFolders.value[0]
    || null
})

const currentAiResult = computed(() => aiResults.value[aiMode.value] || { summary: '', overview: null })

const aiSummary = computed(() => currentAiResult.value.summary || '')

const aiOverview = computed(() => currentAiResult.value.overview)

const aiSections = computed(() => parseAiSections(aiSummary.value, aiMode.value))

const showDeepOverview = computed(() => {
  return aiMode.value === 'deep' && !aiLoading.value && aiOverview.value
})

const riskRows = computed(() => {
  const scores = aiOverview.value?.riskScores || {}
  return [
    { label: '权限风险', value: normalizeRiskScore(scores.permissionRisk) },
    { label: '资产流转风险', value: normalizeRiskScore(scores.assetFlowRisk) },
    { label: '复杂度风险', value: normalizeRiskScore(scores.complexityRisk) },
  ]
})

const aiInsightTags = computed(() => {
  const text = `${aiSummary.value} ${contract.value?.sourceCode || ''}`.toLowerCase()
  const tags = []
  if (text.includes('ownable') || text.includes('owner') || text.includes('权限')) tags.push('权限结构')
  if (text.includes('transfer') || text.includes('asset') || text.includes('资产')) tags.push('资产流转')
  if (text.includes('risk') || text.includes('风险')) tags.push('风险提示')
  if (text.includes('pause') || text.includes('pausable')) tags.push('可暂停机制')
  if (text.includes('whitelist') || text.includes('白名单')) tags.push('访问控制')
  if (tags.length === 0) tags.push('结构化分析')
  return tags.slice(0, 5)
})

onMounted(async () => {
  await fetchDetail()
})

async function fetchDetail() {
  loading.value = true
  error.value = ''
  try {
    const res = await contractApi.getContractDetail(id)
    if (!res.status) {
      throw new Error(res.message || '加载合约详情失败')
    }
    contract.value = res.data
  } catch (err) {
    console.warn('加载合约详情失败', err)
    error.value = '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function handleFavorite() {
  if (!contract.value) return

  const token = localStorage.getItem('token')
  if (!token) {
    labTransferMessage.value = '请先登录后收藏。'
    return
  }

  favoriteLoading.value = true
  try {
    if (contract.value.isFavorited) {
      const res = await contractApi.unfavoriteContract(id)
      if (!res.status) throw new Error(res.message || '取消收藏失败')
      contract.value.isFavorited = false
    } else {
      await openFavoriteModal()
    }
  } catch (err) {
    console.warn('收藏操作失败', err)
    labTransferMessage.value = '收藏失败，请重试。'
  } finally {
    favoriteLoading.value = false
  }
}

async function openFavoriteModal() {
  favoriteModalOpen.value = true
  favoriteError.value = ''
  favoriteNewFolderName.value = ''
  selectedFavoriteFolderId.value = null
  folderPickerOpen.value = false
  creatingFolderInline.value = false
  favoriteFolderLoading.value = true
  try {
    const res = await contractApi.getFavoriteFolders()
    if (!res.status) {
      throw new Error(res.message || '加载收藏夹失败')
    }
    favoriteFolders.value = res.data || []
    const defaultFolder = favoriteFolders.value.find(folder => folder.isDefault)
    selectedFavoriteFolderId.value = defaultFolder?.id || null
  } catch (err) {
    console.warn('加载收藏夹失败', err)
    favoriteError.value = '加载失败，请稍后重试'
  } finally {
    favoriteFolderLoading.value = false
  }
}

function closeFavoriteModal() {
  if (favoriteLoading.value) return
  favoriteModalOpen.value = false
  folderPickerOpen.value = false
  creatingFolderInline.value = false
}

function selectFavoriteFolder(folderId) {
  selectedFavoriteFolderId.value = folderId
}

async function startInlineFolderCreate() {
  creatingFolderInline.value = true
  folderPickerOpen.value = true
  favoriteNewFolderName.value = ''
  favoriteError.value = ''
  await nextTick()
  inlineFolderInput.value?.focus()
}

function cancelInlineFolderCreate() {
  creatingFolderInline.value = false
  favoriteNewFolderName.value = ''
}

async function createFolderFromModal() {
  const name = favoriteNewFolderName.value.trim()
  if (!name) return
  favoriteCreatingFolder.value = true
  favoriteError.value = ''
  try {
    const res = await contractApi.createFavoriteFolder({ name })
    if (!res.status) {
      throw new Error(res.message || '创建收藏夹失败')
    }
    favoriteFolders.value = [
      ...favoriteFolders.value.filter(folder => folder.id !== res.data?.id),
      res.data,
    ]
    selectedFavoriteFolderId.value = res.data?.id || selectedFavoriteFolderId.value
    favoriteNewFolderName.value = ''
    creatingFolderInline.value = false
  } catch (err) {
    console.warn('创建收藏夹失败', err)
    favoriteError.value = '创建失败，请重试'
  } finally {
    favoriteCreatingFolder.value = false
  }
}

async function confirmFavorite() {
  if (!contract.value) return
  favoriteLoading.value = true
  favoriteError.value = ''
  try {
    if (creatingFolderInline.value && favoriteNewFolderName.value.trim()) {
      await createFolderFromModal()
      if (favoriteError.value) return
    }
    const payload = selectedFavoriteFolderId.value
      ? { folderId: selectedFavoriteFolderId.value }
      : {}
    const res = await contractApi.favoriteContract(id, payload)
    if (!res.status) {
      throw new Error(res.message || '收藏失败')
    }
    contract.value.isFavorited = true
    favoriteModalOpen.value = false
  } catch (err) {
    console.warn('收藏失败', err)
    favoriteError.value = '收藏失败，请重试'
  } finally {
    favoriteLoading.value = false
  }
}

function setAiMode(mode) {
  aiMode.value = mode
  aiError.value = ''
}

function persistSelectedModel() {
  selectedModel.value = persistAiModel(selectedModel.value)
}

async function handleAiExplain(mode = 'deep') {
  aiLoading.value = true
  aiError.value = ''
  aiMode.value = mode
  persistSelectedModel()

  try {
    const res = await contractApi.getAiExplain(id, selectedModel.value)
    if (!res.status) {
      throw new Error(res.message || 'AI 解读生成失败')
    }
    aiModelUsed.value = res.data?.modelUsed || selectedModel.value
    console.info(`[explain] 请求模型：${aiModelUsed.value}`)
    aiResults.value = {
      ...aiResults.value,
      [mode]: {
        summary: res.data?.summary || '暂时无法生成解释',
        overview: mode === 'deep' ? normalizeAiOverview(res.data) : null,
      },
    }
  } catch (err) {
    console.warn('AI 解读生成失败', err)
    aiError.value = '生成失败，请重试'
  } finally {
    aiLoading.value = false
  }
}

async function copyText(text, key) {
  try {
    await navigator.clipboard.writeText(text || '')
    copied.value[key] = true
    setTimeout(() => {
      copied.value[key] = false
    }, 1800)
  } catch {
    // ignore
  }
}

function buildLabFileName(name) {
  const base = String(name || '').trim().replace(/[^\w\u4e00-\u9fa5-]/g, '')
  return `${base || 'Contract'}.sol`
}

function openInContractLab() {
  const sourceCode = contract.value?.sourceCode || ''
  if (!sourceCode.trim()) {
    labTransferMessage.value = '当前合约暂无源码，无法打开到合约实验室'
    window.setTimeout(() => {
      labTransferMessage.value = ''
    }, 2200)
    return
  }

  const payload = {
    source: 'contract-detail',
    contractName: contract.value?.contractName || '',
    fileName: buildLabFileName(contract.value?.contractName),
    contractAddress: contract.value?.address || '',
    sourceCode,
    abi: contract.value?.abi || '',
    createdAt: Date.now()
  }

  try {
    sessionStorage.setItem(DETAIL_TO_LAB_KEY, JSON.stringify(payload))
    router.push('/contract-lab')
  } catch (err) {
    labTransferMessage.value = '无法暂存源码，请检查浏览器存储权限'
    window.setTimeout(() => {
      labTransferMessage.value = ''
    }, 2200)
  }
}

function openCodeViewer(type) {
  if (type === 'abi') {
    if (!hasAbi.value) return
    codeViewer.value = {
      open: true,
      title: '合约 ABI',
      subtitle: '接口结构与外部调用信息',
      content: abiText.value,
      copyKey: 'abi',
      copyLabel: '复制 ABI',
    }
    return
  }

  codeViewer.value = {
    open: true,
    title: '合约源代码',
    subtitle: '合约完整实现与函数结构',
    content: contract.value?.sourceCode || '暂无源代码',
    copyKey: 'source',
    copyLabel: '复制源码',
  }
}

function closeCodeViewer() {
  codeViewer.value = {
    ...codeViewer.value,
    open: false,
  }
}

function parseAiSections(text, mode) {
  const raw = String(text || '').trim()
  if (!raw) return []

  const titles = mode === 'deep'
    ? ['合约定位', '功能模块', '权限结构', '资产与调用逻辑', '风险提示', '应用场景与阅读建议']
    : ['一句话总结', '主要用途', '核心机制', '适合关注的点']

  const sections = []
  titles.forEach((title, index) => {
    const start = raw.indexOf(title)
    if (start < 0) return
    const nextStarts = titles
      .slice(index + 1)
      .map(nextTitle => raw.indexOf(nextTitle))
      .filter(pos => pos > start)
    const end = nextStarts.length ? Math.min(...nextStarts) : raw.length
    const content = cleanAiSectionText(raw.slice(start + title.length, end), titles)
    if (content) sections.push({ title, content })
  })

  if (sections.length > 0) return sections
  return [{ title: '分析结果', content: cleanAiSectionText(raw, titles) }]
}

function cleanAiSectionText(value, titles = []) {
  let text = String(value || '')
    .replace(/\r/g, '')
    .replace(/^[:：\s\n]+/, '')
    .replace(/^[（(]?[一二三四五六七八九十][）)]?[、.．:：\s\n]+/g, '')
    .replace(/^\d+[.．、:：\s\n]+/g, '')
    .trim()

  titles.forEach((title) => {
    const escaped = title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    text = text
      .replace(new RegExp(`^${escaped}[：:\\s\\n]*`, 'g'), '')
      .replace(new RegExp(`[\\s\\n]*[（(]?[一二三四五六七八九十][）)]?[、.．]?\\s*$`, 'g'), '')
      .replace(new RegExp(`[\\s\\n]*\\d+[.．、]?\\s*$`, 'g'), '')
  })

  return text
    .replace(/(?:^|\n)\s*[（(]?[一二三四五六七八九十][）)]?[、.．]\s*(?=\n|$)/g, '')
    .replace(/(?:^|\n)\s*\d+[.．、]\s*(?=\n|$)/g, '')
    .replace(/[ \t]+\n/g, '\n')
    .trim()
}

function normalizeAiOverview(data = {}) {
  const riskScores = data.riskScores || {}
  return {
    contractType: cleanOverviewText(data.contractType) || 'Smart Contract',
    complexityLevel: cleanOverviewText(data.complexityLevel) || '中',
    overallRiskLevel: cleanOverviewText(data.overallRiskLevel) || '中',
    corePurpose: cleanOverviewText(data.corePurpose) || '核心用途、结构复杂度与主要风险分布。',
    riskScores: {
      permissionRisk: normalizeRiskScore(riskScores.permissionRisk),
      assetFlowRisk: normalizeRiskScore(riskScores.assetFlowRisk),
      complexityRisk: normalizeRiskScore(riskScores.complexityRisk),
    },
  }
}

function cleanOverviewText(value) {
  return String(value || '')
    .replace(/\r/g, '')
    .replace(/\n+/g, ' ')
    .replace(/\s+/g, ' ')
    .replace(/^[：:\s]+/, '')
    .replace(/^[（(]?[一二三四五六七八九十][）)]?[、.．:：\s]+/g, '')
    .replace(/^\d+[.．、:：\s]+/g, '')
    .trim()
}

function normalizeRiskScore(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return 0
  return Math.max(0, Math.min(100, Math.round(number)))
}

function buildDescriptionSummary(description) {
  const text = String(description || '').trim()
  if (!text) return ['暂无描述，可结合源码与 ABI 继续阅读。']

  const normalized = text
    .replace(/\r/g, '')
    .replace(/\n+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()

  const numbered = normalized
    .split(/\s*(?:\d+[.．、]|[（(]?[一二三四五六七八九十][）)]?[、.．])\s+/)
    .map(item => item.trim())
    .filter(Boolean)

  const chunks = numbered.length > 1
    ? numbered
    : normalized
        .split(/[。；;]+/)
        .map(item => item.trim())
        .filter(Boolean)

  const result = chunks.slice(0, 4).map(item => item.length > 150 ? `${item.slice(0, 150)}...` : item)
  return result.length > 0 ? result : [normalized]
}
</script>

<style scoped>
.contract-detail-page {
  max-width: 980px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 14px;
  margin-bottom: 22px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #ffffff;
  color: #6b7280;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.back-btn:hover {
  background: #f8f9fb;
  border-color: #d9dee5;
  color: #111827;
  transform: translateY(-1px);
}

.loading-state {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #6b7280;
  padding: 8px 2px;
}

.error-msg {
  color: #ef4444;
  font-size: 14px;
  line-height: 1.7;
}

.detail-shell {
  display: block;
}

.detail-main {
  min-width: 0;
}

.hero-panel,
.section-card,
.ai-panel {
  position: relative;
  background: linear-gradient(180deg, #ffffff 0%, #fcfcfd 100%);
  border: 1px solid #e7eaf0;
  box-shadow:
    0 10px 24px rgba(15, 23, 42, 0.04),
    inset 0 1px 0 rgba(255,255,255,0.9);
  overflow: hidden;
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  border-radius: 26px;
  padding: 22px;
  margin-bottom: 18px;
}

.hero-content {
  min-width: 0;
  flex: 1;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 34px;
  line-height: 1.05;
  font-weight: 700;
  letter-spacing: -0.045em;
  color: #111827;
}

.contract-category {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f5f7fa;
  color: #667085;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  border: 1px solid #edf0f4;
}

.detail-address {
  margin: 10px 0 0;
  font-size: 13px;
  color: #98a2b3;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  line-height: 1.7;
  word-break: break-all;
}

.hero-summary {
  margin: 12px 0 0;
  max-width: 760px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.hero-summary p {
  margin: 0;
  font-size: 14px;
  color: #667085;
  line-height: 1.8;
}

.detail-actions,
.section-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.lab-transfer-message {
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fbfbfc;
  color: #374151;
  font-size: 13px;
  font-weight: 700;
}

.section-card {
  border-radius: 22px;
  padding: 20px;
  margin-bottom: 18px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #111827;
}

.section-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #6b7280;
}

.code-block {
  background: #f8fafc;
  border: 1px solid #e7eaf0;
  border-radius: 18px;
  padding: 16px;
  overflow: auto;
  max-height: 360px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 12.5px;
  line-height: 1.72;
  color: #475467;
  white-space: pre;
}

.abi-state-card {
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(248, 250, 252, 0.96), rgba(255, 255, 255, 0.98));
  padding: 18px;
  color: #111827;
}

.abi-state-card--failed {
  background:
    linear-gradient(135deg, rgba(250, 250, 250, 0.98), rgba(255, 255, 255, 0.98));
}

.abi-state-label {
  display: inline-flex;
  margin-bottom: 8px;
  color: #98a2b3;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.abi-state-card h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  letter-spacing: -0.02em;
}

.abi-state-card p {
  margin: 8px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
}

.abi-state-reason {
  margin-top: 14px !important;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f3f4f6;
  color: #374151 !important;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  word-break: break-word;
}

.ai-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 220;
  width: min(460px, calc(100vw - 28px));
  padding: 22px;
  border-radius: 24px 0 0 24px;
  padding: 18px;
  border-color: #dbe6f5;
  background:
    radial-gradient(circle at top right, rgba(232, 240, 255, 0.72), transparent 34%),
    linear-gradient(180deg, #ffffff 0%, #fbfcfe 100%);
  box-shadow:
    -24px 0 60px rgba(15, 23, 42, 0.18),
    inset 1px 0 0 rgba(255,255,255,0.75);
  transform: translateX(104%) scale(0.985);
  opacity: 0;
  transition:
    transform 0.32s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.22s ease;
  overflow-y: auto;
}

.ai-panel--open {
  transform: translateX(0) scale(1);
  opacity: 1;
}

.ai-backdrop {
  position: fixed;
  inset: 0;
  z-index: 210;
  background: rgba(15, 23, 42, 0.24);
  backdrop-filter: blur(5px);
  animation: backdropFade 0.22s ease both;
}

.code-viewer-backdrop {
  position: fixed;
  inset: 0;
  z-index: 260;
  padding: 24px;
  background: rgba(15, 23, 42, 0.46);
  backdrop-filter: blur(7px);
  animation: backdropFade 0.2s ease both;
}

.favorite-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 270;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.30);
  backdrop-filter: blur(6px);
  animation: backdropFade 0.2s ease both;
}

.favorite-modal {
  width: min(760px, 100%);
  max-height: calc(100vh - 64px);
  border: 1px solid rgba(226, 232, 240, 0.96);
  border-radius: 26px;
  background: #ffffff;
  padding: 24px;
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.22);
  animation: codeViewerIn 0.22s cubic-bezier(0.22, 1, 0.36, 1) both;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.favorite-modal__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.favorite-modal__head h2 {
  margin: 0;
  color: #111827;
  font-size: 24px;
  line-height: 1.15;
  letter-spacing: -0.04em;
}

.favorite-field {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.favorite-field span {
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

.favorite-field input {
  width: 100%;
  height: 44px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 0 13px;
  background: #fcfcfd;
  color: #111827;
  outline: none;
  font-size: 14px;
  box-sizing: border-box;
}

.favorite-field input:focus {
  border-color: #cfd6de;
  box-shadow: 0 0 0 4px rgba(17, 24, 39, 0.04);
}

.favorite-field input[readonly] {
  color: #111827;
  background: #ffffff;
}

.favorite-folder-field {
  position: relative;
}

.folder-select-btn {
  width: 100%;
  height: 44px;
  padding: 0 13px;
  border: 1px solid #d1d5db;
  border-radius: 14px;
  background: #f8f8f8;
  color: #111827;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  font-size: 14px;
}

.folder-select-btn strong {
  font-weight: 600;
}

.folder-select-btn svg {
  width: 16px;
  height: 16px;
  color: #4b5563;
}

.folder-picker-panel {
  margin: 2px 0 16px 86px;
  border: 1px solid #d1d5db;
  border-radius: 16px;
  background: #fff;
  overflow: hidden;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.12);
  display: flex;
  flex-direction: column;
  max-height: min(420px, 44vh);
}

.folder-picker-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 0;
}

.folder-picker-item,
.folder-create-inline {
  width: 100%;
  min-height: 42px;
  padding: 0 14px;
  border: none;
  background: transparent;
  color: #111827;
  display: grid;
  align-items: center;
  gap: 10px;
  text-align: left;
}

.folder-picker-item {
  grid-template-columns: 24px 24px minmax(0, 1fr) auto;
}

.folder-create-inline {
  grid-template-columns: 24px minmax(0, 1fr) auto;
  padding-top: 8px;
  padding-bottom: 8px;
}

.folder-picker-item {
  cursor: pointer;
}

.folder-picker-item:hover {
  background: #f5f5f5;
}

.folder-picker-item.active {
  background: #eceff3;
}

.folder-picker-check,
.folder-picker-icon {
  width: 20px;
  height: 20px;
  color: #4b5563;
}

.folder-picker-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.folder-picker-check svg,
.folder-picker-icon {
  width: 20px;
  height: 20px;
}

.folder-picker-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
}

.folder-picker-item em {
  font-style: normal;
  color: #9ca3af;
  font-size: 12px;
  font-weight: 700;
}

.folder-create-inline {
  background: #eef2f7;
}

.folder-create-inline input {
  width: 100%;
  height: 34px;
  border: 2px solid #111827;
  border-radius: 10px;
  padding: 0 10px;
  background: #ffffff;
  color: #111827;
  font-size: 14px;
  font-weight: 600;
  outline: none;
}

.mini-text-btn {
  height: 30px;
  padding: 0 10px;
  border: none;
  border-radius: 999px;
  background: #111827;
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.mini-text-btn:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

.folder-picker-foot {
  flex: 0 0 auto;
  display: flex;
  justify-content: flex-start;
  padding: 14px;
  border-top: 1px solid #e5e7eb;
  background: #fcfcfd;
}

.new-folder-btn {
  height: 40px;
  padding: 0 16px;
  border: 1px solid #111827;
  border-radius: 999px;
  background: #111827;
  color: #ffffff;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.new-folder-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 20px rgba(17, 24, 39, 0.12);
}

.favorite-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.code-viewer {
  height: 100%;
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 24px;
  background:
    radial-gradient(circle at top right, rgba(232, 240, 255, 0.72), transparent 30%),
    linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.26);
  overflow: hidden;
  animation: codeViewerIn 0.24s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.code-viewer__header {
  flex: 0 0 auto;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 22px;
  border-bottom: 1px solid #e5eaf1;
}

.code-viewer__header h2 {
  margin: 0;
  color: #111827;
  font-size: 20px;
  letter-spacing: -0.03em;
}

.code-viewer__header p:not(.panel-eyebrow) {
  margin: 7px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.code-viewer__actions {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-shrink: 0;
}

.code-viewer__body {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 22px;
  overflow: auto;
  border: none;
  border-radius: 0;
  background: #0f172a;
  color: #dbeafe;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.72;
  white-space: pre;
}

.ai-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.drawer-close {
  width: 32px;
  height: 32px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #ffffff;
  color: #6b7280;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.drawer-close:hover {
  background: #f8f9fb;
  color: #111827;
}

.panel-eyebrow {
  margin: 0 0 6px;
  color: #8a94a3;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.ai-panel h2 {
  margin: 0;
  color: #111827;
  font-size: 20px;
  letter-spacing: -0.03em;
}

.ai-disclaimer {
  margin: 0 0 14px;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.7;
}

.ai-mode-switch {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  margin-bottom: 12px;
}

.ai-model-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.ai-generated-by {
  margin: 4px 0 0;
  color: #9ca3af;
  font-size: 11px;
}

.mode-btn {
  text-align: left;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #ffffff;
  padding: 12px;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.mode-btn strong,
.mode-btn span {
  display: block;
}

.mode-btn strong {
  color: #111827;
  font-size: 13px;
}

.mode-btn span {
  margin-top: 4px;
  color: #8a94a3;
  font-size: 12px;
}

.mode-btn.active {
  border-color: #cbd8ec;
  background: #f8fbff;
  box-shadow:
    0 8px 18px rgba(15, 23, 42, 0.04),
    inset 0 1px 0 rgba(255,255,255,0.9);
}

.mode-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.ai-run-btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(180deg, #111827 0%, #0f172a 100%);
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 14px;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.12);
}

.ai-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-bottom: 14px;
}

.ai-tags span {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  background: #f5f7fa;
  color: #667085;
  border: 1px solid #edf0f4;
  font-size: 11px;
  font-weight: 700;
}

.deep-overview-card,
.risk-score-card {
  border: 1px solid #e4eaf3;
  background: rgba(255,255,255,0.86);
  border-radius: 18px;
  padding: 14px;
  margin-bottom: 10px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.85);
}

.deep-overview-card__head,
.risk-score-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.deep-overview-card__head span,
.risk-score-card__head h3 {
  margin: 0;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

.deep-overview-card__head strong,
.risk-score-card__head span {
  color: #667085;
  font-size: 12px;
  font-weight: 700;
}

.deep-overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.deep-overview-grid div {
  min-width: 0;
  border: 1px solid #edf0f4;
  border-radius: 14px;
  background: #f8fafc;
  padding: 10px;
}

.deep-overview-grid span {
  display: block;
  margin-bottom: 5px;
  color: #98a2b3;
  font-size: 11px;
  font-weight: 700;
}

.deep-overview-grid strong {
  display: block;
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.45;
  word-break: break-word;
}

.deep-overview-card p {
  margin: 0;
  color: #475467;
  font-size: 13px;
  line-height: 1.78;
}

.risk-row + .risk-row {
  margin-top: 12px;
}

.risk-row__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 7px;
  color: #475467;
  font-size: 12px;
  font-weight: 700;
}

.risk-row__meta strong {
  color: #111827;
  font-size: 12px;
}

.risk-track {
  height: 7px;
  border-radius: 999px;
  background: #edf1f6;
  overflow: hidden;
}

.risk-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #98a2b3 0%, #475467 100%);
  transition: width 0.38s cubic-bezier(0.22, 1, 0.36, 1);
}

.ai-section-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-result-card,
.ai-empty {
  border: 1px solid #e4eaf3;
  background: rgba(255,255,255,0.82);
  border-radius: 18px;
  padding: 14px;
}

.ai-result-card h3 {
  margin: 0 0 8px;
  color: #111827;
  font-size: 14px;
}

.ai-result-card p {
  margin: 0;
  color: #475467;
  font-size: 13px;
  line-height: 1.85;
  white-space: pre-wrap;
}

.ai-empty-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.ai-empty-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: #6b7280;
}

.action-btn {
  height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
  text-decoration: none;
}

.action-btn--primary {
  border: none;
  background: linear-gradient(180deg, #111827 0%, #0f172a 100%);
  color: #ffffff;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.10);
}

.action-btn--primary:hover:not(:disabled) {
  opacity: 0.97;
  transform: translateY(-1px);
}

.action-btn--secondary {
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #6b7280;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
}

.action-btn--secondary:hover {
  background: #f8f9fb;
  border-color: #d9dee5;
  color: #111827;
  transform: translateY(-1px);
}

.action-btn--sm {
  height: 34px;
  padding: 0 12px;
  font-size: 12px;
}

.action-btn--icon {
  width: 36px;
  height: 36px;
  padding: 0;
  border-radius: 12px;
}

.action-btn--icon svg {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
}

.action-btn:disabled,
.ai-run-btn:disabled,
.mode-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(17, 24, 39, 0.18);
  border-top-color: #111827;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.spinner--light {
  border-color: rgba(255,255,255,0.32);
  border-top-color: #ffffff;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes backdropFade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes codeViewerIn {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 720px) {
  .page-title {
    font-size: 28px;
  }

  .section-card,
  .hero-panel {
    padding: 18px;
    border-radius: 20px;
  }

  .detail-actions .action-btn,
  .section-actions .action-btn {
    flex: 1;
  }

  .code-block {
    border-radius: 16px;
  }

  .ai-panel {
    width: 100vw;
    border-radius: 0;
  }

  .code-viewer-backdrop {
    padding: 0;
  }

  .code-viewer {
    border-radius: 0;
    border-left: none;
    border-right: none;
  }

  .code-viewer__header {
    align-items: flex-start;
    flex-direction: column;
    padding: 16px;
  }

  .code-viewer__actions {
    width: 100%;
  }

  .code-viewer__actions .action-btn {
    flex: 1;
  }

  .code-viewer__body {
    padding: 16px;
    font-size: 12px;
  }

  .favorite-modal-backdrop {
    padding: 12px;
  }

  .favorite-modal {
    padding: 18px;
    border-radius: 22px;
  }

  .favorite-field {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .folder-picker-panel {
    margin-left: 0;
  }

  .favorite-modal__actions {
    justify-content: stretch;
  }

  .favorite-modal__actions .action-btn {
    flex: 1;
  }
}
</style>
