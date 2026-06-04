import axios, { type InternalAxiosRequestConfig } from 'axios'
import { getToken, setToken, removeToken } from '@/lib/auth'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  headers: { 'Content-Type': 'application/json' },
  // Fix 15: withCredentials allows the browser to send and receive HttpOnly cookies
  // (the refresh token cookie) on cross-origin requests to the api-gateway.
  withCredentials: true,
})

// ── Attach access token on every outgoing request ────────────────────────────

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// ── 401 interceptor — refresh + request queue ─────────────────────────────────
//
// Strategy:
//  1. If a 401 arrives, attempt one refresh call (no body — HttpOnly cookie is sent
//     automatically by the browser via withCredentials).
//  2. All concurrent 401s during the refresh are queued and retried after.
//  3. If the refresh itself fails (token expired/revoked), clear the access token
//     and redirect to /login.
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
  // Best-effort logout call — revokes the refresh token cookie on the server.
  // We use a fire-and-forget raw axios call so it doesn't trigger this interceptor.
  axios
    .post(
      `${import.meta.env.VITE_API_URL}/api/v1/auth/logout`,
      {},
      { withCredentials: true },
    )
    .catch(() => {
      // Ignore errors — session cleanup is best-effort here
    })

  removeToken()
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

    // Auth endpoints (login, register, refresh…) return 401 for bad credentials,
    // not for session expiry — never intercept them or we'd redirect to /login
    // on every failed login attempt.
    if (originalRequest.url?.includes('/api/v1/auth/')) {
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
      // Fix 15: no body needed — the HttpOnly refresh token cookie is sent
      // automatically by the browser because withCredentials is true.
      const { data } = await axios.post<{ accessToken: string }>(
        `${import.meta.env.VITE_API_URL}/api/v1/auth/refresh`,
        {},
        { withCredentials: true },
      )

      setToken(data.accessToken)

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
