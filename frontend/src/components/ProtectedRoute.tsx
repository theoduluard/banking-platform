import { Navigate } from 'react-router-dom'
import { isAuthenticated, getUserRoleFromToken } from '@/lib/auth'
import type { ReactNode } from 'react'

/**
 * Protects client routes.
 * - Unauthenticated users  → /login
 * - Admin accounts         → /admin  (strict role separation — admins have no client portal)
 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  if (!isAuthenticated()) return <Navigate to="/login" replace />
  if (getUserRoleFromToken() === 'ADMIN') return <Navigate to="/admin" replace />
  return <>{children}</>
}
