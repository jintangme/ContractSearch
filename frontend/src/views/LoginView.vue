<template>
  <div
    ref="authPageRef"
    class="auth-page"
    @mousemove="handlePointerMove"
    @mouseleave="resetPointer"
  >
    <div class="auth-grid" aria-hidden="true"></div>
    <div class="auth-shapes" aria-hidden="true">
      <span class="shape shape--orb-one"></span>
      <span class="shape shape--orb-two"></span>
      <span class="shape shape--block-one"></span>
      <span class="shape shape--block-two"></span>
      <span class="shape shape--block-three"></span>
      <span class="shape shape--node-one"></span>
      <span class="shape shape--node-two"></span>
      <span class="shape shape--line-one"></span>
      <span class="shape shape--line-two"></span>
      <span class="shape shape--line-three"></span>
    </div>

    <main class="auth-layout">
      <section class="auth-copy" aria-label="产品定位">
        <p class="brand-name">ContractSearch</p>
        <h1>智能合约开发与安全验证平台</h1>
        <p class="brand-subtitle">检索 · 实验 · 预审 · 形式化验证</p>
      </section>

      <section class="auth-panel">
        <div class="auth-card">
          <div class="auth-header">
            <img class="auth-logo" :src="csLogo" alt="CS Logo" />
            <h2 class="auth-title">登录账号</h2>
          </div>

          <form class="auth-form" @submit.prevent="handleLogin">
            <div class="form-group">
              <input
                v-model="form.username"
                class="form-input"
                :class="{ error: errors.username }"
                type="text"
                placeholder="请输入用户名"
                autocomplete="username"
              />
              <p v-if="errors.username" class="form-error">{{ errors.username }}</p>
            </div>

            <div class="form-group">
              <input
                v-model="form.password"
                class="form-input"
                :class="{ error: errors.password }"
                type="password"
                placeholder="请输入密码"
                autocomplete="current-password"
              />
              <p v-if="errors.password" class="form-error">{{ errors.password }}</p>
            </div>

            <p v-if="serverError" class="form-error server-error">{{ serverError }}</p>

            <button type="submit" class="submit-btn" :disabled="loading">
              <span v-if="loading" class="spinner"></span>
              <span>{{ loading ? '登录中...' : '登录' }}</span>
            </button>
          </form>

          <p class="auth-switch">
            还没有账号？
            <router-link to="/register">立即注册</router-link>
          </p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import csLogo from '@/assets/cs-logo.png'

const authStore = useAuthStore()

const form = reactive({ username: '', password: '' })
const errors = reactive({ username: '', password: '' })
const serverError = ref('')
const loading = ref(false)
const authPageRef = ref(null)

function handlePointerMove(event) {
  const el = authPageRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const x = ((event.clientX - rect.left) / rect.width - 0.5) * 18
  const y = ((event.clientY - rect.top) / rect.height - 0.5) * 18
  el.style.setProperty('--parallax-x', `${x.toFixed(2)}px`)
  el.style.setProperty('--parallax-y', `${y.toFixed(2)}px`)
}

function resetPointer() {
  const el = authPageRef.value
  if (!el) return
  el.style.setProperty('--parallax-x', '0px')
  el.style.setProperty('--parallax-y', '0px')
}

function validate() {
  errors.username = form.username.trim() ? '' : '请输入用户名'
  errors.password = form.password ? '' : '请输入密码'
  return !errors.username && !errors.password
}

