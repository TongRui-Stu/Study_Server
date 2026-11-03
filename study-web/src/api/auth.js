import request from '@/utils/request'

export function registerUser(data) {
  return request.post('/auth/register', data)
}

export function loginUser(data) {
  return request.post('/auth/login', data)
}

export function getCurrentUser() {
  return request.get('/auth/me')
}
