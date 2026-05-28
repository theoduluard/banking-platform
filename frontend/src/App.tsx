import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from '@/components/ui/sonner'

import ProtectedRoute    from '@/components/ProtectedRoute'
import AdminRoute        from '@/components/AdminRoute'
import Layout            from '@/components/Layout'
import AdminLayout       from '@/components/AdminLayout'

import LoginPage         from '@/pages/LoginPage'
import RegisterPage      from '@/pages/RegisterPage'
import DashboardPage     from '@/pages/DashboardPage'
import AccountDetailPage from '@/pages/AccountDetailPage'
import NewAccountPage    from '@/pages/NewAccountPage'
import TransferPage      from '@/pages/TransferPage'

import AdminDashboardPage    from '@/pages/admin/AdminDashboardPage'
import AdminUsersPage        from '@/pages/admin/AdminUsersPage'
import AdminAccountsPage     from '@/pages/admin/AdminAccountsPage'
import AdminTransactionsPage from '@/pages/admin/AdminTransactionsPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          {/* Public */}
          <Route path="/login"    element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Protected — wrapped in Layout (Navbar + container) */}
          <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
            <Route path="/dashboard"    element={<DashboardPage />} />
            <Route path="/accounts/new" element={<NewAccountPage />} />
            <Route path="/accounts/:id" element={<AccountDetailPage />} />
            <Route path="/transfer"     element={<TransferPage />} />
          </Route>

          {/* Admin — ADMIN role required, own sidebar layout */}
          <Route element={<AdminRoute><AdminLayout /></AdminRoute>}>
            <Route path="/admin"              element={<AdminDashboardPage />} />
            <Route path="/admin/users"        element={<AdminUsersPage />} />
            <Route path="/admin/accounts"     element={<AdminAccountsPage />} />
            <Route path="/admin/transactions" element={<AdminTransactionsPage />} />
          </Route>

          {/* Default redirect */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
      <Toaster richColors position="top-right" />
    </QueryClientProvider>
  )
}
