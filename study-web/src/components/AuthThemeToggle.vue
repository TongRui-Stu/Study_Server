<script setup>
import { computed } from 'vue'
import { Moon, Sunny } from '@element-plus/icons-vue'
import { useTheme } from '@/composables/useTheme'

const { isDark, toggleTheme } = useTheme()
const toggleLabel = computed(() => isDark.value ? '切换为浅色模式' : '切换为深色模式')
</script>

<template>
  <el-button
    class="auth-theme-toggle"
    circle
    :icon="isDark ? Sunny : Moon"
    :title="toggleLabel"
    :aria-label="toggleLabel"
    :aria-pressed="isDark"
    @click="toggleTheme"
  />
</template>

<style scoped>
.auth-theme-toggle {
  position: fixed;
  z-index: 10;
  top: 20px;
  right: 20px;
  width: 42px;
  height: 42px;
  border-color: var(--color-border);
  background: var(--color-navigation-bg);
  color: var(--color-text);
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(12px);
}
.auth-theme-toggle:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary);
}
.auth-theme-toggle:focus-visible {
  outline: 3px solid var(--focus-ring);
  outline-offset: 3px;
}
@media (max-width: 480px) {
  .auth-theme-toggle { top: 14px; right: 14px; width: 40px; height: 40px; }
}
</style>
