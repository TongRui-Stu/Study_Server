import axios from 'axios'
import { clearToken, getToken } from './authStorage'

export function handleUnauthorized() {
  clearToken()
  if (window.location.pathname !== '/login') {
    window.location.replace('/login')
  }
}

export function authorizationHeaders() {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(config => {
  Object.assign(config.headers, authorizationHeaders())
  return config
})

request.interceptors.response.use(
  response => {
    if (response.config.responseType === 'blob') {
      return {
        data: response.data,
        headers: response.headers,
        status: response.status
      }
    }
    return response.data
  },
  error => {
    if (error.response?.status === 401) {
      handleUnauthorized()
    }
    return Promise.reject(error)
  }
)

export default request
