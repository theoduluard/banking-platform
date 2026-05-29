import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import {
  getToken,
  setToken,
  removeToken,
  getRefreshToken,
  setRefreshToken,
  removeRefreshToken,
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

beforeEach(() => localStorage.clear())
afterEach(()  => localStorage.clear())

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

  it('removeToken also clears the refresh token', () => {
    setToken(CLIENT_TOKEN)
    setRefreshToken('some-refresh-token')
    removeToken()
    expect(getToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })
})

// ── Refresh token ─────────────────────────────────────────────────────────────

describe('getRefreshToken / setRefreshToken / removeRefreshToken', () => {
  it('returns null when no refresh token is stored', () => {
    expect(getRefreshToken()).toBeNull()
  })

  it('stores and retrieves the refresh token independently of the access token', () => {
    setToken(CLIENT_TOKEN)
    setRefreshToken('rt-abc-123')
    expect(getRefreshToken()).toBe('rt-abc-123')
    // Access token must remain untouched
    expect(getToken()).toBe(CLIENT_TOKEN)
  })

  it('removeRefreshToken only removes the refresh token', () => {
    setToken(CLIENT_TOKEN)
    setRefreshToken('rt-abc-123')
    removeRefreshToken()
    expect(getRefreshToken()).toBeNull()
    expect(getToken()).toBe(CLIENT_TOKEN)
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

// ── getUserRoleFromToken ──────────────────────────────────────────────────────

describe('getUserRoleFromToken', () => {
  it('returns null when no token', () => {
    expect(getUserRoleFromToken()).toBeNull()
  })

  it('returns CLIENT for a client token', () => {
    setToken(CLIENT_TOKEN)
    expect(getUserRoleFromToken()).toBe('CLIENT')
  })

  it('returns ADMIN for an admin token', () => {
    setToken(ADMIN_TOKEN)
    expect(getUserRoleFromToken()).toBe('ADMIN')
  })

  it('returns null for a malformed token', () => {
    localStorage.setItem('token', 'not.a.jwt')
    // decodePayload catches the error and returns null → getUserRoleFromToken returns null
    expect(getUserRoleFromToken()).toBeNull()
  })
})
