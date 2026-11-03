<script setup>
import { computed, ref } from 'vue'
import { Moon, Sunny } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mobileMenuOpen = ref(false)
const { isDark, toggleTheme } = useTheme()
const themeToggleLabel = computed(() => isDark.value ? '切换为浅色模式' : '切换为深色模式')

const navigation = [
  { label: '首页', path: '/home' },
  { label: '错题本', path: '/wrong-questions' },
  { label: '我的收藏', path: '/favorites' },
  { label: '学习统计', path: '/statistics' }
]

function isActive(path) {
  if (path === '/home') {
    return route.path === '/home' || route.path.startsWith('/subjects/') ||
      route.path.startsWith('/chapters/') || route.path === '/practice' ||
      route.path.startsWith('/practice/')
  }
  return route.path === path
}

function navigate(path) {
  mobileMenuOpen.value = false
  router.push(path)
}

function logout() {
  mobileMenuOpen.value = false
  auth.logout()
  router.replace('/login')
}
</script>

<template>
  <header class="app-top-bar">
    <div class="app-top-bar__inner">
      <button class="brand-button" type="button" @click="navigate('/home')">
        <span class="brand-mark">学</span>
        <span><strong>在线学习平台</strong><small>学习与题库管理</small></span>
      </button>

      <nav class="desktop-navigation" aria-label="全局页面导航">
        <router-link
          v-for="item in navigation"
          :key="item.path"
          :to="item.path"
          :class="{ active: isActive(item.path) }"
        >{{ item.label }}</router-link>
        <router-link
          v-if="auth.user?.role === 'ADMIN'"
          to="/admin"
          :class="{ active: route.path.startsWith('/admin') }"
        >管理后台</router-link>
      </nav>

      <div class="user-actions">
        <span class="user-name" :title="auth.user?.username">{{ auth.user?.nickname || auth.user?.username }}</span>
        <el-button
          class="desktop-theme-toggle"
          circle
          :icon="isDark ? Sunny : Moon"
          :title="themeToggleLabel"
          :aria-label="themeToggleLabel"
          :aria-pressed="isDark"
          @click="toggleTheme"
        />
        <el-button class="desktop-logout" plain @click="logout">退出登录</el-button>
        <el-button class="mobile-menu-button" aria-label="打开页面导航" @click="mobileMenuOpen = true">页面导航</el-button>
      </div>
    </div>

    <el-drawer
      v-model="mobileMenuOpen"
      title="页面导航"
      direction="rtl"
      size="min(82vw, 320px)"
      append-to-body
    >
      <nav class="mobile-navigation" aria-label="移动端全局页面导航">
        <button
          v-for="item in navigation"
          :key="item.path"
          type="button"
          :class="{ active: isActive(item.path) }"
          @click="navigate(item.path)"
        >{{ item.label }}</button>
        <button
          v-if="auth.user?.role === 'ADMIN'"
          type="button"
          :class="{ active: route.path.startsWith('/admin') }"
          @click="navigate('/admin')"
        >管理后台</button>
        <button
          type="button"
          class="mobile-theme-toggle"
          :title="themeToggleLabel"
          :aria-label="themeToggleLabel"
          :aria-pressed="isDark"
          @click="toggleTheme"
        >
          <el-icon aria-hidden="true"><component :is="isDark ? Sunny : Moon" /></el-icon>
          <span>{{ isDark ? '浅色模式' : '深色模式' }}</span>
        </button>
      </nav>
      <template #footer>
        <div class="mobile-user-footer">
          <span>{{ auth.user?.nickname || auth.user?.username }}</span>
          <el-button type="danger" plain @click="logout">退出登录</el-button>
        </div>
      </template>
    </el-drawer>
  </header>
</template>

<style scoped>
.app-top-bar { position: sticky; top: 0; z-index: 100; height: var(--app-header-height); border-bottom: 1px solid var(--color-border); background: var(--color-navigation-bg); backdrop-filter: blur(12px); }
.app-top-bar__inner { display: flex; align-items: center; gap: 28px; width: min(100% - 40px, var(--content-max-width)); height: 100%; margin: 0 auto; }
.brand-button { display: flex; align-items: center; gap: 10px; flex: 0 0 auto; padding: 0; border: 0; background: transparent; color: var(--color-text); cursor: pointer; text-align: left; }
.brand-button > span:last-child { display: grid; gap: 2px; }
.brand-button strong { font-size: 16px; }
.brand-button small { color: var(--color-text-secondary); font-size: 11px; }
.brand-mark { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 11px; background: var(--color-primary); color: white; font-weight: 800; box-shadow: 0 6px 14px rgba(37, 99, 235, .22); }
.desktop-navigation { display: flex; align-items: stretch; align-self: stretch; gap: 4px; min-width: 0; }
.desktop-navigation a { display: flex; align-items: center; padding: 0 13px; border-bottom: 3px solid transparent; color: var(--color-text-secondary); font-size: 14px; font-weight: 600; text-decoration: none; transition: color .18s ease, background .18s ease, border-color .18s ease; }
.desktop-navigation a:hover, .desktop-navigation a.active, .desktop-navigation a.router-link-active { border-bottom-color: var(--color-primary); background: var(--color-primary-soft); color: var(--color-primary); }
.user-actions { display: flex; align-items: center; gap: 10px; margin-left: auto; min-width: 0; }
.user-name { max-width: 120px; overflow: hidden; color: var(--color-text-secondary); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.desktop-theme-toggle:focus-visible, .mobile-theme-toggle:focus-visible { outline: 3px solid var(--focus-ring); outline-offset: 2px; }
.mobile-menu-button { display: none; }
.mobile-navigation { display: grid; gap: 8px; }
.mobile-navigation button { padding: 14px 16px; border: 0; border-radius: var(--radius-md); background: var(--color-surface-muted); color: var(--color-text); cursor: pointer; font: inherit; text-align: left; }
.mobile-navigation button.active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: 700; }
.mobile-navigation .mobile-theme-toggle { display: flex; align-items: center; gap: 10px; margin-top: 10px; border: 1px solid var(--color-border); background: var(--color-surface); font-weight: 700; }
.mobile-user-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
@media (max-width: 900px) {
  .desktop-navigation, .desktop-logout { display: none; }
  .mobile-menu-button { display: inline-flex; }
}
@media (max-width: 520px) {
  .app-top-bar__inner { width: calc(100% - 24px); gap: 10px; }
  .brand-button small, .user-name { display: none; }
  .brand-button strong { font-size: 15px; }
  .mobile-menu-button { padding-inline: 10px; }
}
</style>
