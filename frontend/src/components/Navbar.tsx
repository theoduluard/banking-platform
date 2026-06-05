import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { removeToken, getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import axios from 'axios'
import { Button, buttonVariants } from '@/components/ui/button'
import { ArrowLeftRight, CalendarClock, PlusCircle, LogOut, LayoutDashboard, Users, Bell, MessageSquare } from 'lucide-react'
import { cn } from '@/lib/utils'
import Logo from './Logo'

const navLinks = [
  { to: '/dashboard',            label: 'Tableau de bord',   icon: LayoutDashboard },
  { to: '/transfer',             label: 'Virement',           icon: ArrowLeftRight },
  { to: '/scheduled-transfers',  label: 'Programmés',          icon: CalendarClock },
  { to: '/beneficiaries',        label: 'Bénéficiaires',       icon: Users },
  { to: '/messages',        label: 'Messages',          icon: Bell },
  { to: '/requests',        label: 'Demandes',          icon: MessageSquare },
  { to: '/accounts/new',    label: 'Nouveau compte',   icon: PlusCircle },
]

export default function Navbar() {
  const navigate     = useNavigate()
  const { pathname } = useLocation()
  const userId       = getUserIdFromToken()

  const { data: unread } = useQuery<{ count: number }>({
    queryKey: ['messages-unread', userId],
    queryFn:  () => api.get<{ count: number }>('/api/v1/messages/unread-count').then(r => r.data),
    enabled:  !!userId,
    refetchInterval: 60_000,
  })

  async function handleLogout() {
    // Revoke the refresh token on the server before clearing local state.
    // The HttpOnly cookie is sent automatically (withCredentials) so no token
    // needs to be passed in the body.  Errors are silently ignored so a network
    // blip never leaves the user stuck on the current page.
    try {
      await axios.post(
        `${import.meta.env.VITE_API_URL}/api/v1/auth/logout`,
        {},
        { withCredentials: true },
      )
    } catch {
      // Best-effort — always clear local state regardless
    }
    removeToken()
    navigate('/login')
  }

  return (
    <header className="sticky top-0 z-50 border-b border-border/60 bg-card/90 backdrop-blur-md shadow-sm">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">

        {/* Brand */}
        <Link to="/dashboard" className="shrink-0">
          <Logo size={32} />
        </Link>

        {/* Nav links — Link is the flex container directly (no asChild) */}
        <nav className="hidden items-center gap-1 md:flex">
          {navLinks.map(({ to, label, icon: Icon }) => {
            const active      = pathname === to
            const isMessages  = to === '/messages'
            const badge       = isMessages && (unread?.count ?? 0) > 0

            return (
              <Link
                key={to}
                to={to}
                className={cn(
                  buttonVariants({ variant: 'ghost', size: 'sm' }),
                  'relative h-9 gap-2 px-3 text-sm font-medium transition-colors',
                  active
                    ? 'bg-primary/10 text-primary hover:bg-primary/15'
                    : 'text-muted-foreground hover:text-foreground',
                )}
              >
                <Icon size={15} strokeWidth={active ? 2.2 : 1.8} />
                <span>{label}</span>
                {badge && (
                  <span className="absolute -right-0.5 -top-0.5 flex size-4 items-center justify-center rounded-full bg-primary text-[9px] font-bold text-primary-foreground">
                    {unread!.count > 9 ? '9+' : unread!.count}
                  </span>
                )}
              </Link>
            )
          })}
        </nav>

        {/* Logout */}
        <Button
          variant="ghost"
          size="sm"
          onClick={handleLogout}
          className="h-9 gap-2 px-3 text-sm text-muted-foreground hover:text-destructive"
        >
          <LogOut size={15} />
          <span className="hidden sm:inline shrink-0">Déconnexion</span>
        </Button>
      </div>
    </header>
  )
}
