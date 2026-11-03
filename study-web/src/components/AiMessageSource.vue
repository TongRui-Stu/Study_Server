<script setup>
import { computed } from 'vue'
import { formatAiSource } from '@/utils/aiSource'
const props = defineProps({ message: { type: Object, required: true } })
const source = computed(() => formatAiSource(props.message))
</script>

<template><div v-if="source" class="ai-source">来自 {{ source }}</div></template>

<style scoped>
.ai-source { margin-top: 8px; color: var(--color-text-secondary); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
</style>
