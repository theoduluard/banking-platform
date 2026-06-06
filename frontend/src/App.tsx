import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from '@/components/ui/sonner'

import ProtectedRoute    from '@/components/ProtectedRoute'
import AdminRoute        from '@/components/AdminRoute'
import Layout            from '@/components/Layout'
import AdminLayout       from '@/components/AdminLayout'

import LoginPage            from '@/pages/LoginPage'
import OtpVerificationPage  from '@/pages/OtpVerificationPage'
import RegisterPage         from '@/pages/RegisterPage'
import VerifyEmailPage      from '@/pages/VerifyEmailPage'
import ForgotPasswordPage   from '@/pages/ForgotPasswordPage'
import ResetPasswordPage    from '@/pages/ResetPasswordPage'
import KYCPage           from '@/pages/KYCPage'
import DashboardPage     from '@/pages/DashboardPage'
import AccountDetailPage from '@/pages/AccountDetailPage'
import NewAccountPage    from '@/pages/NewAccountPage'
import TransferPage            from '@/pages/TransferPage'
import ScheduledTransfersPage from '@/pages/ScheduledTransfersPage'
import BeneficiariesPage      from '@/pages/BeneficiariesPage'
import MessagesPage       from '@/pages/MessagesPage'
import RequestsPage       from '@/pages/RequestsPage'
import SettingsPage       from '@/pages/SettingsPage'
import VerifyNewEmailPage from '@/pages/VerifyNewEmailPage'

import AdminDashboardPage    from '@/pages/admin/AdminDashboardPage'
import AdminUsersPage        from '@/pages/admin/AdminUsersPage'
import AdminAccountsPage     from '@/pages/admin/AdminAccountsPage'
import AdminTransactionsPage from '@/pages/admin/AdminTransactionsPage'
import AdminMessagesPage     from '@/pages/admin/AdminMessagesPage'
import AdminRequestsPage     from '@/pages/admin/AdminRequestsPage'

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
          <Route path="/login"            element={<LoginPage />} />
          <Route path="/verify-otp"       element={<OtpVerificationPage />} />
          <Route path="/register"         element={<RegisterPage />} />
          <Route path="/verify-email"     element={<VerifyEmailPage />} />
          <Route path="/forgot-password"  element={<ForgotPasswordPage />} />
          <Route path="/reset-password"   element={<ResetPasswordPage />} />
          <Route path="/verify-new-email" element={<VerifyNewEmailPage />} />

          {/* Onboarding — protected, no Layout (full-page flow) */}
          <Route path="/onboarding/kyc" element={<ProtectedRoute><KYCPage /></ProtectedRoute>} />

          {/* Protected — wrapped in Layout (Navbar + container) */}
          <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
            <Route path="/dashboard"    element={<DashboardPage />} />
            <Route path="/accounts/new" element={<NewAccountPage />} />
            <Route path="/accounts/:id" element={<AccountDetailPage />} />
            <Route path="/transfer"              element={<TransferPage />} />
            <Route path="/scheduled-transfers"  element={<ScheduledTransfersPage />} />
            <Route path="/beneficiaries"        element={<BeneficiariesPage />} />
            <Route path="/messages"      element={<MessagesPage />} />
            <Route path="/requests"      element={<RequestsPage />} />
            <Route path="/settings"      element={<SettingsPage />} />
          </Route>

          {/* Admin — ADMIN role required, own sidebar layout */}
          <Route element={<AdminRoute><AdminLayout /></AdminRoute>}>
            <Route path="/admin"              element={<AdminDashboardPage />} />
            <Route path="/admin/users"        element={<AdminUsersPage />} />
            <Route path="/admin/accounts"     element={<AdminAccountsPage />} />
            <Route path="/admin/transactions" element={<AdminTransactionsPage />} />
            <Route path="/admin/messages"     element={<AdminMessagesPage />} />
            <Route path="/admin/requests"     element={<AdminRequestsPage />} />
          </Route>

          {/* Default redirect */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
      <Toaster richColors position="top-right" />
    </QueryClientProvider>
  )
}
