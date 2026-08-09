<template>
  <div class="auth-page">
    <div class="auth-grid" aria-hidden="true"></div>
    <span class="auth-orb auth-orb--one" aria-hidden="true"></span>
    <span class="auth-orb auth-orb--two" aria-hidden="true"></span>
    <span class="auth-block auth-block--one" aria-hidden="true"></span>
    <span class="auth-block auth-block--two" aria-hidden="true"></span>
    <span class="auth-block auth-block--three" aria-hidden="true"></span>

    <div class="auth-card">
      <div class="auth-header">
        <img class="auth-logo" :src="csLogo" alt="CS Logo" />
        <h1 class="auth-title">创建账号</h1>
        <p class="auth-subtitle">ContractSearch · 智能合约开发与安全验证平台</p>
      </div>

      <form class="auth-form" @submit.prevent="handleRegister">
        <div class="form-group">
          <input
            v-model="form.username"
            class="form-input"
            :class="{ error: errors.username }"
            type="text"
            placeholder="用户名（不超过 32 个字符）"
            autocomplete="username"
          />
          <p v-if="errors.username" class="form-error">{{ errors.username }}</p>
        </div>

        <div class="form-group">
          <input
            v-model="form.nickname"
            class="form-input"
            :class="{ error: errors.nickname }"
            type="text"
            placeholder="昵称（不超过 32 个字符）"
            autocomplete="nickname"
          />
          <p v-if="errors.nickname" class="form-error">{{ errors.nickname }}</p>
        </div>

        <div class="form-group">
          <input
            v-model="form.email"
            class="form-input"
            :class="{ error: errors.email }"
            type="email"
            placeholder="邮箱（example@email.com）"
            autocomplete="email"
          />
          <p v-if="errors.email" class="form-error">{{ errors.email }}</p>
        </div>

        <div class="form-group">
          <input
            v-model="form.password"
            class="form-input"
            :class="{ error: errors.password }"
            type="password"
            placeholder="请输入密码"
            autocomplete="new-password"
          />
          <p v-if="errors.password" class="form-error">{{ errors.password }}</p>
        </div>

        <div class="form-group">
          <input
            v-model="form.confirmPassword"
            class="form-input"
            :class="{ error: errors.confirmPassword }"
            type="password"
            placeholder="请再次输入密码"
            autocomplete="new-password"
          />
          <p v-if="errors.confirmPassword" class="form-error">{{ errors.confirmPassword }}</p>
        </div>

        <p v-if="serverError" class="form-error server-error">{{ serverError }}</p>

        <button type="submit" class="submit-btn" :disabled="loading">
          <span v-if="loading" class="spinner"></span>
          <span>{{ loading ? '注册中...' : '注册' }}</span>
        </button>
      </form>

      <p class="auth-switch">
        已有账号？
        <router-link to="/login">立即登录</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import csLogo from '@/assets/cs-logo.png'

const authStore = useAuthStore()

