import { Link, useNavigate, useLocation } from 'react-router-dom'
import { removeToken } from '@/lib/auth'
import { Button } from '@/components/ui/button'
import { Landmark, ArrowLeftRight, PlusCircle, LogOut } from 'lucide-react'
import { cn } from '@/lib/utils'

const navLinks = [
  { to: '/dashboard', label: 'Comptes', icon: Landmark },
  { to: '/transfer',  label: 'Virement', icon: ArrowLeftRight },
  { to: '/accounts/new', label: 'Nouveau compte', icon: PlusCircle },
]

export default function Navbar() {
  const navigate  = useNavigate()
  const { pathname } = useLocation()

  function handleLogout() {
    removeToken()
    navigate('/login')
  }

  return (
    <header className="sticky top-0 z-50 border-b border-border bg-card/80 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
        <Link to="/dashboard" className="flex items-center gap-2 font-semibold text-foreground">
          <Landmark size={20} />
          <span>Solaris Bank</span>
        </Link>

        <nav className="flex items-center gap-1">
          {navLinks.map(({ to, label, icon: Icon }) => (
            <Button
              key={to}
              variant="ghost"
              size="sm"
              asChild
              className={cn(
                'gap-1.5 text-muted-foreground',
                pathname === to && 'bg-accent text-accent-foreground',
              )}
            >
              <Link to={to}>
                <Icon size={15} />
                {label}
              </Link>
            </Button>
          ))}

          <Button variant="ghost" size="sm" onClick={handleLogout}
            className="ml-2 gap-1.5 text-muted-foreground">
            <LogOut size={15} />
            Déconnexion
          </Button>
        </nav>
      </div>
    </header>
  )
}
