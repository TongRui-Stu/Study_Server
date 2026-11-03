<script setup>
import { computed } from 'vue'
import { renderSafeMarkdown, renderSafeMarkdownInline } from '@/utils/safeMarkdown'

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  inline: {
    type: Boolean,
    default: false
  }
})

const safeHtml = computed(() => props.inline
  ? renderSafeMarkdownInline(props.content)
  : renderSafeMarkdown(props.content))
</script>

<template>
  <component
    :is="inline ? 'span' : 'div'"
    :class="['math-text', inline ? 'math-text--inline' : 'math-text--block']"
    v-html="safeHtml"
  />
</template>

<style>
.math-text {
  min-width: 0;
  max-width: 100%;
  color: inherit;
  line-height: inherit;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.math-text--inline { display: inline; }
.math-text--block > :first-child { margin-top: 0; }
.math-text--block > :last-child { margin-bottom: 0; }
.math-text--block p { margin: .35em 0; }
.math-text .katex { max-width: 100%; color: inherit; }
.math-text .katex-display {
  max-width: 100%;
  margin: .55em 0;
  padding: .15em 0;
  overflow-x: auto;
  overflow-y: hidden;
}
.math-text--inline .katex {
  font-size: 1em;
  line-height: 1;
}
</style>
