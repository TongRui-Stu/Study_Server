<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import AppTopBar from '@/components/AppTopBar.vue'

const route = useRoute()
const mobileMenuOpen = ref(false)

const menuItems = [
  { path: '/admin', label: '管理首页' },
  { path: '/admin/subjects', label: '科目管理' },
  { path: '/admin/chapters', label: '章节管理' },
  { path: '/admin/knowledge-points', label: '知识点管理' },
  { path: '/admin/questions', label: '题库管理' },
  { path: '/admin/question-import', label: '题库批量导入' }
]

function closeMobileMenu() {
  mobileMenuOpen.value = false
}
</script>

<template>
  <div class="admin-shell">
    <AppTopBar />
    <el-container class="admin-body">
      <el-aside width="220px" class="admin-aside">
        <div class="admin-section-title">管理模块</div>
        <el-menu :default-active="route.path" router>
          <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">{{ item.label }}</el-menu-item>
        </el-menu>
      </el-aside>
      <el-container class="admin-content">
        <div class="admin-mobile-toolbar">
          <strong>管理后台</strong>
          <el-button aria-label="打开管理菜单" @click="mobileMenuOpen = true">管理菜单</el-button>
        </div>
        <el-main class="admin-main"><router-view /></el-main>
      </el-container>
    </el-container>

    <el-drawer
      v-model="mobileMenuOpen"
      title="管理菜单"
      direction="ltr"
      size="min(82vw, 300px)"
      append-to-body
    >
      <el-menu :default-active="route.path" router @select="closeMobileMenu">
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">{{ item.label }}</el-menu-item>
      </el-menu>
    </el-drawer>
  </div>
</template>

<style scoped>
.admin-shell { min-height: 100vh; }
.admin-body { min-height: calc(100vh - var(--app-header-height)); }
.admin-aside { position: sticky; top: var(--app-header-height); height: calc(100vh - var(--app-header-height)); background: var(--color-surface); border-right: 1px solid var(--color-border); }
.admin-section-title { display: flex; align-items: center; height: 58px; padding: 0 20px; color: var(--color-text-secondary); font-size: 13px; font-weight: 700; letter-spacing: 1px; }
.el-menu { border-right: 0; }
.admin-content { min-width: 0; flex-direction: column; }
.admin-main { min-width: 0; padding: 24px; background: var(--color-page); }
.admin-mobile-toolbar { display: none; }
@media (max-width: 760px) {
  .admin-aside { display: none; }
  .admin-mobile-toolbar { position: sticky; top: var(--app-header-height); z-index: 80; display: flex; align-items: center; justify-content: space-between; height: 54px; padding: 0 14px; border-bottom: 1px solid var(--color-border); background: var(--color-navigation-bg); }
  .admin-main { padding: 14px; }
}
</style>
