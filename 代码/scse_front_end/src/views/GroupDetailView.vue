<template>
  <div class="group-detail-page">
    <button class="back-btn" @click="router.back()">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
        <polyline points="15 18 9 12 15 6" />
      </svg>
      <span>返回浏览</span>
    </button>

    <div v-if="groupLoading" class="loading-state">
      <span class="spinner"></span>
      <span>加载中...</span>
    </div>

    <template v-else-if="group">
      <div class="page-header">
        <p class="page-eyebrow">CURATED GROUP</p>
        <h1 class="page-title">{{ formatGroupTitle(group.groupTitle) }}</h1>
        <p class="page-subtitle">
          {{ formatGroupDescription(group.groupTitle, group.groupDescription) }}
        </p>
      </div>

      <div v-if="listLoading" class="loading-state">
        <span class="spinner"></span>
        <span>加载合约列表...</span>
      </div>

      <p v-else-if="listError" class="error-msg">{{ listError }}</p>

      <template v-else>
        <p class="result-count" v-if="total > 0">
          共 <strong>{{ total }}</strong> 个合约
        </p>

        <ContractListWithPagination
          :items="contracts"
          :page-size="pageSize"
          empty-text="该组暂无合约"
          @card-click="handleCardClick"
          @page-change="handlePageChange"
        />
      </template>
    </template>

    <p v-else class="error-msg">{{ groupError || '组不存在' }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { browseApi } from '@/api'
import ContractListWithPagination from '@/components/ContractListWithPagination.vue'

const router = useRouter()
const route = useRoute()
const groupId = route.params.groupId

const group = ref({
  groupTitle: route.query.title || '',
  groupDescription: route.query.description || ''
})
const groupLoading = ref(false)
const groupError = ref('')

const contracts = ref([])
const total = ref(0)
const pageSize = 10
const listLoading = ref(false)
const listError = ref('')

function formatGroupTitle(title) {
  if (title === 'top100') return '热门合约'
  return title
}

function formatGroupDescription(title, description) {
  if (title === 'top100') return '交易活跃度较高的热门合约集合'
  return description
}

onMounted(async () => {
  groupLoading.value = true
  await fetchGroup()
  await fetchContracts()
})

async function fetchGroup() {
  try {
    const res = await browseApi.getGroupDetail(groupId)
    group.value = res.data
  } catch (err) {
    groupError.value = typeof err === 'string' ? err : '加载分组信息失败'
  } finally {
    groupLoading.value = false
  }
}

async function fetchContracts() {
  listLoading.value = true
  listError.value = ''
  try {
    const res = await browseApi.getGroupContracts(groupId)
    contracts.value = res.data
    total.value = res.data.length
  } catch (err) {
    listError.value = typeof err === 'string' ? err : '加载合约列表失败'
  } finally {
    listLoading.value = false
  }
}

function handlePageChange() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleCardClick(item) {
  router.push(`/contract/${item.id}`)
}
</script>

<style scoped>
.group-detail-page {
  max-width: 840px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 14px;
  margin-bottom: 26px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #ffffff;
  color: #6b7280;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
}

.back-btn:hover {
  background: #f8f9fb;
  border-color: #d9dee5;
  color: #111827;
  transform: translateY(-1px);
}

.page-header {
  margin-bottom: 28px;
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

.result-count {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 16px;
  letter-spacing: 0.01em;
}

.result-count strong {
  color: #111827;
  font-weight: 700;
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

  .back-btn {
    margin-bottom: 22px;
  }
}
</style>