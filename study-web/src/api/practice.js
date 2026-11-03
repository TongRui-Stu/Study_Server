import request from '@/utils/request'

export function getPracticeQuestions(params) {
  return request.get('/practice/questions', { params })
}

export function submitPracticeAnswer(data) {
  return request.post('/practice/submit', data)
}
