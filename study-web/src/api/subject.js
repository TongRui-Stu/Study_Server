import request from '@/utils/request'

export function getSubjectList() {
  return request.get('/subjects')
}

export function getAdminSubjectPage(params) {
  return request.get('/admin/subjects', { params })
}

export function createSubject(data) {
  return request.post('/admin/subjects', data)
}

export function updateSubject(id, data) {
  return request.put(`/admin/subjects/${id}`, data)
}

export function updateSubjectStatus(id, status) {
  return request.put(`/admin/subjects/${id}/status`, { status })
}

export function deleteSubject(id) {
  return request.delete(`/admin/subjects/${id}`)
}
