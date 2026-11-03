import request from '@/utils/request'

export function getChapterList(subjectId) {
  return request.get(`/subjects/${subjectId}/chapters`)
}

export function getAdminChapterPage(params) {
  return request.get('/admin/chapters', { params })
}

export function createChapter(data) {
  return request.post('/admin/chapters', data)
}

export function updateChapter(id, data) {
  return request.put(`/admin/chapters/${id}`, data)
}

export function updateChapterStatus(id, status) {
  return request.put(`/admin/chapters/${id}/status`, { status })
}

export function deleteChapter(id) {
  return request.delete(`/admin/chapters/${id}`)
}
