export function getToken(): string | null {
  return localStorage.getItem('token')
}

export function setToken(token: string): void {
  localStorage.setItem('token', token)
}

export function removeToken(): void {
  localStorage.removeItem('token')
}

export function isAuthenticated(): boolean {
  return !!getToken()
}

/** Decode the JWT payload (no verification — display only). */
function decodePayload(token: string): Record<string, unknown> | null {
  try {
    return JSON.parse(atob(token.split('.')[1]))
  } catch {
    return null
  }
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
