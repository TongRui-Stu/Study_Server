const providerNames = { deepseek: 'DeepSeek', qwen: 'Qwen' }
const knownModels = {
  // Official deepseek-flash alias points to V4.1 Flash as of 2026-10-15.
  deepseek: { 'deepseek-v4.1-flash': 'DeepSeek V4.1 Flash', 'deepseek-flash': 'DeepSeek V4.1 Flash' },
  qwen: { 'qwen3.8-flash': 'Qwen3.8 Flash' }
}

export function formatAiSource(message) {
  if (!message || message.local || message.role !== 'assistant') return ''
  const provider = typeof message.provider === 'string' ? message.provider.trim().toLowerCase() : ''
  const model = typeof message.model === 'string' ? message.model.trim() : ''
  if (!providerNames[provider] || !model || model === 'local') return ''
  return knownModels[provider]?.[model] || `${providerNames[provider]} · ${model}`
}
