import request from '@/utils/request'

export function getKnowledgePointList(chapterId) {
  return request.get(`/chapters/${chapterId}/knowledge-points`)
}

export function getAdminKnowledgePointPage(params) {
  return request.get('/admin/knowledge-points', { params })
}

export function createKnowledgePoint(data) {
  return request.post('/admin/knowledge-points', data)
}

export function updateKnowledgePoint(id, data) {
  return request.put(`/admin/knowledge-points/${id}`, data)
}

export function updateKnowledgePointStatus(id, status) {
  return request.put(`/admin/knowledge-points/${id}/status`, { status })
}

export function deleteKnowledgePoint(id) {
  return request.delete(`/admin/knowledge-points/${id}`)
}
