import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import { features } from '@/config/features'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || null)
  const isLoggedIn = computed(() => !!token.value)

  // 登录：保存 token 并跳转
  async function login(credentials) {
    const res = await authApi.login(credentials)
    token.value = res.data.token
    localStorage.setItem('token', res.data.token)
    useUserStore().setProfile({
      id: res.data.id,
      username: res.data.username,
      nickname: res.data.nickname,
      email: res.data.email,
      avatarType: res.data.avatarType,
      avatarUrl: res.data.avatarUrl,
      avatarPresetKey: res.data.avatarPresetKey,
      avatarHistory: res.data.avatarHistory
    })
    const redirect = router.currentRoute.value.query.redirect
    router.push(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : features.homeRoute)
  }

  // 注册：成功后跳转到登录页
  async function register(data) {
    await authApi.register(data)
    router.push('/login')
  }

  // 退出登录
  function logout() {
    token.value = null
    localStorage.removeItem('token')
    useUserStore().clearProfile()
    router.push('/login')
  }

  return { token, isLoggedIn, login, register, logout }
})
