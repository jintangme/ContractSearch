<template>
  <div class="pagination" v-if="totalPages > 1">
    <button
      class="page-btn page-btn--icon"
      :disabled="current <= 1"
      @click="$emit('change', current - 1)"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
        <polyline points="15 18 9 12 15 6" />
      </svg>
    </button>

    <template v-for="p in pages" :key="p">
      <span v-if="p === '...'" class="page-ellipsis">…</span>
      <button
        v-else
        class="page-btn"
        :class="{ 'page-btn--active': p === current }"
        @click="$emit('change', p)"
      >
        {{ p }}
      </button>
    </template>

    <button
      class="page-btn page-btn--icon"
      :disabled="current >= totalPages"
      @click="$emit('change', current + 1)"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
        <polyline points="9 18 15 12 9 6" />
      </svg>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  current: { type: Number, required: true },
  total: { type: Number, required: true },
  pageSize: { type: Number, default: 10 },
})
defineEmits(['change'])

const totalPages = computed(() => Math.ceil(props.total / props.pageSize))

const pages = computed(() => {
  const t = totalPages.value
  const c = props.current
  if (t <= 7) return Array.from({ length: t }, (_, i) => i + 1)

  const result = []
  result.push(1)

  if (c > 4) result.push('...')

  const start = Math.max(2, c - 2)
  const end = Math.min(t - 1, c + 2)

  for (let i = start; i <= end; i++) result.push(i)

  if (c < t - 3) result.push('...')

  result.push(t)
  return result
})
</script>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 28px;
  flex-wrap: wrap;
}

.page-btn {
  min-width: 38px;
  height: 38px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #6b7280;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
}

.page-btn--icon {
  padding: 0;
}

.page-btn:hover:not(:disabled) {
  background: #f8f9fb;
  border-color: #d9dee5;
  color: #111827;
  transform: translateY(-1px);
}

.page-btn--active {
  background: #111827;
  border-color: #111827;
  color: #ffffff;
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.08),
    0 8px 16px rgba(15, 23, 42, 0.10);
}

.page-btn--active:hover:not(:disabled) {
  background: #111827;
  border-color: #111827;
  color: #ffffff;
}

.page-btn:disabled {
  opacity: 0.38;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.page-ellipsis {
  color: #9ca3af;
  font-size: 14px;
  padding: 0 2px;
  user-select: none;
}
</style>