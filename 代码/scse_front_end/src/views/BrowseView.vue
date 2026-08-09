<template>
  <div class="browse-page">
    <div class="page-header">
      <p class="page-eyebrow">CURATED DISCOVERY</p>
      <h1 class="page-title">探索浏览</h1>
      <p class="page-subtitle">按学习路径、热度和典型场景浏览合约。</p>
    </div>

    <div class="category-grid">
      <button
        v-for="category in categories"
        :key="category.key"
        class="category-card"
        @click="goToCategory(category)"
      >
        <div class="category-card__icon-wrap">
          <img class="category-card__icon" :src="csLogo" alt="CS Logo" />
        </div>

        <div class="category-card__body">
          <h3 class="category-card__title">{{ category.title }}</h3>
          <p class="category-card__desc">{{ category.description }}</p>
          <span class="category-card__count">{{ category.countLabel }}</span>
        </div>

        <svg class="category-card__arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 18 15 12 9 6" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import csLogo from '@/assets/cs-logo.png'

const router = useRouter()

const categories = [
  {
    key: 'starter',
    title: '新手入门',
    description: '结构清晰，适合快速理解。',
    countLabel: '入门精选'
  },
  {
    key: 'popular',
    title: '热门合约',
    description: '高频复用的代表性合约。',
    countLabel: '热门集合'
  },
  {
    key: 'cases',
    title: '典型案例',
    description: '场景明确，便于对照复用。',
    countLabel: '案例样本'
  },
  {
    key: 'all',
    title: '浏览全部',
    description: '查看当前收录内容。',
    countLabel: '全部合约'
  }
]

function goToCategory(category) {
  router.push(`/browse/category/${category.key}?title=${encodeURIComponent(category.title)}`)
}
</script>

<style scoped>
.browse-page {
  max-width: 920px;
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
  max-width: 920px;
  font-size: 15px;
  line-height: 1.8;
  color: #6b7280;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.category-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  text-align: left;
  background: linear-gradient(180deg, #ffffff 0%, #fcfcfd 100%);
  border: 1px solid #e7eaf0;
  border-radius: 22px;
  padding: 20px;
  cursor: pointer;
  transition:
    transform 0.24s ease,
    box-shadow 0.24s ease,
    border-color 0.24s ease,
    background 0.24s ease;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
  overflow: hidden;
}

.category-card::before {
  content: "";
  position: absolute;
  left: 18px;
  right: 18px;
  top: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    rgba(255,255,255,0),
    rgba(255,255,255,0.95),
    rgba(255,255,255,0)
  );
  opacity: 0.8;
}

.category-card:hover {
  transform: translateY(-2px);
  border-color: #d8dde6;
  background: linear-gradient(180deg, #ffffff 0%, #fbfbfc 100%);
  box-shadow:
    0 14px 30px rgba(15, 23, 42, 0.06),
    0 2px 6px rgba(15, 23, 42, 0.03);
}

.category-card__icon-wrap {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: #f8f9fb;
  border: 1px solid #edf0f4;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.category-card__icon {
  width: 20px;
  height: 20px;
  object-fit: contain;
}

.category-card__body {
  flex: 1;
  min-width: 0;
}

.category-card__title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.02em;
}

.category-card__desc {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
  line-height: 1.7;
}

.category-card__count {
  display: inline-flex;
  margin-top: 10px;
  color: #98a2b3;
  font-size: 12px;
  font-weight: 800;
}

.category-card__arrow {
  width: 18px;
  height: 18px;
  color: #9ca3af;
  flex-shrink: 0;
  transition: transform 0.2s ease, color 0.2s ease;
}

.category-card:hover .category-card__arrow {
  transform: translateX(2px);
  color: #6b7280;
}

@media (max-width: 840px) {
  .category-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .page-title {
    font-size: 30px;
  }

  .page-subtitle {
    font-size: 14px;
  }

  .category-card {
    padding: 18px;
    border-radius: 20px;
  }
}
</style>
