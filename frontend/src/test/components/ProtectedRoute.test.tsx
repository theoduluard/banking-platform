import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import ProtectedRoute from '@/components/ProtectedRoute'
import { setToken, removeToken } from '@/lib/auth'

// ── Helpers ────────────────────────────────────────────────────────────────────

function buildJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body   = btoa(JSON.stringify(payload))
  return `${header}.${body}.sig`
}

const CLIENT_TOKEN = buildJwt({ sub: 'u1', role: 'CLIENT' })
const ADMIN_TOKEN  = buildJwt({ sub: 'u2', role: 'ADMIN' })

function renderWithRouter(initialPath = '/dashboard') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/login" element={<p>Login Page</p>} />
        <Route path="/admin" element={<p>Admin Portal</p>} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <p>Client Dashboard</p>
            </ProtectedRoute>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => localStorage.clear())
afterEach(()  => { localStorage.clear() })

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ProtectedRoute', () => {
  it('redirects unauthenticated users to /login', () => {
    renderWithRouter()
    expect(screen.getByText('Login Page')).toBeInTheDocument()
  })

  it('renders children for authenticated CLIENT users', () => {
    setToken(CLIENT_TOKEN)
    renderWithRouter()
    expect(screen.getByText('Client Dashboard')).toBeInTheDocument()
  })

  it('redirects ADMIN users to /admin (strict role separation)', () => {
    setToken(ADMIN_TOKEN)
    renderWithRouter()
    expect(screen.getByText('Admin Portal')).toBeInTheDocument()
  })

  it('does NOT render client content for ADMIN users', () => {
    setToken(ADMIN_TOKEN)
    renderWithRouter()
    expect(screen.queryByText('Client Dashboard')).not.toBeInTheDocument()
  })

  it('redirects to /login after logout (token removed)', () => {
    setToken(CLIENT_TOKEN)
    renderWithRouter()
    expect(screen.getByText('Client Dashboard')).toBeInTheDocument()

    // Simulate logout
    removeToken()
    // Re-render to reflect the cleared token
    renderWithRouter()
    expect(screen.getAllByText('Login Page').length).toBeGreaterThan(0)
  })
})
