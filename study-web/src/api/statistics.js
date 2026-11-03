import request from '@/utils/request'

export function getLearningStatistics(days = 7) {
  return request.get('/statistics/overview', { params: { days } })
}

export function exportLearningStatistics(days = 7) {
  return request.get('/statistics/export', {
    params: { days },
    responseType: 'blob'
  })
}
