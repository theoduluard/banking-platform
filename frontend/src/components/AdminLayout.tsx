import { Outlet, Link, useNavigate, useLocation } from 'react-router-dom'
import { removeToken } from '@/lib/auth'
import { Button, buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import Logo from './Logo'
import {
  LayoutDashboard,
  Users,
  CreditCard,
  ArrowLeftRight,
  LogOut,
  ShieldCheck,
  BarChart2,
  ExternalLink,
  Bell,
  MessageSquare,
} from 'lucide-react'

const sidebarLinks = [
  { to: '/admin',              label: 'Vue d\'ensemble', icon: LayoutDashboard, exact: true },
  { to: '/admin/users',        label: 'Utilisateurs',    icon: Users },
  { to: '/admin/accounts',     label: 'Comptes',         icon: CreditCard },
  { to: '/admin/transactions', label: 'Transactions',    icon: ArrowLeftRight },
  { to: '/admin/messages',     label: 'Messages',        icon: Bell },
  { to: '/admin/requests',     label: 'Demandes',        icon: MessageSquare },
]

export default function AdminLayout() {
  const navigate     = useNavigate()
  const { pathname } = useLocation()

  function handleLogout() {
    removeToken()
    navigate('/login')
  }

  return (
    <div className="flex min-h-screen bg-background">

      {/* ── Sidebar ─────────────────────────────────────────────────────── */}
      <aside className="flex w-60 shrink-0 flex-col border-r border-border/60 bg-card">
        {/* Logo + badge */}
        <div className="flex h-16 items-center gap-2.5 border-b border-border/60 px-5">
          <Logo size={28} />
          <span className="flex items-center gap-1 rounded-md bg-primary/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-primary">
            <ShieldCheck size={10} />
            Admin
          </span>
        </div>

        {/* Nav — Link is the flex container directly (no asChild) */}
        <nav className="flex flex-col gap-0.5 p-3">
          {sidebarLinks.map(({ to, label, icon: Icon, exact }) => {
            const active = exact ? pathname === to : pathname.startsWith(to)
            return (
              <Link
                key={to}
                to={to}
                className={cn(
                  buttonVariants({ variant: 'ghost', size: 'sm' }),
                  'h-9 w-full justify-start gap-2.5 px-3 text-sm font-medium',
                  active
                    ? 'bg-primary/10 text-primary hover:bg-primary/15'
                    : 'text-muted-foreground hover:text-foreground hover:bg-muted/60',
                )}
              >
                <Icon size={15} strokeWidth={active ? 2.2 : 1.8} />
                <span>{label}</span>
              </Link>
            )
          })}
        </nav>

        {/* Spacer */}
        <div className="flex-1" />

        {/* External tools */}
        <div className="border-t border-border/60 p-3">
          <p className="mb-1.5 px-3 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground/60">
            Outils
          </p>
          <a
            href={import.meta.env.VITE_GRAFANA_URL ?? 'http://localhost:3000'}
            target="_blank"
            rel="noopener noreferrer"
            className={cn(
              'flex h-9 w-full items-center gap-2.5 rounded-md px-3 text-sm font-medium',
              'text-muted-foreground hover:bg-muted/60 hover:text-foreground transition-colors',
            )}
          >
            <BarChart2 size={15} strokeWidth={1.8} />
            Grafana
            <ExternalLink size={11} className="ml-auto opacity-50" />
          </a>
        </div>

        {/* Bottom actions */}
        <div className="flex flex-col gap-0.5 border-t border-border/60 p-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={handleLogout}
            className="h-9 justify-start gap-2.5 px-3 text-sm text-muted-foreground hover:text-destructive"
          >
            <LogOut size={14} />
            <span>Déconnexion</span>
          </Button>
        </div>
      </aside>

      {/* ── Main content ─────────────────────────────────────────────────── */}
      <main className="flex-1 overflow-auto">
        <div className="mx-auto max-w-6xl space-y-6 p-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
