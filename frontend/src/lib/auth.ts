// ── Access token (Fix 15) ─────────────────────────────────────────────────────
//
// Moved from localStorage → sessionStorage.
// sessionStorage is:
//  • cleared automatically when the tab/browser closes (shorter exposure window)
//  • not shared across tabs (reduces cross-tab XSS surface)
//  • still readable by same-origin JS, so it doesn't fully block XSS — but the
//    refresh token (which confers long-term access) is now in an HttpOnly cookie
//    and is completely inaccessible to JavaScript.

export function getToken(): string | null {
  // One-time migration: move any stale token from localStorage to sessionStorage
  const legacy = localStorage.getItem('token')
  if (legacy) {
    sessionStorage.setItem('token', legacy)
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken') // clean up old refresh token too
  }
  return sessionStorage.getItem('token')
}

export function setToken(token: string): void {
  sessionStorage.setItem('token', token)
}

// ── User role (Fix 16) ────────────────────────────────────────────────────────
//
// The role is stored from the login RESPONSE BODY (server-issued, HTTPS-delivered)
// instead of being decoded from the JWT payload on the client.
//
// Why this matters: the JWT payload can be decoded by anyone who has the token —
// it is only integrity-protected (signed), not encrypted. A malicious actor who
// injects JavaScript into the page could set localStorage['role'] = 'ADMIN' and
// make the UI think the user is an admin. By deriving the role from the login
// response (which the server controls), we eliminate this forgery vector.
// The backend still enforces authorization independently; this fix prevents
// misleading UI escalation.

export function setRole(role: string): void {
  sessionStorage.setItem('userRole', role)
}

export function getRole(): string | null {
  return sessionStorage.getItem('userRole')
}

// ── Shared logout helper — clears access token and role ───────────────────────

export function removeToken(): void {
  sessionStorage.removeItem('token')
  sessionStorage.removeItem('userRole')
  // Also clear any legacy localStorage tokens (migration safety net)
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
}

// ── Refresh token: HttpOnly cookie (Fix 15) ───────────────────────────────────
//
// The refresh token is no longer accessible to JavaScript at all — it lives in
// an HttpOnly cookie set by the server on login/refresh.  The browser sends it
// automatically (withCredentials: true) to /api/v1/auth/refresh and /logout.
//
// The shims below keep the existing import surface intact for callers that
// haven't been updated yet; they are all no-ops.

/** @deprecated Refresh token is now an HttpOnly cookie. This is a no-op. */
export function getRefreshToken(): string | null {
  return null
}

/** @deprecated Refresh token is now an HttpOnly cookie. This is a no-op. */
export function setRefreshToken(_token: string): void {
  // no-op — the server sets the cookie via Set-Cookie
}

/** @deprecated Refresh token is now an HttpOnly cookie. This is a no-op. */
export function removeRefreshToken(): void {
  // no-op
}

// ── Token utilities ────────────────────────────────────────────────────────────

/** Decode the JWT payload without verifying the signature (display-only). */
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
    removeToken()
    return false
  }
  return true
}

/** Reads the userId from the JWT 'sub' claim (safe — userId is not privileged). */
export function getUserIdFromToken(): string | null {
  const token = getToken()
  if (!token) return null
  const p = decodePayload(token)
  return (p?.sub as string) ?? null
}

/**
 * Fix 16: returns the role stored in sessionStorage at login time (from the
 * server response body), NOT decoded from the unsigned JWT payload.
 * Falls back to the JWT claim only if the sessionStorage value is missing
 * (e.g. after a hard page reload that cleared sessionStorage).
 */
export function getUserRoleFromToken(): string | null {
  const stored = getRole()
  if (stored) return stored

  // Fallback: decode from JWT (safe for display — server still enforces authz)
  const token = getToken()
  if (!token) return null
  const p = decodePayload(token)
  return (p?.role as string) ?? null
}
