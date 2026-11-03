import { formatAiSource } from './aiSource.js'

const UTF8_BOM = '\uFEFF'
const WINDOWS_ILLEGAL_FILENAME_CHARS = /[<>:"/\\|?*\u0000-\u001F]/g

function normalizeLineEndings(value) {
  return value.replace(/\r\n?/g, '\n')
}

function resolveExportDate(exportedAt) {
  const date = exportedAt instanceof Date
    ? new Date(exportedAt.getTime())
    : new Date(exportedAt)
  if (Number.isNaN(date.getTime())) throw new Error('导出时间无效')
  return date
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatExportTime(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function formatFilenameTime(date) {
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}_${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
}

function sanitizeFilenamePart(value) {
  const sanitized = String(value || '')
    .replace(WINDOWS_ILLEGAL_FILENAME_CHARS, '_')
    .replace(/[. ]+$/g, '')
    .trim()
  if (!sanitized) throw new Error('导出文件名不能为空')
  return sanitized
}

function metadataEntries(metadata) {
  if (!metadata) return []
  if (Array.isArray(metadata)) return metadata
  return Object.entries(metadata)
}

export function getExportableMessages(messages) {
  if (!Array.isArray(messages)) return []
  return messages
    .filter(message => (
      message
      && message.local !== true
      && (message.role === 'user' || message.role === 'assistant')
      && typeof message.content === 'string'
      && message.content.trim().length > 0
    ))
    .map(message => ({
      role: message.role,
      content: normalizeLineEndings(message.content),
      provider: message.provider,
      model: message.model
    }))
}

export function hasExportableMessages(messages) {
  return getExportableMessages(messages).length > 0
}

export function buildConversationMarkdown({
  title,
  scene,
  messages,
  metadata,
  filenamePrefix,
  exportedAt = new Date()
}) {
  const validMessages = getExportableMessages(messages)
  if (validMessages.length === 0) throw new Error('没有可导出的对话')

  const date = resolveExportDate(exportedAt)
  const lines = [
    `# ${String(title).trim()}`,
    '',
    `- 场景：${String(scene).trim()}`
  ]

  metadataEntries(metadata).forEach(entry => {
    const [label, value] = entry
    if (String(label || '').trim() && value !== null && value !== undefined && String(value).trim()) {
      lines.push(`- ${String(label).trim()}：${normalizeLineEndings(String(value))}`)
    }
  })
  lines.push(`- 导出时间：${formatExportTime(date)}`, '')

  validMessages.forEach(message => {
    lines.push(message.role === 'user' ? '## 问：' : '## 答：', '', message.content, '')
    const source = formatAiSource(message)
    if (source) lines.push(`> 来源：${source.replace(/[\r\n]/g, ' ')}`, '')
  })

  return {
    content: `${lines.join('\n').replace(/\n+$/g, '')}\n`,
    filename: `${sanitizeFilenamePart(filenamePrefix)}_${formatFilenameTime(date)}.md`,
    messageCount: validMessages.length
  }
}

export function downloadConversationMarkdown(options) {
  const result = buildConversationMarkdown(options)
  const blob = new Blob([UTF8_BOM, result.content], { type: 'text/markdown;charset=utf-8' })
  let objectUrl = ''
  let link = null

  try {
    objectUrl = URL.createObjectURL(blob)
    link = document.createElement('a')
    link.href = objectUrl
    link.download = result.filename
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
  } finally {
    link?.remove()
    if (objectUrl) URL.revokeObjectURL(objectUrl)
  }

  return result
}
