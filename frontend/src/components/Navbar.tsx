import { Link, useNavigate, useLocation } from 'react-router-dom'
import { removeToken } from '@/lib/auth'
import { Button, buttonVariants } from '@/components/ui/button'
import { ArrowLeftRight, PlusCircle, LogOut, LayoutDashboard, Users } from 'lucide-react'
import { cn } from '@/lib/utils'
import Logo from './Logo'

const navLinks = [
  { to: '/dashboard',       label: 'Tableau de bord', icon: LayoutDashboard },
  { to: '/transfer',        label: 'Virement',         icon: ArrowLeftRight },
  { to: '/beneficiaries',   label: 'Bénéficiaires',    icon: Users },
  { to: '/accounts/new',    label: 'Nouveau compte',   icon: PlusCircle },
]

export default function Navbar() {
  const navigate     = useNavigate()
  const { pathname } = useLocation()

  function handleLogout() {
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
            const active = pathname === to
            return (
              <Link
                key={to}
                to={to}
                className={cn(
                  buttonVariants({ variant: 'ghost', size: 'sm' }),
                  'h-9 gap-2 px-3 text-sm font-medium transition-colors',
                  active
                    ? 'bg-primary/10 text-primary hover:bg-primary/15'
                    : 'text-muted-foreground hover:text-foreground',
                )}
              >
                <Icon size={15} strokeWidth={active ? 2.2 : 1.8} />
                <span>{label}</span>
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
