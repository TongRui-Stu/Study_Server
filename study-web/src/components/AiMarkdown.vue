<script setup>
import { computed, shallowRef, watch } from 'vue'
import { renderSafeMarkdown } from '@/utils/safeMarkdown'
import { createStreamingMarkdownBlocks } from '@/utils/streamingMarkdown'

const props = defineProps({
  streaming: { type: Boolean, default: false },
  content: {
    type: String,
    default: ''
  }
})

const parser = createStreamingMarkdownBlocks(renderSafeMarkdown)
const preview = shallowRef({ blocks: [], tail: '' })
watch(() => [props.content, props.streaming], ([content, streaming]) => {
  if (streaming) preview.value = parser.update(content)
  else parser.reset()
}, { immediate: true })
const safeHtml = computed(() => props.streaming ? '' : renderSafeMarkdown(props.content))
</script>

<template>
  <div v-if="streaming" class="ai-markdown ai-markdown--streaming">
    <div v-for="block in preview.blocks" :key="block.key" class="ai-markdown__block" v-html="block.html"></div>
    <div v-if="preview.tail" class="ai-markdown__tail">{{ preview.tail }}</div>
  </div>
  <div v-else class="ai-markdown" v-html="safeHtml"></div>
</template>

<style>
.ai-markdown {
  min-width: 0;
  max-width: 100%;
  color: inherit;
  line-height: 1.75;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.ai-markdown > :first-child { margin-top: 0; }
.ai-markdown > :last-child { margin-bottom: 0; }
.ai-markdown__block { display: contents; }
.ai-markdown__block:first-child > :first-child { margin-top: 0; }
.ai-markdown__tail { white-space: pre-wrap; }
.ai-markdown h1, .ai-markdown h2, .ai-markdown h3,
.ai-markdown h4, .ai-markdown h5, .ai-markdown h6 {
  margin: 1.05em 0 .55em;
  color: inherit;
  line-height: 1.35;
}
.ai-markdown h1 { font-size: 1.42em; }
.ai-markdown h2 { font-size: 1.28em; }
.ai-markdown h3 { font-size: 1.16em; }
.ai-markdown p { margin: .6em 0; }
.ai-markdown ul, .ai-markdown ol { margin: .65em 0; padding-left: 1.6em; }
.ai-markdown li + li { margin-top: .28em; }
.ai-markdown blockquote {
  margin: .8em 0;
  padding: .15em .9em;
  border-left: 4px solid var(--color-border-strong);
  color: var(--color-text-secondary);
  background: var(--color-surface-muted);
}
.ai-markdown hr { margin: 1em 0; border: 0; border-top: 1px solid var(--color-border); }
.ai-markdown a { color: var(--color-primary); text-decoration: underline; }
.ai-markdown code {
  padding: .12em .38em;
  border-radius: 4px;
  background: var(--color-code-inline-bg);
  font-family: Consolas, Monaco, monospace;
  font-size: .92em;
}
.ai-markdown pre {
  max-width: 100%;
  margin: .8em 0;
  padding: 12px 14px;
  overflow-x: auto;
  border-radius: 8px;
  background: var(--color-code-block-bg);
  color: var(--color-code-block-text);
  white-space: pre;
}
.ai-markdown pre code { padding: 0; background: transparent; color: inherit; }
.ai-markdown table {
  display: block;
  width: max-content;
  max-width: 100%;
  margin: .8em 0;
  overflow-x: auto;
  border-collapse: collapse;
}
.ai-markdown th, .ai-markdown td {
  min-width: 90px;
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  text-align: left;
  vertical-align: top;
}
.ai-markdown th { background: var(--color-table-header); font-weight: 700; }
.ai-markdown .katex-display {
  max-width: 100%;
  margin: .85em 0;
  padding: .25em 0;
  overflow-x: auto;
  overflow-y: hidden;
}
.ai-markdown .katex { max-width: 100%; }
</style>
