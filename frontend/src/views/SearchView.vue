<template>
  <div class="search-page">
    <div class="page-header">
      <p class="page-eyebrow">CONTRACT SEARCH</p>
      <h1 class="page-title">搜索合约</h1>
      <p class="page-subtitle">按功能、接口或源码片段定位可复用合约。</p>
    </div>

    <section class="search-workbench">
      <div class="primary-search">
        <input
          v-model="keyword"
          class="search-input"
          type="text"
          :placeholder="searchPlaceholder"
          @keyup.enter="handleSearch"
        />
        <button class="search-btn" @click="handleSearch" :disabled="loading">
          <span v-if="loading" class="btn-spinner"></span>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" />
          </svg>
          <span>{{ loading ? '搜索中...' : '搜索' }}</span>
        </button>
      </div>

      <div v-if="showAssistArea" class="assist-area">
        <div class="assist-block recent-block">
          <div class="assist-head">
            <span class="assist-label">最近搜索</span>
            <div class="history-actions">
              <button v-if="searchHistory.length" class="text-action" @click="clearSearchHistory">清空</button>
            </div>
          </div>
          <div v-if="visibleSearchHistory.length" class="keyword-list">
            <button
              v-for="item in visibleSearchHistory"
              :key="item"
              class="keyword-chip keyword-chip--recent"
              @click="handleHistoryKeywordClick(item)"
            >
              {{ item }}
            </button>
          </div>
          <p v-else class="assist-empty">暂无最近搜索</p>
        </div>

        <div class="assist-block hot-block">
          <div class="assist-head">
            <span class="assist-label">{{ hotSearchLabel }}</span>
          </div>
          <div class="keyword-list">
            <button
              v-for="item in hotKeywords"
              :key="item"
              class="keyword-chip keyword-chip--hot"
              @click="handleHotKeywordClick(item)"
            >
              {{ item }}
            </button>
          </div>
        </div>
      </div>

      <div class="control-area">
        <div class="control-group">
          <span class="control-label">搜索模式</span>
          <div class="segmented-control">
            <label class="segment-item" :class="{ active: searchMode === 'semantic' }">
              <input type="radio" v-model="searchMode" value="semantic" />
              语义搜索
            </label>
            <label class="segment-item" :class="{ active: searchMode === 'code' }">
              <input type="radio" v-model="searchMode" value="code" />
              代码片段
            </label>
          </div>
        </div>
      </div>
    </section>

    <section v-if="validationMessage" class="initial-hint initial-hint--warning">
      {{ validationMessage }}
    </section>

    <section v-if="hasSearched && !validationMessage" class="results-area">
      <template v-if="loading">
        <div class="result-toolbar result-toolbar--loading">
          <div>
            <p class="toolbar-title">正在检索合约</p>
            <p class="toolbar-meta">搜索模式：{{ modeLabel }}</p>
          </div>
        </div>
        <div class="skeleton-list">
          <div v-for="item in 4" :key="item" class="skeleton-card">
            <span class="skeleton-line skeleton-line--title"></span>
            <span class="skeleton-line skeleton-line--address"></span>
            <span class="skeleton-line"></span>
            <span class="skeleton-line skeleton-line--short"></span>
          </div>
        </div>
      </template>

      <template v-else-if="error">
        <div class="state-panel state-panel--error">
          <h2>搜索失败，请稍后重试</h2>
          <p>{{ error }}</p>
          <button class="state-action" @click="handleSearch">重新搜索</button>
        </div>
      </template>

      <template v-else>
        <div class="result-toolbar">
          <div>
            <p class="toolbar-title">
              找到 <strong>{{ filteredResults.length }}</strong> 个合约
              <span v-if="localFilterKeyword && rawResults.length !== filteredResults.length">
                / 原始 {{ rawResults.length }} 个
              </span>
            </p>
            <p class="toolbar-meta">
              {{ modeLabel }}
            </p>
          </div>

          <div class="toolbar-controls">
            <select v-model="sortType" class="control-select">
              <option value="relevance">默认相关度</option>
              <option value="name">名称排序</option>
              <option value="category">标签/分类排序</option>
            </select>
            <input
              v-model="localFilterKeyword"
              class="filter-input filter-input--toolbar"
              type="text"
              placeholder="结果内过滤"
            />
          </div>
        </div>

        <div v-if="filteredResults.length === 0" class="state-panel state-panel--empty">
          <h2>{{ rawResults.length === 0 ? '没有找到相关合约' : '当前结果内没有匹配项' }}</h2>
          <p>{{ rawResults.length === 0 ? '请尝试其他关键词或切换搜索模式。' : '调整结果内过滤关键词，或回到默认相关度查看完整结果。' }}</p>
        </div>

        <ContractListWithPagination
          v-else
          :items="filteredResults"
          :page-size="pageSize"
          :highlight-keyword="lastSearchKeyword"
          :search-mode="searchMode"
          @card-click="handleCardClick"
          @page-change="handlePageChange"
        />
      </template>
    </section>
  </div>
