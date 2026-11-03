<script setup>
import AppTopBar from '@/components/AppTopBar.vue'
</script>

<template>
  <div class="user-shell">
    <AppTopBar />
    <div class="user-content"><router-view /></div>
  </div>
</template>

<style scoped>
.user-shell { min-height: 100vh; }
.user-content { min-height: calc(100vh - var(--app-header-height)); }
</style>
