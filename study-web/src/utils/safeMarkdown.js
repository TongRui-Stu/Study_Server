import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import { katex } from '@mdit/plugin-katex'
import 'katex/dist/katex.min.css'

const markdown = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: false
}).use(katex, {
  delimiters: 'all',
  throwOnError: false,
  strict: false
})

const defaultLinkOpen = markdown.renderer.rules.link_open
  || ((tokens, index, options, environment, self) => self.renderToken(tokens, index, options))

markdown.renderer.rules.link_open = (tokens, index, options, environment, self) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer nofollow')
  return defaultLinkOpen(tokens, index, options, environment, self)
}

const sanitizeOptions = {
  USE_PROFILES: { html: true, svg: true, mathMl: true },
  ADD_ATTR: ['target'],
  FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form'],
  FORBID_ATTR: ['srcdoc']
}

function escapeHtml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function fallbackHtml(content, inline) {
  const escaped = escapeHtml(content).replace(/\r?\n/g, '<br>')
  return inline ? escaped : `<p>${escaped}</p>`
}

function normalizeInlineDisplayMath(content) {
  return content
    .replace(/\\\[([\s\S]*?)\\\]/g, (_, formula) => `\\(${formula}\\)`)
    .replace(/\$\$([\s\S]*?)\$\$/g, (_, formula) => `$${formula}$`)
}

function renderSafely(content, inline) {
  const source = String(content ?? '')
  try {
    const rendered = inline
      ? markdown.renderInline(normalizeInlineDisplayMath(source))
      : markdown.render(source)
    return DOMPurify.sanitize(rendered, sanitizeOptions)
  } catch {
    return DOMPurify.sanitize(fallbackHtml(source, inline), sanitizeOptions)
  }
}

export function renderSafeMarkdown(content) {
  return renderSafely(content, false)
}

export function renderSafeMarkdownInline(content) {
  return renderSafely(content, true)
}
