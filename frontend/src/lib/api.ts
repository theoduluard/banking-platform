import axios, { type InternalAxiosRequestConfig } from 'axios'
import {
  getRefreshToken,
  setToken,
  setRefreshToken,
  removeToken,
  removeRefreshToken,
} from '@/lib/auth'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  headers: { 'Content-Type': 'application/json' },
})

// ── Attach access token on every outgoing request ────────────────────────────

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// ── 401 interceptor — refresh + request queue ─────────────────────────────────
//
// Strategy:
//  1. If a 401 arrives and we have a refresh token, attempt one refresh call.
//  2. All concurrent 401s during the refresh are queued and retried after.
//  3. If the refresh itself fails (expired/revoked), clear both tokens and
//     redirect to /login.
//
// We use a raw axios call for the refresh so it skips this interceptor and
// never enters an infinite loop.

let isRefreshing = false
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (err: unknown) => void
}> = []

function processQueue(err: unknown, token: string | null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (err) reject(err)
    else resolve(token!)
  })
  failedQueue = []
}

function logout() {
  removeToken()
  removeRefreshToken()
  window.location.href = '/login'
}

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    // Only handle 401; let every other status pass through immediately.
    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error)
    }

    const refreshToken = getRefreshToken()

    // No refresh token available → hard logout
    if (!refreshToken) {
      logout()
      return Promise.reject(error)
    }

    // A refresh is already in-flight — queue this request
    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then((newToken) => {
        originalRequest._retry = true
        originalRequest.headers['Authorization'] = `Bearer ${newToken}`
        return api(originalRequest)
      })
    }

    // First 401 — kick off the refresh
    isRefreshing = true
    originalRequest._retry = true

    try {
      const { data } = await axios.post<{ accessToken: string; refreshToken: string }>(
        `${import.meta.env.VITE_API_URL}/api/v1/auth/refresh`,
        { refreshToken },
      )

      setToken(data.accessToken)
      setRefreshToken(data.refreshToken)

      processQueue(null, data.accessToken)

      originalRequest.headers['Authorization'] = `Bearer ${data.accessToken}`
      return api(originalRequest)
    } catch (refreshError) {
      processQueue(refreshError, null)
      logout()
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)

export default api
