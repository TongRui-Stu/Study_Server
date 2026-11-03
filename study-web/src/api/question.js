import request from '@/utils/request'

export function getAdminQuestionPage(params) {
  return request.get('/admin/questions', { params })
}

export function exportAdminQuestions(params) {
  return request.get('/admin/questions/export', {
    params,
    responseType: 'blob'
  })
}

export function getAdminQuestion(id) {
  return request.get(`/admin/questions/${id}`)
}

export function createQuestion(data) {
  return request.post('/admin/questions', data)
}

export function updateQuestion(id, data) {
  return request.put(`/admin/questions/${id}`, data)
}

export function updateQuestionStatus(id, status) {
  return request.put(`/admin/questions/${id}/status`, { status })
}

export function deleteQuestion(id) {
  return request.delete(`/admin/questions/${id}`)
}

export function downloadQuestionImportTemplate() {
  return request.get('/admin/questions/import/template', {
    responseType: 'blob',
    timeout: 60000
  })
}

export function validateQuestionImport(file) {
  const data = new FormData()
  data.append('file', file)
  return request.post('/admin/questions/import/validate', data, { timeout: 60000 })
}

export function executeQuestionImport(file) {
  const data = new FormData()
  data.append('file', file)
  return request.post('/admin/questions/import/execute', data, { timeout: 60000 })
}