</template>

<script setup>
import { features } from '@/config/features'
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { searchApi } from '@/api'
import ContractListWithPagination from '@/components/ContractListWithPagination.vue'

const router = useRouter()

const keyword = ref('')
const searchMode = ref(features.defaultSearchMode)
const loading = ref(false)
const error = ref('')
const hasSearched = ref(false)
const rawResults = ref([])
const sortType = ref('relevance')
const localFilterKeyword = ref('')
const lastSearchKeyword = ref('')
const validationMessage = ref('')
const pageSize = 10
const cache = ref({})

const semanticHotKeywords = ['NFT', 'Token', 'DeFi', 'Governance', 'Staking']
const codeHotKeywords = ['ERC20', 'ERC721', 'onlyOwner', 'transferFrom', 'event Approval']

const SEARCH_HISTORY_KEY = 'contract-search-history'
const searchHistory = ref([])
const historyPreviewCount = 5
const maxHistoryCount = 16

const visibleSearchHistory = computed(() => {
  return searchHistory.value.slice(0, historyPreviewCount)
})

const modeLabel = computed(() => searchMode.value === 'semantic' ? '语义搜索' : '代码片段')
const searchPlaceholder = computed(() => (
  searchMode.value === 'semantic'
    ? '输入合约名称、功能描述或关键词'
    : '输入函数名、接口、事件或 modifier'
))
const hotSearchLabel = computed(() => '推荐搜索')
const hotKeywords = computed(() => searchMode.value === 'semantic' ? semanticHotKeywords : codeHotKeywords)
const showAssistArea = computed(() => !hasSearched.value && !keyword.value.trim())

const filteredResults = computed(() => {
  const filter = localFilterKeyword.value.trim().toLowerCase()
  let next = rawResults.value.map((item, index) => ({ item, index }))

  if (filter) {
    next = next.filter(({ item }) => {
      return [getContractName(item), item.description, item.address, item.category]
        .some(value => String(value || '').toLowerCase().includes(filter))
    })
  }

  if (sortType.value === 'name') {
    next = [...next].sort((a, b) => getContractName(a.item).localeCompare(getContractName(b.item), 'zh-Hans-CN'))
  } else if (sortType.value === 'category') {
    next = [...next].sort((a, b) => {
      const byCategory = String(a.item.category || '').localeCompare(String(b.item.category || ''), 'zh-Hans-CN')
      return byCategory || a.index - b.index
    })
  }

  return next.map(({ item }) => item)
})

onMounted(() => {
  loadSearchHistory()
})

function getContractName(item) {
  return item?.contractName || item?.name || '未命名合约'
}

function loadSearchHistory() {
  try {
    const raw = localStorage.getItem(SEARCH_HISTORY_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    searchHistory.value = Array.isArray(parsed) ? parsed : []
  } catch {
    searchHistory.value = []
  }
}

function saveSearchHistoryToLocal() {
  localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(searchHistory.value))
}

function addSearchHistory(value) {
  const trimmed = value.trim()
  if (!trimmed) return

  const next = [trimmed, ...searchHistory.value.filter(item => item !== trimmed)]
  searchHistory.value = next.slice(0, maxHistoryCount)
  saveSearchHistoryToLocal()
}

function clearSearchHistory() {
  searchHistory.value = []
  localStorage.removeItem(SEARCH_HISTORY_KEY)
}

