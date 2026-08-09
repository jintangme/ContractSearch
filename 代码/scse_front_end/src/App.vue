<template>
  <div id="app">
    <AppSidebar v-if="showAppLayout" :collapsed="sidebarCollapsed" @toggle-collapse="toggleSidebar" />
    <main
      class="main-content"
      :class="{
        'main-content--collapsed': showAppLayout && sidebarCollapsed,
        'main-content--auth': !showAppLayout
      }"
    >
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from '@/components/AppSidebar.vue'
import { useAuthStore } from '@/stores/auth'

const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')
const route = useRoute()
const authStore = useAuthStore()

const showAppLayout = computed(() => authStore.isLoggedIn && !route.meta.guest)

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem('sidebarCollapsed', String(sidebarCollapsed.value))
}
</script>

<style>
#app {
  display: flex;
  height: 100%;
  min-height: 100vh;
}

.main-content {
  flex: 1;
  margin-left: var(--sidebar-width);
  width: calc(100vw - var(--sidebar-width));
  min-width: 0;
  min-height: 100vh;
  background: var(--color-bg-base);
  overflow-y: auto;
  overflow-x: hidden;
  padding: 32px 36px;
  transition:
    margin-left 0.22s ease,
    width 0.22s ease;
}

.main-content--collapsed {
  margin-left: var(--sidebar-collapsed-width);
  width: calc(100vw - var(--sidebar-collapsed-width));
}

.main-content--auth {
  margin-left: 0;
  width: 100vw;
  padding: 0;
}
</style>
