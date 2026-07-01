import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '@/lib/api'
import type { AdminUser, AdminAccount, AdminTransaction, AdminLoan, Page } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import {
  Users, CreditCard, ArrowLeftRight, TrendingUp, ShieldCheck,
  MessageSquare, ArrowRight, AlertCircle, Banknote,
} from 'lucide-react'

// ── Local types ────────────────────────────────────────────────────────────────

interface RequestStats { openCount: number }

// ── Helpers ────────────────────────────────────────────────────────────────────

function formatAmount(n: number) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(n)
}

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium' }).format(new Date(iso))
}

// ── StatCard ───────────────────────────────────────────────────────────────────

function StatCard({
  title, value, sub, icon: Icon, loading, color = 'primary',
}: {
  title: string
  value: string | number
  sub?: string
  icon: React.ElementType
  loading?: boolean
  color?: 'primary' | 'emerald' | 'amber' | 'blue'
}) {
  const colorMap = {
    primary: 'bg-primary/10 text-primary',
    emerald: 'bg-emerald-100 text-emerald-700',
    amber:   'bg-amber-100 text-amber-700',
    blue:    'bg-blue-100 text-blue-700',
  }
  return (
    <Card>
      <CardContent className="flex items-start gap-4 pt-6">
        <div className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${colorMap[color]}`}>
          <Icon size={18} />
        </div>
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">{title}</p>
          {loading
            ? <Skeleton className="mt-1 h-7 w-20" />
            : <p className="mt-0.5 text-2xl font-bold tabular-nums">{value}</p>
          }
          {sub && !loading && <p className="mt-0.5 text-xs text-muted-foreground">{sub}</p>}
        </div>
      </CardContent>
    </Card>
  )
}

// ── ActionCard ─────────────────────────────────────────────────────────────────

function ActionCard({
  title, count, icon: Icon, href, color,
}: {
  title: string
  count: number
  icon: React.ElementType
  href: string
  color: 'primary' | 'emerald' | 'amber' | 'blue'
}) {
  const c = {
    primary: { icon: 'bg-primary/10 text-primary',         badge: 'bg-primary text-primary-foreground',  border: 'hover:border-primary/40' },
    emerald: { icon: 'bg-emerald-100 text-emerald-700',    badge: 'bg-emerald-600 text-white',            border: 'hover:border-emerald-300' },
    amber:   { icon: 'bg-amber-100 text-amber-700',        badge: 'bg-amber-500 text-white',              border: 'hover:border-amber-300' },
    blue:    { icon: 'bg-blue-100 text-blue-700',          badge: 'bg-blue-600 text-white',               border: 'hover:border-blue-300' },
  }[color]

  return (
    <Link to={href} className={cn('block rounded-xl border bg-card p-4 transition-colors', c.border)}>
      <div className="flex items-center justify-between">
        <div className={cn('flex size-9 items-center justify-center rounded-lg', c.icon)}>
          <Icon size={16} />
        </div>
        {count > 0 ? (
          <span className={cn('inline-flex h-6 min-w-[24px] items-center justify-center rounded-full px-1.5 text-xs font-bold', c.badge)}>
            {count}
          </span>
        ) : (
          <span className="text-xs font-medium text-emerald-600">OK</span>
        )}
      </div>
      <p className="mt-3 text-sm font-medium">{title}</p>
      <p className="mt-0.5 text-xs text-muted-foreground">
        {count > 0 ? `${count} en attente de traitement` : 'Aucune action requise'}
      </p>
    </Link>
  )
}

// ── Page ───────────────────────────────────────────────────────────────────────

export default function AdminDashboardPage() {

  // ── Recent users (5 most recent, sorted by date desc) — type FIXED to Page<AdminUser>
  const { data: usersPage, isLoading: loadingUsers } = useQuery<Page<AdminUser>>({
    queryKey: ['admin', 'users-recent'],
    queryFn:  () => api
      .get<Page<AdminUser>>('/api/v1/admin/users?size=5&sortBy=date&sortDir=desc')
      .then(r => r.data),
  })

  // ── Active users count (lightweight — only totalElements matters)
  const { data: activeUsersPage } = useQuery<Page<AdminUser>>({
    queryKey: ['admin', 'users-active-count'],
    queryFn:  () => api
      .get<Page<AdminUser>>('/api/v1/admin/users?size=1&status=ACTIVE')
      .then(r => r.data),
  })

  // ── Accounts stats (load up to 200 for balance aggregation)
  const { data: accountsPage, isLoading: loadingAccounts } = useQuery<Page<AdminAccount>>({
    queryKey: ['admin', 'accounts-dashboard'],
    queryFn:  () => api
      .get<Page<AdminAccount>>('/api/v1/admin/accounts?size=200')
      .then(r => r.data),
  })

  // ── Transaction count (lightweight — totalElements only)
  const { data: txPage, isLoading: loadingTx } = useQuery<Page<AdminTransaction>>({
    queryKey: ['admin', 'transactions-count'],
    queryFn:  () => api
      .get<Page<AdminTransaction>>('/api/v1/admin/transactions?size=1')
      .then(r => r.data),
  })

  // ── Pending accounts (action center, auto-refresh every 30 s)
  const { data: pendingAccounts } = useQuery<AdminAccount[]>({
    queryKey:       ['admin', 'accounts-pending'],
    queryFn:        () => api.get<AdminAccount[]>('/api/v1/admin/accounts/pending').then(r => r.data),
    refetchInterval: 30_000,
  })

  // ── Pending loans (action center)
  const { data: pendingLoans } = useQuery<AdminLoan[]>({
    queryKey:       ['admin', 'loans-pending'],
    queryFn:        () => api.get<AdminLoan[]>('/api/v1/admin/loans/pending').then(r => r.data),
    refetchInterval: 30_000,
  })

  // ── Open support requests (action center)
  const { data: requestStats } = useQuery<RequestStats>({
    queryKey:       ['admin', 'requests-stats'],
    queryFn:        () => api.get<RequestStats>('/api/v1/admin/requests/stats').then(r => r.data),
    refetchInterval: 30_000,
  })

  // ── Derived values — all use Page.content (previous version used users directly as array)
  const recentUsers    = usersPage?.content ?? []
  const totalUsers     = usersPage?.totalElements ?? 0
  const activeUsers    = activeUsersPage?.totalElements ?? 0
  const accounts       = accountsPage?.content ?? []
  const activeAccounts = accounts.filter(a => a.status === 'ACTIVE').length
  const totalBalance   = accounts.reduce((s, a) => s + a.balance, 0)
  const totalTx        = txPage?.totalElements ?? 0

  const pendingAccountsCount = pendingAccounts?.length ?? 0
  const pendingLoansCount    = pendingLoans?.length ?? 0
  const openRequestsCount    = requestStats?.openCount ?? 0
  const totalPending         = pendingAccountsCount + pendingLoansCount + openRequestsCount

  return (
    <div className="space-y-6">

      {/* ── Header ────────────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <ShieldCheck size={20} />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Administration</h1>
            <p className="text-sm text-muted-foreground">Vue d'ensemble de la plateforme Solaris Bank</p>
          </div>
        </div>
        {totalPending > 0 && (
          <div className="flex items-center gap-1.5 rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-700">
            <AlertCircle size={13} />
            {totalPending} action{totalPending > 1 ? 's' : ''} en attente
          </div>
        )}
      </div>

      {/* ── KPI cards ─────────────────────────────────────────────────────────── */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          title="Utilisateurs actifs"
          value={activeUsers}
          sub={`${totalUsers} inscrits au total`}
          icon={Users}
          loading={loadingUsers}
          color="primary"
        />
        <StatCard
          title="Comptes actifs"
          value={activeAccounts}
          sub={`${accountsPage?.totalElements ?? 0} au total`}
          icon={CreditCard}
          loading={loadingAccounts}
          color="emerald"
        />
        <StatCard
          title="Encours total"
          value={formatAmount(totalBalance)}
          sub="Tous comptes confondus"
          icon={TrendingUp}
          loading={loadingAccounts}
          color="blue"
        />
        <StatCard
          title="Transactions"
          value={totalTx.toLocaleString('fr-FR')}
          sub="Volume total enregistré"
          icon={ArrowLeftRight}
          loading={loadingTx}
          color="amber"
        />
      </div>

      {/* ── Actions requises ──────────────────────────────────────────────────── */}
      <div>
        <h2 className="mb-3 text-sm font-semibold">Actions requises</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          <ActionCard
            title="Comptes en attente"
            count={pendingAccountsCount}
            icon={CreditCard}
            href="/admin/accounts"
            color="blue"
          />
          <ActionCard
            title="Prêts en attente"
            count={pendingLoansCount}
            icon={Banknote}
            href="/admin/loans"
            color="amber"
          />
          <ActionCard
            title="Demandes ouvertes"
            count={openRequestsCount}
            icon={MessageSquare}
            href="/admin/requests"
            color="emerald"
          />
        </div>
      </div>

      {/* ── Derniers inscrits — sorted by date desc (FIXED) ───────────────────── */}
      <Card>
        <CardHeader className="border-b pb-4">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-semibold">Derniers inscrits</CardTitle>
            <Link
              to="/admin/users"
              className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }), 'h-7 gap-1 px-2 text-xs text-muted-foreground')}
            >
              Voir tous <ArrowRight size={12} />
            </Link>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {loadingUsers ? (
            <div className="space-y-3 p-4">
              {[1, 2, 3].map(i => <Skeleton key={i} className="h-12 w-full" />)}
            </div>
          ) : recentUsers.length === 0 ? (
            <div className="flex items-center justify-center py-10 text-sm text-muted-foreground">
              Aucun utilisateur
            </div>
          ) : (
            <div className="divide-y divide-border">
              {recentUsers.map(u => (
                <div key={u.userId} className="flex items-center gap-3 px-4 py-3">
                  <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold uppercase text-muted-foreground">
                    {u.firstname[0]}{u.lastname[0]}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{u.firstname} {u.lastname}</p>
                    <p className="truncate text-xs text-muted-foreground">{u.email}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {u.createdAt && (
                      <span className="hidden text-xs text-muted-foreground sm:block">
                        {formatDate(u.createdAt)}
                      </span>
                    )}
                    <span className={cn(
                      'inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium',
                      u.isActive
                        ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                        : 'border-red-200 bg-red-50 text-red-700',
                    )}>
                      {u.isActive ? 'Actif' : 'Inactif'}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

    </div>
  )
}
