import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi } from '@/api'

export const useUserStore = defineStore('user', () => {
  const profile = ref(null)

  // 获取个人信息
  async function fetchProfile() {
    const res = await userApi.getProfile()
    profile.value = res.data
  }

  // 更新个人信息
  async function updateProfile(data) {
    const res = await userApi.updateProfile(data)
    if (res.code !== 200) {
      throw typeof res.message === 'string' ? res.message : '更新用户资料失败'
    }
    await fetchProfile()
  }

  function setProfile(data) {
    profile.value = data || null
  }

  // 修改密码
  async function changePassword(data) {
    const res = await userApi.changePassword(data)
    if (res.code !== 200) {
      throw typeof res.message === 'string' ? res.message : '修改密码失败'
    }
  }

  function clearProfile() {
    profile.value = null
  }

  return { profile, fetchProfile, updateProfile, changePassword, setProfile, clearProfile }
})
