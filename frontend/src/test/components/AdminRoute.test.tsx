import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import AdminRoute from '@/components/AdminRoute'
import { setToken, removeToken } from '@/lib/auth'

// ── Helpers ────────────────────────────────────────────────────────────────────

function buildJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body   = btoa(JSON.stringify(payload))
  return `${header}.${body}.sig`
}

const CLIENT_TOKEN = buildJwt({ sub: 'u1', role: 'CLIENT' })
const ADMIN_TOKEN  = buildJwt({ sub: 'u2', role: 'ADMIN' })

/** Render AdminRoute wrapping a protected page, with a router that also
 *  renders /login and /dashboard sentinel pages for redirect assertions. */
function renderWithRouter(initialPath = '/admin') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/login"     element={<p>Login Page</p>} />
        <Route path="/dashboard" element={<p>Dashboard Page</p>} />
        <Route
          path="/admin"
          element={
            <AdminRoute>
              <p>Admin Page</p>
            </AdminRoute>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => localStorage.clear())
afterEach(()  => { localStorage.clear(); vi.restoreAllMocks() })

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('AdminRoute', () => {
  it('redirects unauthenticated users to /login', () => {
    renderWithRouter()
    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('redirects authenticated CLIENT users to /dashboard', () => {
    setToken(CLIENT_TOKEN)
    renderWithRouter()
    expect(screen.getByText('Dashboard Page')).toBeInTheDocument()
  })

  it('renders children for authenticated ADMIN users', () => {
    setToken(ADMIN_TOKEN)
    renderWithRouter()
    expect(screen.getByText('Admin Page')).toBeInTheDocument()
  })

  it('does NOT render children for CLIENT users (strict role separation)', () => {
    setToken(CLIENT_TOKEN)
    renderWithRouter()
    expect(screen.queryByText('Admin Page')).not.toBeInTheDocument()
  })
})