const form = reactive({
  username: '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const errors = reactive({
  username: '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const serverError = ref('')
const loading = ref(false)

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validate() {
  errors.username = !form.username.trim()
    ? '请输入用户名'
    : form.username.trim().length > 32
      ? '用户名不超过 32 个字符'
      : ''

  errors.nickname = !form.nickname.trim()
    ? '请输入昵称'
    : form.nickname.trim().length > 32
      ? '昵称不超过 32 个字符'
      : ''

  errors.email = !form.email.trim()
    ? '请输入邮箱'
    : !EMAIL_RE.test(form.email.trim())
      ? '邮箱格式不正确'
      : ''

  errors.password = !form.password ? '请设置密码' : ''

  errors.confirmPassword = !form.confirmPassword
    ? '请确认密码'
    : form.confirmPassword !== form.password
      ? '两次输入的密码不一致'
      : ''

  return Object.values(errors).every((e) => !e)
}

async function handleRegister() {
  serverError.value = ''
  if (!validate()) return

  loading.value = true
  try {
    await authStore.register({
      username: form.username.trim(),
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      password: form.password,
      confirmPassword: form.confirmPassword
    })
  } catch (err) {
    serverError.value = typeof err === 'string' ? err : '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  position: relative;
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
  background:
    radial-gradient(circle at 22% 26%, rgba(255, 255, 255, 0.68), transparent 25%),
    radial-gradient(circle at 66% 50%, rgba(15, 23, 42, 0.026), transparent 30%),
    linear-gradient(135deg, #f8f9fb 0%, #eef0f3 100%);
  box-sizing: border-box;
}

.auth-grid {
  position: absolute;
  inset: 0;
  opacity: 0.22;
  background-image:
    linear-gradient(rgba(15, 23, 42, 0.028) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, 0.028) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(circle at 46% 48%, #000 0%, transparent 72%);
}

.auth-orb {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.58);
  filter: blur(18px);
  box-shadow: 0 28px 90px rgba(15, 23, 42, 0.10);
}

.auth-orb--one {
  left: 12%;
  top: 18%;
  width: 260px;
  height: 260px;
}

.auth-orb--two {
  right: 16%;
  bottom: 10%;
  width: 220px;
  height: 220px;
  opacity: 0.72;
}

.auth-block {
  position: absolute;
  display: block;
  border: 1px solid rgba(15, 23, 42, 0.09);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.58);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(8px);
  opacity: 0.78;
  animation: authFloat 18s ease-in-out infinite;
}

.auth-block--one {
  left: 12%;
  bottom: 13%;
  width: 210px;
  height: 140px;
  --r: -4deg;
  transform: rotate(var(--r));
}

.auth-block--two {
  right: 22%;
  top: 18%;
  width: 124px;
  height: 106px;
  opacity: 0.62;
  --r: 5deg;
  transform: rotate(var(--r));
  animation-delay: 3.5s;
}

.auth-block--three {
  right: 31%;
  bottom: 28%;
  width: 70px;
  height: 70px;
  border-radius: 22px;
  opacity: 0.48;
  --r: -8deg;
  transform: rotate(var(--r));
  animation-delay: 7s;
}

.auth-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 28px;
  padding: 32px 30px 24px;
  box-shadow: 0 28px 90px rgba(15, 23, 42, 0.10);
  transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease;
}

.auth-card:hover {
  transform: translateY(-3px);
  border-color: rgba(15, 23, 42, 0.14);
  box-shadow: 0 36px 108px rgba(15, 23, 42, 0.17);
}

.auth-header {
  margin-bottom: 18px;
  text-align: center;
}

.auth-logo {
  display: block;
  width: 42px;
  height: 42px;
  margin: 0 auto 12px;
  object-fit: contain;
}

.auth-title {
  margin: 0;
  font-size: 28px;
  line-height: 1.1;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.03em;
}

.auth-subtitle {
  margin-top: 8px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-input {
  width: 100%;
  height: 44px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #111827;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
  box-sizing: border-box;
}

.form-input::placeholder {
  color: #9ca3af;
}

.form-input:focus {
  border-color: rgba(17, 24, 39, 0.52);
  background: #fff;
  box-shadow: 0 0 0 4px rgba(17, 24, 39, 0.075), 0 10px 26px rgba(15, 23, 42, 0.07);
  transform: translateY(-1px);
}

.form-input.error {
  border-color: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.06);
}

.form-error {
  font-size: 12px;
  line-height: 1.4;
  color: #ef4444;
}

.server-error {
  text-align: center;
  margin-top: -2px;
}

.submit-btn {
  width: 100%;
  height: 46px;
  margin-top: 4px;
  border: none;
  border-radius: 14px;
  background: #111827;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.13);
  transition: background 0.18s ease, opacity 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.submit-btn:hover:not(:disabled) {
  background: #030712;
  transform: translateY(-1px);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.24);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
  background: #111827;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.18);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.auth-switch {
  margin-top: 14px;
  text-align: center;
  font-size: 14px;
  color: #6b7280;
}

.auth-switch a {
  color: #111827;
  font-weight: 600;
  text-decoration: none;
  text-underline-offset: 3px;
  transition: color 0.2s ease, opacity 0.2s ease, text-decoration-color 0.2s ease;
}

.auth-switch a:hover {
  color: #000000;
  opacity: 0.82;
  text-decoration: underline;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.34);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes authFloat {
  0%,
  100% {
    transform: translate3d(0, 0, 0) rotate(var(--r));
  }
  50% {
    transform: translate3d(12px, -14px, 0) rotate(calc(var(--r) + 2deg));
  }
}

@media (max-width: 640px) {
  .auth-page {
    padding: 14px;
  }

  .auth-card {
    border-radius: 20px;
    padding: 24px 18px 20px;
  }

  .auth-orb {
    display: none;
  }

  .auth-block {
    display: none;
  }

  .auth-title {
    font-size: 26px;
  }

  .auth-logo {
    width: 38px;
    height: 38px;
    margin-bottom: 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-block,
  .auth-card,
  .form-input,
  .submit-btn {
    animation: none !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
