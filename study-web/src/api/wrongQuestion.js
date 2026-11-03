import request from '@/utils/request'

export function getWrongQuestions(params) {
  return request.get('/wrong-questions', { params })
}

export function removeWrongQuestion(questionId) {
  return request.delete(`/wrong-questions/${questionId}`)
}

export function exportWrongQuestions(params) {
  return request.get('/wrong-questions/export', { params, responseType: 'blob' })
}