async function handleLogin() {
  serverError.value = ''
  if (!validate()) return
  loading.value = true
  try {
    await authStore.login({
      username: form.username.trim(),
      password: form.password
    })
  } catch (err) {
    serverError.value = typeof err === 'string' ? err : '登录失败，请检查用户名或密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  position: relative;
  --parallax-x: 0px;
  --parallax-y: 0px;
  min-height: 100vh;
  width: 100%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px clamp(32px, 6vw, 88px);
  background:
    radial-gradient(circle at 22% 30%, rgba(255, 255, 255, 0.72), transparent 25%),
    radial-gradient(circle at 62% 48%, rgba(15, 23, 42, 0.026), transparent 31%),
    radial-gradient(circle at 74% 65%, rgba(255, 255, 255, 0.62), transparent 24%),
    linear-gradient(135deg, #fafbfc 0%, #eef0f3 100%);
  box-sizing: border-box;
}

.auth-layout {
  position: relative;
  z-index: 1;
  width: min(100%, 1240px);
  min-height: min(660px, calc(100vh - 112px));
  display: grid;
  grid-template-columns: minmax(0, 56%) minmax(390px, 44%);
  align-items: center;
  column-gap: clamp(34px, 5vw, 72px);
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

.auth-shapes {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.shape {
  position: absolute;
  display: block;
}

.shape--orb-one,
.shape--orb-two {
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.70);
  filter: blur(16px);
  box-shadow: 0 28px 90px rgba(15, 23, 42, 0.12);
  transition: transform 0.18s ease-out;
}

.shape--orb-one {
  left: 8%;
  top: 24%;
  width: 280px;
  height: 280px;
  opacity: 0.78;
  transform: translate3d(calc(var(--parallax-x) * -0.45), calc(var(--parallax-y) * -0.45), 0);
}

.shape--orb-two {
  right: 15%;
  top: 26%;
  width: 260px;
  height: 260px;
  opacity: 0.68;
  transform: translate3d(calc(var(--parallax-x) * 0.35), calc(var(--parallax-y) * 0.35), 0);
}

.shape--block-one,
.shape--block-two,
.shape--block-three {
  border: 1px solid rgba(15, 23, 42, 0.105);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.70);
  box-shadow: 0 24px 76px rgba(15, 23, 42, 0.10);
  backdrop-filter: blur(8px);
  opacity: 0.86;
  animation: authFloat 14s ease-in-out infinite;
}

.shape--block-one {
  left: 8%;
  bottom: 11%;
  width: 220px;
  height: 148px;
  --r: -4deg;
  --fx: 14px;
  --fy: -18px;
  --px: calc(var(--parallax-x) * -0.55);
  --py: calc(var(--parallax-y) * -0.55);
  transform: rotate(var(--r));
}

.shape--block-two {
  left: 43%;
  top: 18%;
  width: 128px;
  height: 112px;
  border-radius: 26px;
  opacity: 0.68;
  --r: 5deg;
  --fx: -12px;
  --fy: 15px;
  --px: calc(var(--parallax-x) * 0.40);
  --py: calc(var(--parallax-y) * 0.40);
  transform: rotate(var(--r));
  animation-delay: 3.5s;
}

.shape--block-three {
  right: 20%;
  top: 58%;
  width: 74px;
  height: 74px;
  border-radius: 22px;
  opacity: 0.50;
  --r: -8deg;
  --fx: 10px;
  --fy: -12px;
  --px: calc(var(--parallax-x) * 0.72);
  --py: calc(var(--parallax-y) * 0.72);
  transform: rotate(var(--r));
  animation-delay: 7s;
}

.shape--node-one,
.shape--node-two {
  border-radius: 50%;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: rgba(255, 255, 255, 0.74);
  box-shadow: 0 14px 38px rgba(15, 23, 42, 0.10);
  animation: nodePulse 8s ease-in-out infinite;
  transition: transform 0.18s ease-out;
}

.shape--node-one {
  right: 13%;
  top: 18%;
  width: 18px;
  height: 18px;
  transform: translate3d(calc(var(--parallax-x) * 0.75), calc(var(--parallax-y) * 0.75), 0);
}

.shape--node-two {
  left: 34%;
  top: 61%;
  width: 12px;
  height: 12px;
  opacity: 0.78;
  animation-delay: 2s;
  transform: translate3d(calc(var(--parallax-x) * -0.38), calc(var(--parallax-y) * -0.38), 0);
}

.shape--line-one,
.shape--line-two,
.shape--line-three {
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(15, 23, 42, 0.125), transparent);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.36);
  transition: transform 0.18s ease-out;
}

.shape--line-one {
  left: 13%;
  top: 51%;
  width: 260px;
  opacity: 0.78;
  transform: translate3d(calc(var(--parallax-x) * -0.28), calc(var(--parallax-y) * -0.28), 0) rotate(-7deg);
}

.shape--line-two {
  left: 32%;
  bottom: 31%;
  width: 210px;
  opacity: 0.62;
  transform: translate3d(calc(var(--parallax-x) * 0.22), calc(var(--parallax-y) * 0.22), 0) rotate(10deg);
}

.shape--line-three {
  right: 15%;
  top: 38%;
  width: 160px;
  opacity: 0.54;
  transform: translate3d(calc(var(--parallax-x) * 0.42), calc(var(--parallax-y) * 0.42), 0) rotate(-18deg);
}

