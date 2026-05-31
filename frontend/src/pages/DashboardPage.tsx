import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { PlusCircle, ArrowRight, ArrowLeftRight, CreditCard, PiggyBank, TrendingUp } from 'lucide-react'

function formatAmount(amount: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount)
}

function AccountTypeIcon({ type }: { type: Account['type'] }) {
  return type === 'CHECKING'
    ? <CreditCard size={16} className="text-primary" />
    : <PiggyBank  size={16} className="text-[oklch(0.78_0.145_82)]" />
}

function AccountCard({ account }: { account: Account }) {
  const isChecking = account.type === 'CHECKING'

  return (
    <Link to={`/accounts/${account.id}`} className="group block">
      <Card className="relative overflow-hidden transition-all duration-200 hover:shadow-md hover:-translate-y-0.5">
        {/* Accent stripe */}
        <div className={`absolute inset-y-0 left-0 w-1 rounded-l-xl ${
          isChecking ? 'bg-primary' : 'bg-[oklch(0.78_0.145_82)]'
        }`} />

        <CardHeader className="pb-3 pl-6">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-2.5">
              <div className={`flex size-9 items-center justify-center rounded-lg ${
                isChecking ? 'bg-primary/10' : 'bg-[oklch(0.78_0.145_82)]/15'
              }`}>
                <AccountTypeIcon type={account.type} />
              </div>
              <div>
                <CardTitle className="text-sm font-semibold">
                  {isChecking ? 'Compte courant' : 'Compte épargne'}
                </CardTitle>
                <p className="mt-0.5 font-mono text-[10px] tracking-wide text-muted-foreground">
                  {account.iban.slice(0, 8)}···{account.iban.slice(-4)}
                </p>
              </div>
            </div>
            <Badge
              variant={account.status === 'ACTIVE' ? 'default' : 'secondary'}
              className="text-[10px]"
            >
              {account.status === 'ACTIVE' ? 'Actif' : 'Fermé'}
            </Badge>
          </div>
        </CardHeader>

        <CardContent className="flex items-end justify-between pl-6 pt-0">
          <div>
            <p className="text-xs text-muted-foreground">Solde disponible</p>
            <p className="mt-0.5 text-2xl font-bold tabular-nums tracking-tight">
              {formatAmount(account.balance, account.currency)}
            </p>
          </div>
          {/* Span styled as ghost button — no asChild needed */}
          <span className={cn(
            buttonVariants({ variant: 'ghost', size: 'sm' }),
            'gap-1.5 text-xs text-muted-foreground group-hover:text-primary pointer-events-none',
          )}>
            Détails
            <ArrowRight size={13} className="shrink-0 transition-transform group-hover:translate-x-0.5" />
          </span>
        </CardContent>
      </Card>
    </Link>
  )
}

export default function DashboardPage() {
  const userId = getUserIdFromToken()

  const { data: accounts, isLoading, isError } = useQuery<Account[]>({
    queryKey: ['accounts', userId],
    queryFn:  () => api
      .get<Account[]>('/api/v1/accounts', { headers: { 'X-User-Id': userId } })
      .then(r => r.data),
    enabled: !!userId,
  })

  const activeAccounts = accounts?.filter(a => a.status === 'ACTIVE') ?? []
  const totalBalance   = activeAccounts.reduce((sum, a) => sum + a.balance, 0)
  const currency       = accounts?.[0]?.currency ?? 'EUR'

  return (
    <div className="space-y-8">

      {/* ── Hero balance ────────────────────────────────────────────────────── */}
      <div className="relative overflow-hidden rounded-2xl bg-primary px-8 py-7 text-primary-foreground shadow-lg">
        {/* Background decoration */}
        <div className="pointer-events-none absolute inset-0 overflow-hidden">
          <div className="absolute -right-12 -top-12 h-56 w-56 rounded-full bg-white/5" />
          <div className="absolute -bottom-8 right-24 h-32 w-32 rounded-full bg-white/5" />
        </div>

        <div className="relative z-10 flex items-center justify-between">
          <div>
            <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-widest text-primary-foreground/60">
              <TrendingUp size={12} /> Solde total
            </p>
            {isLoading
              ? <Skeleton className="mt-2 h-10 w-48 bg-white/10" />
              : <p className="mt-1 text-4xl font-bold tabular-nums tracking-tight">
                  {formatAmount(totalBalance, currency)}
                </p>
            }
            <p className="mt-2 text-xs text-primary-foreground/50">
              {activeAccounts.length} compte{activeAccounts.length > 1 ? 's' : ''} actif{activeAccounts.length > 1 ? 's' : ''}
            </p>
          </div>

          <div className="flex flex-col gap-2 sm:flex-row">
            {/* Link styled directly as button — no asChild */}
            <Link
              to="/transfer"
              className={cn(buttonVariants({ variant: 'secondary', size: 'sm' }), 'gap-1.5 bg-white/15 text-white hover:bg-white/25 border-0')}
            >
              <ArrowLeftRight size={14} />
              <span>Virer</span>
            </Link>
            <Link
              to="/accounts/new"
              className={cn(buttonVariants({ variant: 'secondary', size: 'sm' }), 'gap-1.5 bg-white/15 text-white hover:bg-white/25 border-0')}
            >
              <PlusCircle size={14} />
              <span>Nouveau</span>
            </Link>
          </div>
        </div>
      </div>

      {/* ── Accounts ────────────────────────────────────────────────────────── */}
      <div>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-base font-semibold">Mes comptes</h2>
          <Link
            to="/accounts/new"
            className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }), 'gap-1 text-xs text-muted-foreground')}
          >
            <PlusCircle size={13} />
            <span>Ouvrir un compte</span>
          </Link>
        </div>

        {isLoading && (
          <div className="grid gap-4 sm:grid-cols-2">
            {[1, 2].map(i => (
              <Card key={i}><CardContent className="p-6"><Skeleton className="h-20 w-full" /></CardContent></Card>
            ))}
          </div>
        )}

        {isError && (
          <Card>
            <CardContent className="py-8 text-center text-sm text-destructive">
              Impossible de charger vos comptes.
            </CardContent>
          </Card>
        )}

        {!isLoading && accounts?.length === 0 && (
          <Card className="border-dashed">
            <CardContent className="flex flex-col items-center gap-4 py-12 text-center">
              <div className="flex size-14 items-center justify-center rounded-2xl bg-muted">
                <CreditCard size={24} className="text-muted-foreground" />
              </div>
              <div>
                <p className="font-medium">Aucun compte pour l'instant</p>
                <p className="mt-1 text-sm text-muted-foreground">Ouvrez votre premier compte en quelques secondes.</p>
              </div>
              <Link
                to="/accounts/new"
                className={cn(buttonVariants({ size: 'sm' }), 'gap-1.5')}
              >
                <PlusCircle size={14} />
                <span>Ouvrir un compte</span>
              </Link>
            </CardContent>
          </Card>
        )}

        {!isLoading && accounts && accounts.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2">
            {accounts.map(a => <AccountCard key={a.id} account={a} />)}
          </div>
        )}
      </div>
    </div>
  )
}
