import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/conversation' },
  // 登录/注册页
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { guest: true } },
  { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { guest: true } },

  // 浏览板块
  { path: '/explore', redirect: '/browse' },
  { path: '/browse', component: () => import('@/views/BrowseView.vue') },
  { path: '/browse/category/:key', component: () => import('@/views/CategoryBrowseView.vue') },
  { path: '/browse/:groupId', component: () => import('@/views/GroupDetailView.vue') },

  // 搜索板块
  { path: '/search', component: () => import('@/views/SearchView.vue') },

  { path: '/conversation', component: () => import('@/views/ConversationView.vue'), meta: { requiresAuth: true } },

  // 合约详情
  { path: '/contract/:id', component: () => import('@/views/ContractDetailView.vue') },

  { path: '/contract-check', component: () => import('@/views/ContractCheckView.vue') },
  { path: '/contract-lab', component: () => import('@/views/ContractLabView.vue') },

  // 个人信息
  { path: '/profile', component: () => import('@/views/ProfileView.vue'), meta: { requiresAuth: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫：未登录跳转到登录页，已登录不可访问 guest 页
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  const isGuestRoute = Boolean(to.meta.guest)
  if (!isGuestRoute && !token) {
    return {
      path: '/login',
      query: to.fullPath && to.fullPath !== '/' ? { redirect: to.fullPath } : undefined
    }
  }
  if (isGuestRoute && token) return { path: '/conversation' }
})

export default router