function isMeaningfulKeyword(value) {
  const trimmed = value.trim()
  if (trimmed.length < 2) return false
  if (!/[\u4e00-\u9fa5A-Za-z0-9]/.test(trimmed)) return false
  if (/^([A-Za-z0-9\u4e00-\u9fa5])\1{3,}$/i.test(trimmed)) return false

  const compact = trimmed.replace(/[^A-Za-z0-9\u4e00-\u9fa5]/g, '')
  if (/^[A-Za-z]$/.test(compact)) return false
  if (/^[A-Za-z]{8,}$/.test(compact)) {
    const vowelCount = (compact.match(/[aeiou]/gi) || []).length
    if (vowelCount / compact.length < 0.25) return false
  }
  return true
}

async function doSearch() {
  const trimmed = keyword.value.trim()
  loading.value = true
  error.value = ''
  localFilterKeyword.value = ''
  lastSearchKeyword.value = trimmed

  try {
    const cacheKey = `${trimmed}-${searchMode.value}`
    if (cache.value[cacheKey]) {
      rawResults.value = cache.value[cacheKey]
      return
    }

    const res = await searchApi.searchContracts({
      input: trimmed,
      searchMode: searchMode.value,
      limit: 100,
    })

    const data = Array.isArray(res.data) ? res.data : []
    cache.value[cacheKey] = data
    rawResults.value = data
  } catch (err) {
    console.warn('搜索失败', err)
    error.value = '搜索失败，请稍后重试'
    rawResults.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  const trimmed = keyword.value.trim()
  if (!trimmed || loading.value) return
  if (!isMeaningfulKeyword(trimmed)) {
    validationMessage.value = '关键词过短或缺少有效语义，请输入合约名称、功能描述或代码关键词'
    hasSearched.value = false
    rawResults.value = []
    localFilterKeyword.value = ''
    return
  }

  validationMessage.value = ''
  addSearchHistory(trimmed)
  hasSearched.value = true
  doSearch()
}

function handleHotKeywordClick(value) {
  keyword.value = value
  handleSearch()
}

function handleHistoryKeywordClick(value) {
  keyword.value = value
  handleSearch()
}

function handlePageChange() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleCardClick(item) {
  router.push(`/contract/${item.id}`)
}

</script>

<style scoped>
.search-page {
  max-width: 920px;
}

.page-header {
  margin-bottom: 24px;
}

.page-eyebrow {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #9ca3af;
  text-transform: uppercase;
}

.page-title {
  margin: 0;
  font-size: 36px;
  line-height: 1.02;
  font-weight: 700;
  letter-spacing: -0.045em;
  color: #111827;
}

.page-subtitle {
  margin: 12px 0 0;
  max-width: 620px;
  font-size: 15px;
  line-height: 1.8;
  color: #6b7280;
}

.search-workbench,
.skeleton-card {
  background: linear-gradient(180deg, #ffffff 0%, #fcfcfd 100%);
  border: 1px solid #e7eaf0;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
}

.search-workbench {
  border-radius: 26px;
  padding: 20px 20px 16px;
  margin-bottom: 12px;
  box-shadow:
    0 12px 28px rgba(15, 23, 42, 0.045),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.primary-search {
  display: flex;
  gap: 12px;
  align-items: center;
}

.search-input,
.filter-input,
.control-select {
  height: 36px;
  border-radius: 12px;
  border: 1px solid #dfe4ea;
  background: #ffffff;
  color: #111827;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.search-input {
  flex: 1;
  height: 56px;
  padding: 0 20px;
  border-radius: 20px;
  font-size: 16px;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.03);
}

.filter-input {
  width: 168px;
  padding: 0 12px;
}

.filter-input--toolbar {
  width: 170px;
}

.control-select {
  min-width: 126px;
  padding: 0 30px 0 11px;
}

.search-input::placeholder,
.filter-input::placeholder {
  color: #9ca3af;
}

.search-input:focus,
.filter-input:focus,
.control-select:focus {
  border-color: #cfd6de;
  box-shadow: 0 0 0 4px rgba(17, 24, 39, 0.04);
}

.search-btn {
  flex-shrink: 0;
  height: 56px;
  min-width: 112px;
  padding: 0 22px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(180deg, #111827 0%, #0f172a 100%);
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  transition: opacity 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
  box-shadow: 0 8px 16px rgba(15, 23, 42, 0.09);
}

.search-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.11);
}

.search-btn:disabled {
  opacity: 0.72;
  cursor: not-allowed;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.38);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.assist-area {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px;
  margin-top: 14px;
  padding: 0 2px;
}

.assist-block {
  min-width: 0;
}

.assist-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.assist-label,
.control-label {
  font-size: 11px;
  color: #a3adba;
  letter-spacing: 0.04em;
  white-space: nowrap;
  text-transform: uppercase;
}

.history-actions,
.keyword-list,
.toolbar-controls {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.text-action {
  border: none;
  background: transparent;
  color: #9ca3af;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}

.text-action:hover {
  color: #111827;
}

.keyword-chip {
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: #f7f8fa;
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.keyword-chip--hot {
  background: transparent;
  border-color: #eef1f4;
}

.keyword-chip:hover {
  background: #f3f4f6;
  border-color: #e5e7eb;
  color: #111827;
}

.assist-empty {
  margin: 0;
  color: #b0b8c4;
  font-size: 12px;
  line-height: 28px;
}

.control-area {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #eef1f4;
}

.control-group {
  display: flex;
  align-items: center;
  flex-direction: row;
  gap: 8px;
}

.segmented-control {
  display: inline-flex;
  padding: 3px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #f8f9fb;
  gap: 2px;
}

.segment-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0 12px;
  border-radius: 10px;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
}

.segment-item input {
  display: none;
}

.segment-item.active {
  background: #ffffff;
  color: #111827;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.quick-links {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 0 10px;
}

.quick-link {
  display: inline-flex;
  align-items: center;
  height: 30px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  padding: 0 10px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.quick-link span {
  display: block;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
}

.quick-link:hover {
  background: #f8f9fb;
  border-color: #edf0f4;
}

.quick-link:hover span {
  color: #111827;
}

.initial-hint {
  margin: 10px 2px 22px;
  max-width: 680px;
  color: #8a94a3;
  font-size: 13px;
  line-height: 1.8;
}

.initial-hint--warning {
  color: #9a3412;
}

.results-area {
  margin-top: 8px;
}

.result-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 6px 2px 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #eef1f4;
}

.result-toolbar--loading {
  justify-content: flex-start;
}

.toolbar-title {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
  font-weight: 600;
}

.toolbar-title strong {
  color: #111827;
}

.toolbar-meta {
  margin: 3px 0 0;
  font-size: 11px;
  color: #98a2b3;
}

.toolbar-controls {
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.state-panel {
  background: linear-gradient(180deg, #ffffff 0%, #fcfcfd 100%);
  border: 1px solid #e7eaf0;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
  border-radius: 20px;
  padding: 28px;
  margin-top: 14px;
}

.state-panel h2 {
  margin: 0 0 10px;
  font-size: 18px;
  color: #111827;
}

.state-panel p {
  margin: 0;
  max-width: 660px;
  font-size: 14px;
  line-height: 1.8;
  color: #6b7280;
}

.state-panel--error {
  border-color: #fee2e2;
  background: #fffafa;
}

.state-panel--error h2 {
  color: #b91c1c;
}

.state-panel--empty {
  padding: 18px 20px;
}

.state-panel--empty h2 {
  margin-bottom: 6px;
  font-size: 15px;
}

.state-panel--empty p {
  font-size: 13px;
}

.state-action {
  margin-top: 16px;
  height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.skeleton-card {
  border-radius: 20px;
  padding: 20px;
}

.skeleton-line {
  display: block;
  height: 12px;
  border-radius: 999px;
  background: linear-gradient(90deg, #eef1f4 0%, #f7f8fa 50%, #eef1f4 100%);
  background-size: 200% 100%;
  animation: shimmer 1.1s ease-in-out infinite;
  margin-top: 12px;
}

.skeleton-line--title {
  width: 32%;
  height: 18px;
  margin-top: 0;
}

.skeleton-line--address {
  width: 56%;
}

.skeleton-line--short {
  width: 68%;
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 780px) {
  .primary-search,
  .result-toolbar,
  .toolbar-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .assist-area,
  .quick-links {
    grid-template-columns: 1fr;
  }

  .assist-area {
    gap: 14px;
  }

  .quick-links {
    flex-wrap: wrap;
  }

  .control-area,
  .control-group {
    align-items: flex-start;
  }

  .filter-input,
  .filter-input--toolbar,
  .control-select,
  .search-btn {
    width: 100%;
  }

  .page-title {
    font-size: 30px;
  }

  .page-subtitle {
    font-size: 14px;
  }

  .search-workbench,
  .state-panel {
    padding: 16px;
    border-radius: 20px;
  }
}
</style>
