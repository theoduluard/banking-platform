import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import {
  getToken,
  setToken,
  removeToken,
  getRefreshToken,
  setRefreshToken,
  removeRefreshToken,
  setRole,
  isAuthenticated,
  getUserIdFromToken,
  getUserRoleFromToken,
} from '@/lib/auth'

// ── Helpers ────────────────────────────────────────────────────────────────────

/**
 * Build a minimal JWT with the given payload (no signature verification —
 * auth.ts only base64-decodes the payload, it never verifies the signature).
 */
function buildJwt(payload: Record<string, unknown>): string {
  const header  = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body    = btoa(JSON.stringify(payload))
  return `${header}.${body}.fake-signature`
}

const CLIENT_TOKEN = buildJwt({ sub: 'user-uuid-123', role: 'CLIENT' })
const ADMIN_TOKEN  = buildJwt({ sub: 'admin-uuid-456', role: 'ADMIN' })

// ── Setup / teardown ──────────────────────────────────────────────────────────

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})
afterEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})

// ── Access token ──────────────────────────────────────────────────────────────

describe('getToken / setToken / removeToken', () => {
  it('returns null when no token is stored', () => {
    expect(getToken()).toBeNull()
  })

  it('stores and retrieves the access token', () => {
    setToken(CLIENT_TOKEN)
    expect(getToken()).toBe(CLIENT_TOKEN)
  })

  it('removeToken clears the access token', () => {
    setToken(CLIENT_TOKEN)
    removeToken()
    expect(getToken()).toBeNull()
  })

  it('removeToken also clears the role', () => {
    setToken(CLIENT_TOKEN)
    setRole('CLIENT')
    removeToken()
    expect(getToken()).toBeNull()
    // getRefreshToken is always null (HttpOnly cookie)
    expect(getRefreshToken()).toBeNull()
  })

  it('migrates a legacy localStorage token to sessionStorage', () => {
    // Simulate a token left behind from before the migration
    localStorage.setItem('token', CLIENT_TOKEN)
    // getToken() should migrate it transparently
    expect(getToken()).toBe(CLIENT_TOKEN)
    // After migration, it should be in sessionStorage, not localStorage
    expect(sessionStorage.getItem('token')).toBe(CLIENT_TOKEN)
    expect(localStorage.getItem('token')).toBeNull()
  })
})

// ── Refresh token (now HttpOnly cookie, JS no-ops) ────────────────────────────

describe('getRefreshToken / setRefreshToken / removeRefreshToken', () => {
  it('getRefreshToken always returns null (token is in HttpOnly cookie)', () => {
    expect(getRefreshToken()).toBeNull()
  })

  it('setRefreshToken is a no-op (token is set by the server)', () => {
    setRefreshToken('rt-abc-123')
    // Still null — the HttpOnly cookie cannot be read by JS
    expect(getRefreshToken()).toBeNull()
    // Access token must remain untouched
    setToken(CLIENT_TOKEN)
    setRefreshToken('rt-abc-123')
    expect(getToken()).toBe(CLIENT_TOKEN)
    expect(getRefreshToken()).toBeNull()
  })

  it('removeRefreshToken is a no-op', () => {
    setToken(CLIENT_TOKEN)
    removeRefreshToken()
    // Access token must remain untouched
    expect(getToken()).toBe(CLIENT_TOKEN)
    expect(getRefreshToken()).toBeNull()
  })
})

// ── isAuthenticated ───────────────────────────────────────────────────────────

describe('isAuthenticated', () => {
  it('returns false when not logged in', () => {
    expect(isAuthenticated()).toBe(false)
  })

  it('returns true after setToken', () => {
    setToken(CLIENT_TOKEN)
    expect(isAuthenticated()).toBe(true)
  })

  it('returns false after removeToken', () => {
    setToken(CLIENT_TOKEN)
    removeToken()
    expect(isAuthenticated()).toBe(false)
  })
})

// ── getUserIdFromToken ────────────────────────────────────────────────────────

describe('getUserIdFromToken', () => {
  it('returns null when no token', () => {
    expect(getUserIdFromToken()).toBeNull()
  })

  it('extracts the sub claim from a CLIENT token', () => {
    setToken(CLIENT_TOKEN)
    expect(getUserIdFromToken()).toBe('user-uuid-123')
  })

  it('extracts the sub claim from an ADMIN token', () => {
    setToken(ADMIN_TOKEN)
    expect(getUserIdFromToken()).toBe('admin-uuid-456')
  })
})

// ── getUserRoleFromToken ───────────────────────────────────────────────────────

describe('getUserRoleFromToken', () => {
  it('returns null when no token and no role in sessionStorage', () => {
    expect(getUserRoleFromToken()).toBeNull()
  })

  it('returns the role from sessionStorage when set via setRole', () => {
    setRole('CLIENT')
    // No JWT needed — role comes from sessionStorage
    expect(getUserRoleFromToken()).toBe('CLIENT')
  })

  it('returns ADMIN from sessionStorage when set via setRole', () => {
    setRole('ADMIN')
    expect(getUserRoleFromToken()).toBe('ADMIN')
  })

  it('falls back to JWT role claim when sessionStorage role is absent', () => {
    // Simulates a hard page reload after login (sessionStorage cleared)
    setToken(CLIENT_TOKEN)
    expect(getUserRoleFromToken()).toBe('CLIENT')
  })

  it('falls back to JWT ADMIN role when sessionStorage is absent', () => {
    setToken(ADMIN_TOKEN)
    expect(getUserRoleFromToken()).toBe('ADMIN')
  })

  it('returns null for a malformed token and no sessionStorage role', () => {
    // Put a bad token in sessionStorage directly (migration path)
    sessionStorage.setItem('token', 'not.a.jwt')
    expect(getUserRoleFromToken()).toBeNull()
  })
})
