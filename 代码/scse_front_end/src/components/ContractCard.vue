<template>
  <div class="contract-card" @click="$emit('click')">
    <div class="contract-card__header">
      <span class="contract-name">{{ contract.contractName || contract.name || '未命名合约' }}</span>
      <span
        class="contract-category"
        v-if="contract.category"
        v-html="highlightText(contract.category)"
      ></span>
    </div>

    <p class="contract-address">{{ contract.address || '暂无地址' }}</p>

    <p v-if="description" class="contract-desc">{{ description }}</p>

    <p v-if="searchMode === 'code' && codeHits.length" class="code-snippet-line">
      <span v-if="codeHits[0].line">Line {{ codeHits[0].line }} ·</span>
      <code v-html="highlightCode(codeHits[0].text)"></code>
    </p>

    <div v-if="tagList.length" class="contract-tags">
      <span v-for="tag in tagList" :key="tag" v-html="highlightText(tag)"></span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  contract: {
    type: Object,
    required: true,
  },
  highlightKeyword: {
    type: String,
    default: '',
  },
  searchMode: {
    type: String,
    default: 'semantic',
  },
})
defineEmits(['click'])

const description = computed(() => {
  const text = props.contract.shortDescription || props.contract.description || props.contract.summary || ''
  return String(text).trim()
})

const tagList = computed(() => {
  const raw = [
    props.contract.category,
    ...(Array.isArray(props.contract.tags) ? props.contract.tags : []),
    ...(Array.isArray(props.contract.labels) ? props.contract.labels : []),
  ].filter(Boolean).map(item => String(item))
  const fromKeyword = props.highlightKeyword
    .split(/[\s,，;；]+/)
    .map(item => item.trim())
    .filter(item => item.length > 1)
  const fallback = raw.length ? [] : fromKeyword
  return [...new Set([...raw, ...fallback])].slice(0, 5)
})

const codeHits = computed(() => {
  const explicit = props.contract.codeHits || props.contract.matches || props.contract.snippets
  if (Array.isArray(explicit) && explicit.length) {
    return explicit.map(item => ({
      line: item.line || item.lineNumber || item.startLine || null,
      text: item.text || item.snippet || item.code || String(item)
    })).filter(item => item.text).slice(0, 3)
  }

  return []
})

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function highlightText(value) {
  const safeValue = escapeHtml(value || '')
  const keyword = props.highlightKeyword.trim()
  if (!keyword) return safeValue

  const terms = keyword
    .split(/[\s,，;；()]+/)
    .map(item => item.trim())
    .filter(item => item.length > 1)
    .slice(0, 3)
  if (!terms.length) return safeValue
  const pattern = new RegExp(`(${terms.map(term => escapeRegExp(escapeHtml(term))).join('|')})`, 'gi')
  return safeValue.replace(pattern, '<mark class="contract-highlight">$1</mark>')
}

function highlightCode(value) {
  const safeValue = escapeHtml(value || '')
  const keyword = props.highlightKeyword.trim()
  if (!keyword) return safeValue
  const terms = keyword
    .split(/[\s,，;；()]+/)
    .map(item => item.trim())
    .filter(item => /^[A-Za-z_$][\w$]*$/.test(item) || /^ERC\d+/i.test(item))
    .slice(0, 4)
  if (!terms.length) return safeValue
  const pattern = new RegExp(`\\b(${terms.map(term => escapeRegExp(escapeHtml(term))).join('|')})\\b`, 'gi')
  return safeValue.replace(pattern, '<mark class="contract-highlight contract-highlight--code">$1</mark>')
}

</script>

<style scoped>
.contract-card {
  position: relative;
  background: linear-gradient(180deg, #ffffff 0%, #fcfcfd 100%);
  border: 1px solid #e7eaf0;
  border-radius: 20px;
  padding: 20px 20px 18px;
  cursor: pointer;
  transition:
    transform 0.24s ease,
    box-shadow 0.24s ease,
    border-color 0.24s ease,
    background 0.24s ease;
  display: flex;
  flex-direction: column;
  gap: 9px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
  overflow: hidden;
}

.contract-card::before {
  content: "";
  position: absolute;
  left: 18px;
  right: 18px;
  top: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0),
    rgba(255, 255, 255, 0.95),
    rgba(255, 255, 255, 0)
  );
  opacity: 0.8;
}

.contract-card:hover {
  transform: translateY(-2px);
  border-color: #d8dde6;
  background: linear-gradient(180deg, #ffffff 0%, #fbfbfc 100%);
  box-shadow:
    0 14px 30px rgba(15, 23, 42, 0.06),
    0 2px 6px rgba(15, 23, 42, 0.03);
}

.contract-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}

.contract-name {
  font-size: 17px;
  font-weight: 700;
  color: #111827;
  line-height: 1.3;
  letter-spacing: -0.02em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 72%;
}

.contract-category {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f5f7fa;
  color: #667085;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
  border: 1px solid #edf0f4;
}

.contract-address {
  margin: 0;
  font-size: 12px;
  color: #98a2b3;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contract-desc {
  margin: 4px 0 0;
  font-size: 14px;
  color: #667085;
  line-height: 1.75;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.contract-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.contract-tags span {
  min-height: 22px;
  padding: 0 8px;
  border: 1px solid #edf0f4;
  border-radius: 999px;
  background: #f8fafc;
  color: #667085;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
}

.code-snippet-line {
  margin: 2px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-snippet-line span {
  color: #98a2b3;
  font-weight: 700;
}

.code-snippet-line code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  color: #111827;
}

:deep(.contract-highlight) {
  border-radius: 5px;
  padding: 0 3px;
  background: #eef2ff;
  color: #1f2937;
  font-weight: 700;
}

:deep(.contract-highlight--code) {
  background: #f3f4f6;
  color: #111827;
}

@media (max-width: 640px) {
  .contract-card {
    padding: 18px 16px 16px;
    border-radius: 18px;
  }

  .contract-name {
    max-width: 100%;
    font-size: 16px;
  }

  .contract-desc {
    font-size: 13px;
  }
}
</style>
