<template>
  <div class="result-list-wrapper">
    <p v-if="items.length === 0" class="empty-msg">{{ emptyText }}</p>

    <template v-else>
      <div class="contract-list">
        <ContractCard
          v-for="item in pagedItems"
          :key="item.id"
          :contract="item"
          :highlight-keyword="highlightKeyword"
          :search-mode="searchMode"
          @click="$emit('card-click', item)"
        />
      </div>

      <div class="pagination-wrap">
        <PaginationBar
          :current="currentPage"
          :total="items.length"
          :page-size="pageSize"
          @change="handlePageChange"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import ContractCard from '@/components/ContractCard.vue'
import PaginationBar from '@/components/PaginationBar.vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  pageSize: { type: Number, default: 10 },
  emptyText: { type: String, default: '暂无数据' },
  highlightKeyword: { type: String, default: '' },
  searchMode: { type: String, default: 'semantic' },
})

const emit = defineEmits(['card-click', 'page-change'])
const currentPage = ref(1)

const pagedItems = computed(() => {
  const start = (currentPage.value - 1) * props.pageSize
  return props.items.slice(start, start + props.pageSize)
})

watch(
  () => props.items,
  () => {
    currentPage.value = 1
  }
)

watch(
  () => [props.items.length, props.pageSize],
  () => {
    const totalPages = Math.max(1, Math.ceil(props.items.length / props.pageSize))
    if (currentPage.value > totalPages) currentPage.value = totalPages
  }
)

function handlePageChange(newPage) {
  currentPage.value = newPage
  emit('page-change', newPage)
}
</script>

<style scoped>
.result-list-wrapper {
  width: 100%;
}

.contract-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 14px;
}

.pagination-wrap {
  margin-top: 20px;
}

.empty-msg {
  padding: 56px 0;
  text-align: center;
  font-size: 14px;
  line-height: 1.7;
  color: #9ca3af;
  border: 1px dashed #e5e7eb;
  border-radius: 18px;
  background: #ffffff;
}
</style>
