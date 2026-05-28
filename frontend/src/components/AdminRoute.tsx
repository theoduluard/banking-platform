import { Navigate } from 'react-router-dom'
import { isAuthenticated, getUserRoleFromToken } from '@/lib/auth'

interface Props {
  children: React.ReactNode
}

/**
 * Protects admin routes.
 * - Unauthenticated users → /login
 * - Authenticated non-admin users → /dashboard
 */
export default function AdminRoute({ children }: Props) {
  if (!isAuthenticated()) return <Navigate to="/login" replace />
  if (getUserRoleFromToken() !== 'ADMIN') return <Navigate to="/dashboard" replace />
  return <>{children}</>
}
