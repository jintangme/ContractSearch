<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
    <div class="sidebar-logo">
      <div class="brand-lockup">
        <img class="logo-image" :src="csLogo" alt="CS Logo" />
        <span class="logo-text">ContractSearch</span>
      </div>
      <button
        class="sidebar-toggle"
        type="button"
        :aria-label="collapsed ? '展开侧边栏' : '折叠侧边栏'"
        @click="$emit('toggle-collapse')"
      >
        <svg v-if="!collapsed" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="4" width="18" height="16" rx="2" />
          <path d="M9 4v16" />
          <path d="m16 9-3 3 3 3" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="4" width="18" height="16" rx="2" />
          <path d="M9 4v16" />
          <path d="m13 9 3 3-3 3" />
        </svg>
      </button>
    </div>

    <nav class="sidebar-nav">
      <router-link to="/conversation" class="nav-item" active-class="nav-item--active" :title="features.agentEnabled ? '对话' : '智能对话尚未启用'" @click="handleConversationNav">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z" />
          <path d="M8 9h8" />
          <path d="M8 13h5" />
        </svg>
        <span>{{ features.agentEnabled ? '对话' : '对话（未启用）' }}</span>
      </router-link>

      <router-link to="/search" class="nav-item" active-class="nav-item--active" title="搜索合约">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.35-4.35" />
        </svg>
        <span>搜索合约</span>
      </router-link>

      <router-link to="/browse" class="nav-item" active-class="nav-item--active" title="浏览探索">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="7" height="7" />
          <rect x="14" y="3" width="7" height="7" />
          <rect x="14" y="14" width="7" height="7" />
          <rect x="3" y="14" width="7" height="7" />
        </svg>
        <span>浏览探索</span>
      </router-link>

      <router-link to="/contract-lab" class="nav-item" active-class="nav-item--active" title="合约实验室">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M7 8 3 12l4 4" />
          <path d="m17 8 4 4-4 4" />
          <path d="m14 4-4 16" />
        </svg>
        <span>合约实验室</span>
      </router-link>

      <router-link to="/contract-check" class="nav-item" active-class="nav-item--active" title="智能预审">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 11l3 3L22 4"/>
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
        </svg>
        <span>智能预审</span>
      </router-link>

      <router-link
        v-if="isLoggedIn"
        to="/profile"
        class="nav-item"
        active-class="nav-item--active"
        title="个人中心"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
        <span>个人中心</span>
      </router-link>
    </nav>

    <div class="sidebar-footer">
      <router-link v-if="!isLoggedIn" to="/login" class="nav-item footer-btn login-btn" title="登录">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
          <polyline points="10 17 15 12 10 7" />
          <line x1="15" y1="12" x2="3" y2="12" />
        </svg>
        <span>登录</span>
      </router-link>

      <button v-else class="nav-item footer-btn logout-btn" title="退出登录" @click="handleLogout">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <polyline points="16 17 21 12 16 7" />
          <line x1="21" y1="12" x2="9" y2="12" />
        </svg>
        <span>退出登录</span>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { features } from '@/config/features'
import { useAuthStore } from '@/stores/auth'
import csLogo from '@/assets/cs-logo.png'

defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})

defineEmits(['toggle-collapse'])

const authStore = useAuthStore()
const isLoggedIn = computed(() => authStore.isLoggedIn)

function handleLogout() {
  authStore.logout()
}

function handleConversationNav() {
  if (!features.agentEnabled) return
  window.dispatchEvent(new CustomEvent('conversation:new'))
}
</script>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  width: var(--sidebar-width);
  height: 100vh;
  background: #ffffff;
  border-right: 1px solid #eceff3;
  display: flex;
  flex-direction: column;
  z-index: 100;
  transition: width 0.22s ease;
}

.sidebar--collapsed {
  width: var(--sidebar-collapsed-width);
}

.sidebar-logo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 18px 14px 14px;
  border-bottom: 1px solid #f1f3f6;
  margin-bottom: 6px;
  min-height: 64px;
}

.brand-lockup {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.logo-image {
  width: 18px;
  height: 18px;
  object-fit: contain;
  flex-shrink: 0;
}

.logo-text {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.1px;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  transition:
    opacity 0.16s ease,
    width 0.22s ease;
}

.sidebar-toggle {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border: 1px solid #e7eaf0;
  border-radius: 11px;
  background: #ffffff;
  color: #6b7280;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.sidebar-toggle:hover {
  background: #f7f8fa;
  border-color: #dfe4ea;
  color: #111827;
}

.sidebar-toggle svg {
  width: 17px;
  height: 17px;
}

.sidebar-nav {
  flex: 1;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 12px;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  background: transparent;
  border: none;
  width: 100%;
  text-align: left;
  text-decoration: none;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.nav-item svg {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.nav-item span {
  white-space: nowrap;
  overflow: hidden;
  transition:
    opacity 0.16s ease,
    width 0.22s ease;
}

.nav-item:hover {
  background: #f7f8fa;
  color: #111827;
}

.nav-item--active {
  background: #f3f4f6;
  color: #111827;
  font-weight: 600;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid #f1f3f6;
}

.footer-btn {
  border-radius: 12px;
}

.login-btn {
  color: #111827;
}

.login-btn:hover {
  background: #f7f8fa;
  color: #111827;
}

.logout-btn {
  color: #6b7280;
}

.logout-btn:hover {
  background: #f7f7f8;
  color: #111827;
}

.sidebar--collapsed .sidebar-logo {
  padding: 18px 12px 14px;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
}

.sidebar--collapsed .brand-lockup {
  display: none;
}

.sidebar--collapsed .logo-text,
.sidebar--collapsed .nav-item span {
  width: 0;
  opacity: 0;
}

.sidebar--collapsed .sidebar-nav,
.sidebar--collapsed .sidebar-footer {
  padding-left: 10px;
  padding-right: 10px;
}

.sidebar--collapsed .nav-item {
  justify-content: center;
  gap: 0;
  padding: 11px 0;
}

.sidebar--collapsed .footer-btn {
  justify-content: center;
}
</style>
