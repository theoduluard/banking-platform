// ── Access token ─────────────────────────────────────────────────────────────

export function getToken(): string | null {
  return localStorage.getItem('token')
}

export function setToken(token: string): void {
  localStorage.setItem('token', token)
}

// ── Refresh token ─────────────────────────────────────────────────────────────

export function getRefreshToken(): string | null {
  return localStorage.getItem('refreshToken')
}

export function setRefreshToken(token: string): void {
  localStorage.setItem('refreshToken', token)
}

export function removeRefreshToken(): void {
  localStorage.removeItem('refreshToken')
}

// ── Shared logout helper — clears both tokens ─────────────────────────────────

export function removeToken(): void {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
}

/** Decode the JWT payload (no verification — display only). */
function decodePayload(token: string): Record<string, unknown> | null {
  try {
    return JSON.parse(atob(token.split('.')[1]))
  } catch {
    return null
  }
}

export function isAuthenticated(): boolean {
  const token = getToken()
  if (!token) return false
  const payload = decodePayload(token)
  if (!payload) return false
  // Check expiry — exp is in seconds, Date.now() in ms
  const exp = payload.exp as number | undefined
  if (exp && Date.now() / 1000 > exp) {
    // Token expired — clean up so future checks are instant
    removeToken()
    return false
  }
  return true
}

/** Decode the 'sub' claim (userId) from the JWT. */
export function getUserIdFromToken(): string | null {
  const token = getToken()
  if (!token) return null
  const p = decodePayload(token)
  return (p?.sub as string) ?? null
}

/** Decode the 'role' claim from the JWT. Returns 'CLIENT' | 'ADMIN' | null. */
export function getUserRoleFromToken(): string | null {
  const token = getToken()
  if (!token) return null
  const p = decodePayload(token)
  return (p?.role as string) ?? null
}
