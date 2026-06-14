import { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { removeToken, getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import axios from 'axios'
import { cn } from '@/lib/utils'
import Logo from './Logo'
import {
  ArrowLeftRight, CalendarClock, PlusCircle, LogOut, LayoutDashboard,
  Users, Bell, MessageSquare, Settings, CreditCard, TrendingUp,
  Calculator, BarChart3, FileDown, Menu, X,
} from 'lucide-react'

const navLinks = [
  { to: '/dashboard',           label: 'Tableau de bord',  icon: LayoutDashboard },
  { to: '/transfer',            label: 'Virement',          icon: ArrowLeftRight },
  { to: '/scheduled-transfers', label: 'Programmés',        icon: CalendarClock },
  { to: '/beneficiaries',       label: 'Bénéficiaires',     icon: Users },
  { to: '/cards',               label: 'Cartes',            icon: CreditCard },
  { to: '/loans',               label: 'Prêts',             icon: Calculator },
  { to: '/analytics',           label: 'Analyses',          icon: BarChart3 },
  { to: '/currency',            label: 'Devises',           icon: TrendingUp },
  { to: '/documents',           label: 'Documents',         icon: FileDown },
  { to: '/messages',            label: 'Messages',          icon: Bell },
  { to: '/requests',            label: 'Demandes',          icon: MessageSquare },
  { to: '/accounts/new',        label: 'Nouveau compte',    icon: PlusCircle },
  { to: '/settings',            label: 'Paramètres',        icon: Settings },
]

// ── Sidebar content (shared between desktop & mobile drawer) ──────────────────
function SidebarContent({
  onClose,
  unreadCount,
  onLogout,
}: {
  onClose?: () => void
  unreadCount: number
  onLogout: () => void
}) {
  const { pathname } = useLocation()

  return (
    <div className="flex h-full flex-col">
      {/* Logo */}
      <div className="flex items-center justify-between px-4 py-5">
        <Link to="/dashboard" onClick={onClose}>
          <Logo size={32} />
        </Link>
        {onClose && (
          <button
            onClick={onClose}
            className="rounded-lg p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground md:hidden"
          >
            <X size={18} />
          </button>
        )}
      </div>

      {/* Divider */}
      <div className="mx-4 h-px bg-border/60" />

      {/* Nav links */}
      <nav className="flex-1 overflow-y-auto px-3 py-3">
        <ul className="space-y-0.5">
          {navLinks.map(({ to, label, icon: Icon }) => {
            const active      = pathname === to || (to !== '/dashboard' && pathname.startsWith(to))
            const isMessages  = to === '/messages'
            const badge       = isMessages && unreadCount > 0

            return (
              <li key={to}>
                <Link
                  to={to}
                  onClick={onClose}
                  className={cn(
                    'relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                    active
                      ? 'bg-primary/10 text-primary'
                      : 'text-muted-foreground hover:bg-muted/60 hover:text-foreground',
                  )}
                >
                  <Icon
                    size={16}
                    strokeWidth={active ? 2.2 : 1.8}
                    className="shrink-0"
                  />
                  <span className="flex-1 truncate">{label}</span>
                  {badge && (
                    <span className="flex size-5 items-center justify-center rounded-full bg-primary text-[10px] font-bold text-primary-foreground">
                      {unreadCount > 9 ? '9+' : unreadCount}
                    </span>
                  )}
                </Link>
              </li>
            )
          })}
        </ul>
      </nav>

      {/* Divider */}
      <div className="mx-4 h-px bg-border/60" />

      {/* Logout */}
      <div className="px-3 py-3">
        <button
          onClick={onLogout}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
        >
          <LogOut size={16} strokeWidth={1.8} className="shrink-0" />
          <span>Déconnexion</span>
        </button>
      </div>
    </div>
  )
}

// ── Main export ───────────────────────────────────────────────────────────────
export default function Navbar() {
  const navigate = useNavigate()
  const userId   = getUserIdFromToken()
  const [mobileOpen, setMobileOpen] = useState(false)

  const { data: unread } = useQuery<{ count: number }>({
    queryKey: ['messages-unread', userId],
    queryFn:  () => api.get<{ count: number }>('/api/v1/messages/unread-count').then(r => r.data),
    enabled:  !!userId,
    refetchInterval: 60_000,
    // Don't retry on error — if the messaging service is down it will be polled
    // again at the next interval (60 s) without spamming the console.
    retry: false,
    // Never throw into an error boundary for this non-critical background poll.
    throwOnError: false,
  })
  const unreadCount = unread?.count ?? 0

  async function handleLogout() {
    try {
      await axios.post(
        `${import.meta.env.VITE_API_URL}/api/v1/auth/logout`,
        {},
        { withCredentials: true },
      )
    } catch { /* best-effort */ }
    removeToken()
    navigate('/login')
  }

  return (
    <>
      {/* ── Desktop sidebar ───────────────────────────────────────────────── */}
      <aside className="hidden md:flex md:w-56 md:flex-col md:border-r md:border-border/60 md:bg-card">
        <SidebarContent unreadCount={unreadCount} onLogout={handleLogout} />
      </aside>

      {/* ── Mobile top bar ────────────────────────────────────────────────── */}
      <header className="flex items-center justify-between border-b border-border/60 bg-card px-4 py-3 md:hidden">
        <Link to="/dashboard">
          <Logo size={28} />
        </Link>
        <button
          onClick={() => setMobileOpen(true)}
          className="rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-foreground"
          aria-label="Ouvrir le menu"
        >
          <Menu size={20} />
        </button>
      </header>

      {/* ── Mobile drawer overlay ─────────────────────────────────────────── */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 md:hidden">
          {/* Backdrop */}
          <div
            className="absolute inset-0 bg-black/40 backdrop-blur-sm"
            onClick={() => setMobileOpen(false)}
          />
          {/* Drawer */}
          <aside className="absolute inset-y-0 left-0 w-64 bg-card shadow-2xl">
            <SidebarContent
              onClose={() => setMobileOpen(false)}
              unreadCount={unreadCount}
              onLogout={handleLogout}
            />
          </aside>
        </div>
      )}
    </>
  )
}
