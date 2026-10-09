<template>
  <section v-if="items.length" class="answer-sources">
    <details>
      <summary title="查看参考来源" aria-label="查看参考来源">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <path d="M10.6 13.4a4.5 4.5 0 0 0 6.36 0l2.12-2.12a4.5 4.5 0 0 0-6.36-6.36L11.5 6.14" />
          <path d="M13.4 10.6a4.5 4.5 0 0 0-6.36 0l-2.12 2.12a4.5 4.5 0 0 0 6.36 6.36l1.22-1.22" />
        </svg>
      </summary>
      <div class="source-list">
        <details v-for="(item, index) in visibleItems" :key="item.key" class="source-item">
          <summary>
            <i>{{ index + 1 }}</i>
            <strong>{{ item.title }}</strong>
            <span v-if="item.cited" class="trust-badge">已引用</span>
            <span class="trust-badge" :class="`trust-badge--${String(item.trustLevel || '').toLowerCase()}`">{{ trustLevelLabel(item.trustLevel) }}</span>
          </summary>
          <div class="source-detail">
            <p>{{ item.excerpt || '暂无摘要' }}</p>
            <small v-if="item.location">{{ item.location }}</small>
            <small v-if="item.relatedCount > 1">合并了 {{ item.relatedCount }} 条相同依据</small>
            <a v-if="item.link?.external" :href="item.link.href" target="_blank" rel="noopener noreferrer">打开原始来源 ↗</a>
            <router-link v-else-if="item.link" :to="item.link.href">查看合约源码</router-link>
            <small v-if="item.sourceRef">{{ item.sourceRef }}</small>
          </div>
        </details>
        <button v-if="items.length > previewCount" type="button" class="source-more" :aria-expanded="showAll" @click="showAll = !showAll">
          {{ showAll ? '收起更多来源' : `展开全部 ${items.length} 个来源` }}
        </button>
      </div>
    </details>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { trustLevelLabel } from './evidence'

const props = defineProps({ items: { type: Array, default: () => [] } })
const previewCount = 5
const showAll = ref(false)
const visibleItems = computed(() => showAll.value ? props.items : props.items.slice(0, previewCount))
</script>

<style scoped>
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


.source-more {
  display: block;
  border: 0;
  background: transparent;
  padding: 5px 0;
  color: #475467;
  cursor: pointer;
  text-decoration: underline;
}
.source-detail p { white-space: pre-wrap; overflow-wrap: anywhere; }
.source-detail a { display: inline-block; margin-top: 6px; font-size: 12px; color: #344054; }
</style>
