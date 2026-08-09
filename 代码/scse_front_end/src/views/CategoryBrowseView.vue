<template>
  <div class="category-page">
    <button class="back-btn" @click="router.back()">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
        <polyline points="15 18 9 12 15 6" />
      </svg>
      <span>返回浏览探索</span>
    </button>

    <div class="page-header">
      <p class="page-eyebrow">{{ categoryMeta.eyebrow }}</p>
      <h1 class="page-title">{{ categoryMeta.title }}</h1>
      <p class="page-subtitle">{{ categoryMeta.description }}</p>
    </div>

    <div class="filter-toolbar" v-if="!loading && !error">
      <div class="toolbar-controls">
        <input
          v-model="localKeyword"
          class="filter-input"
          type="text"
          :placeholder="isAllCategory ? '在全部合约中筛选' : '在当前分类中筛选'"
        />
        <select v-model="sortType" class="sort-select">
          <option value="default">默认顺序</option>
          <option value="name">名称排序</option>
          <option value="category">标签排序</option>
        </select>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <span class="spinner"></span>
      <span>加载中...</span>
    </div>

    <p v-else-if="error" class="error-msg">{{ error }}</p>

    <template v-else>
      <div class="result-summary">
        <span>
          当前显示 <strong>{{ filteredContracts.length }}</strong> 个合约
          <template v-if="filteredContracts.length !== rawContracts.length">
            / 原始 {{ rawContracts.length }} 个
          </template>
        </span>
      </div>

      <div v-if="filteredContracts.length === 0" class="empty-state">
        <h2>当前分类下暂无符合条件的合约</h2>
        <p>调整关键词过滤，或清除筛选后查看完整列表。</p>
        <div class="empty-actions">
          <button class="empty-action" @click="resetFilters">清除筛选</button>
          <button class="empty-action" @click="router.push('/browse/category/all?title=%E6%B5%8F%E8%A7%88%E5%85%A8%E9%83%A8')">浏览全部</button>
        </div>
      </div>

      <ContractListWithPagination
        v-else
        :items="filteredContracts"
        :page-size="pageSize"
        :highlight-keyword="localKeyword"
        @card-click="handleCardClick"
        @page-change="handlePageChange"
      />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { searchApi, contractApi } from '@/api'
import ContractListWithPagination from '@/components/ContractListWithPagination.vue'

const router = useRouter()
const route = useRoute()

const categoryKey = route.params.key

const rawContracts = ref([])
const pageSize = 10
const loading = ref(true)
const error = ref('')
const localKeyword = ref('')
const sortType = ref('default')

const categoryMap = {
  starter: {
    title: '新手入门',
    eyebrow: 'STARTER PICKS',
    description: '优先浏览结构清晰、功能容易理解的合约，适合第一次了解智能合约。',
    query: 'ERC20 Token NFT 简单 基础 智能合约',
    maxItems: 24,
  },
  popular: {
    title: '热门合约',
    eyebrow: 'POPULAR CONTRACTS',
    description: '关注度更高、代表性更强的合约集合，适合作为核心内容入口。',
    query: 'NFT Token ERC20 ERC721 DeFi 智能合约',
    maxItems: 60,
  },
  cases: {
    title: '典型案例',
    eyebrow: 'USE CASES',
    description: '不追求数量，重点呈现应用场景明确、能代表真实用途的合约样本。',
    query: 'marketplace auction vault governance staking reward 应用场景 智能合约',
    maxItems: 36,
  },
  all: {
    title: '浏览全部',
    eyebrow: 'ALL CONTRACTS',
    description: '查看平台当前收录的全部合约内容，适合整体浏览与检索补充。',
    query: '',
    maxItems: null,
  },
}

const categoryMeta = computed(() => {
  return categoryMap[categoryKey] || {
    title: '分类浏览',
    eyebrow: 'CATEGORY',
    description: '浏览该分类下的相关合约。',
    query: '智能合约',
    maxItems: 48,
  }
})

const isAllCategory = computed(() => categoryKey === 'all')

const filteredContracts = computed(() => {
  const keyword = localKeyword.value.trim().toLowerCase()
  let next = rawContracts.value.map((item, index) => ({ item, index }))

  if (keyword) {
    next = next.filter(({ item }) => getSearchText(item).includes(keyword))
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

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    let res
    if (categoryKey === 'all') {
      res = await contractApi.getAllContracts()
    } else {
      res = await searchApi.searchContracts({
        input: categoryMeta.value.query,
        searchMode: 'semantic',
        limit: 100,
      })
    }

    const data = Array.isArray(res.data) ? res.data : []
    rawContracts.value = categoryMeta.value.maxItems
      ? data.slice(0, categoryMeta.value.maxItems)
      : data
  } catch (err) {
    error.value = typeof err === 'string' ? err : '加载分类内容失败'
  } finally {
    loading.value = false
  }
})

function getContractName(item) {
  return item?.contractName || item?.name || '未命名合约'
}

function getSearchText(item) {
  return [
    getContractName(item),
    item?.category,
    item?.address,
    item?.description,
  ].map(value => String(value || '').toLowerCase()).join(' ')
}

function resetFilters() {
  localKeyword.value = ''
  sortType.value = 'default'
}

function handlePageChange() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleCardClick(item) {
  router.push(`/contract/${item.id}`)
}
</script>

<style scoped>
.category-page {
  max-width: 900px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 14px;
  margin-bottom: 24px;
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

.page-header {
  margin-bottom: 20px;
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
  max-width: 680px;
  font-size: 15px;
  line-height: 1.8;
  color: #6b7280;
}

.page-subtitle {
  margin: 12px 0 0;
}

.filter-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  flex-wrap: wrap;
  padding: 12px 0;
  margin-bottom: 12px;
  border-top: 1px solid #eef1f4;
  border-bottom: 1px solid #eef1f4;
}

.toolbar-controls,
.empty-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-input,
.sort-select {
  height: 36px;
  border-radius: 12px;
  border: 1px solid #dfe4ea;
  background: #ffffff;
  color: #111827;
  font-size: 13px;
  outline: none;
}

.filter-input {
  width: 210px;
  padding: 0 12px;
}

.sort-select {
  min-width: 118px;
  padding: 0 28px 0 11px;
}

.filter-input:focus,
.sort-select:focus {
  border-color: #cfd6de;
  box-shadow: 0 0 0 4px rgba(17, 24, 39, 0.04);
}

.result-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 8px 0 12px;
  color: #6b7280;
  font-size: 13px;
}

.result-summary strong {
  color: #111827;
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
  margin-bottom: 12px;
}

.empty-state {
  border: 1px dashed #dfe4ea;
  border-radius: 18px;
  background: #ffffff;
  padding: 28px;
}

.empty-state h2 {
  margin: 0 0 8px;
  color: #111827;
  font-size: 17px;
}

.empty-state p {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.8;
}

.empty-actions {
  margin-top: 16px;
}

.empty-action {
  height: 34px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.empty-action:hover {
  color: #111827;
  border-color: #d9dee5;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(17, 24, 39, 0.18);
  border-top-color: #111827;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .page-title {
    font-size: 30px;
  }

  .page-subtitle {
    font-size: 14px;
  }

  .filter-toolbar,
  .result-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .filter-input,
  .sort-select {
    width: 100%;
  }

  .toolbar-controls {
    width: 100%;
  }
}
</style>