.auth-copy {
  max-width: 620px;
}

.brand-name {
  margin: 0 0 28px;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
}

.auth-copy h1 {
  margin: 0;
  max-width: 600px;
  color: #111827;
  font-size: clamp(36px, 3.25vw, 44px);
  line-height: 1.11;
  font-weight: 720;
  letter-spacing: -0.02em;
  word-break: keep-all;
}

.brand-subtitle {
  margin: 18px 0 0;
  color: #5f6877;
  font-size: 15px;
  font-weight: 500;
  opacity: 0.82;
}

.auth-panel {
  justify-self: center;
  width: 410px;
  max-width: min(410px, calc(100vw - 48px));
  padding: 44px 42px 36px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 28px 90px rgba(15, 23, 42, 0.10);
  box-sizing: border-box;
  animation: panelEnter 0.36s ease-out both;
  transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease;
}

.auth-panel:hover {
  transform: translateY(-4px);
  border-color: rgba(15, 23, 42, 0.16);
  box-shadow: 0 38px 112px rgba(15, 23, 42, 0.20);
}

.auth-card {
  width: 100%;
}

.auth-header {
  margin-bottom: 22px;
  text-align: center;
}

.auth-logo {
  display: block;
  width: 34px;
  height: 34px;
  margin: 0 auto 10px;
  object-fit: contain;
}

.auth-title {
  margin: 0;
  color: #111827;
  font-size: 27px;
  line-height: 1.1;
  font-weight: 740;
  letter-spacing: 0;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.form-input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid #d9dde4;
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
  border-color: rgba(17, 24, 39, 0.62);
  background: #fff;
  box-shadow: 0 0 0 4px rgba(17, 24, 39, 0.095), 0 12px 30px rgba(15, 23, 42, 0.09);
  transform: translateY(-2px);
}

.form-input.error {
  border-color: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.06);
}

.form-error {
  font-size: 13px;
  line-height: 1.5;
  color: #ef4444;
}

.server-error {
  text-align: center;
  margin-top: -4px;
}

.submit-btn {
  width: 100%;
  height: 48px;
  margin-top: 6px;
  border: none;
  border-radius: 14px;
  background: #111827;
  color: #ffffff;
  font-size: 15px;
  font-weight: 650;
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
  transform: translateY(-2px);
  box-shadow: 0 16px 38px rgba(15, 23, 42, 0.28);
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
  margin: 18px 0 0;
  text-align: center;
  font-size: 14px;
  color: #6b7280;
}

.auth-switch a {
  color: #111827;
  font-weight: 700;
  text-decoration: none;
  text-underline-offset: 3px;
  transition: color 0.2s ease, opacity 0.2s ease, text-decoration-color 0.2s ease;
}

.auth-switch a:hover {
  color: #000000;
  opacity: 0.72;
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
    transform: translate3d(var(--px), var(--py), 0) rotate(var(--r));
  }
  50% {
    transform: translate3d(calc(var(--px) + var(--fx)), calc(var(--py) + var(--fy)), 0) rotate(calc(var(--r) + 4deg));
  }
}

@keyframes nodePulse {
  0%,
  100% {
    opacity: 0.72;
  }
  50% {
    opacity: 1;
  }
}

@keyframes panelEnter {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 920px) {
  .auth-page {
    padding: 24px;
  }

  .auth-layout {
    min-height: auto;
    display: flex;
    justify-content: center;
  }

  .auth-copy {
    display: none;
  }

  .auth-panel {
    justify-self: auto;
    width: min(410px, 100%);
    padding: 38px 28px 30px;
  }

  .shape--block-one,
  .shape--block-two,
  .shape--block-three,
  .shape--node-one,
  .shape--node-two,
  .shape--line-one,
  .shape--line-two {
    display: none;
  }
}

@media (max-width: 520px) {
  .auth-page {
    padding: 16px;
  }

  .auth-panel {
    padding: 32px 20px 26px;
    border-radius: 22px;
  }

  .auth-title {
    font-size: 24px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .shape--block-one,
  .shape--block-two,
  .shape--block-three,
  .shape--node-one,
  .shape--node-two,
  .shape--line-one,
  .shape--line-two,
  .shape--line-three,
  .auth-panel,
  .form-input,
  .submit-btn {
    animation: none !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
