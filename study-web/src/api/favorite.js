import request from '@/utils/request'

export function getFavorites(params) {
  return request.get('/favorites', { params })
}

export function addFavorite(questionId) {
  return request.post(`/favorites/${questionId}`)
}

export function removeFavorite(questionId) {
  return request.delete(`/favorites/${questionId}`)
}

export function exportFavorites(params) {
  return request.get('/favorites/export', { params, responseType: 'blob' })
}
