import request, { authorizationHeaders, handleUnauthorized } from '@/utils/request'
import { consumeSseResponse } from '@/utils/sse'

const AI_TIMEOUT = 65000

export function sendAiChat(data) {
  return request.post('/ai/chat', data, { timeout: AI_TIMEOUT })
}

export function sendQuestionAiChat(data) {
  return request.post('/ai/question-chat', data, { timeout: AI_TIMEOUT })
}

export function sendLearningAnalysisMessage(data) {
  return request.post('/ai/learning-analysis', data, { timeout: AI_TIMEOUT })
}

async function streamAi(path, data, { signal, onEvent }) {
  const response = await fetch(`/api${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...authorizationHeaders()
    },
    body: JSON.stringify(data),
    signal
  })
  if (response.status === 401) {
    handleUnauthorized()
    throw new Error('登录状态已失效，请重新登录')
  }
  if (!response.ok) {
    let message = 'AI 服务暂时不可用，请稍后重试'
    try {
      const payload = await response.json()
      if (typeof payload?.message === 'string' && payload.message.trim()) message = payload.message
    } catch {
      // Keep the safe local message when the server does not return JSON.
    }
    throw new Error(message)
  }
  return consumeSseResponse(response, onEvent)
}

export function streamAiChat(data, options) {
  return streamAi('/ai/chat/stream', data, options)
}

export function streamQuestionAiChat(data, options) {
  return streamAi('/ai/question-chat/stream', data, options)
}

export function streamLearningAnalysisMessage(data, options) {
  return streamAi('/ai/learning-analysis/stream', data, options)
}
