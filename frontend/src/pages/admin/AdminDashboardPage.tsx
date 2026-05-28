import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import type { AdminUser, AdminAccount, AdminTransaction, Page } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Users, CreditCard, ArrowLeftRight, TrendingUp, ShieldCheck } from 'lucide-react'

function StatCard({
  title,
  value,
  sub,
  icon: Icon,
  loading,
  color = 'primary',
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

function formatAmount(n: number) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(n)
}

export default function AdminDashboardPage() {
  const { data: users, isLoading: loadingUsers } = useQuery<AdminUser[]>({
    queryKey: ['admin', 'users'],
    queryFn: () => api.get<AdminUser[]>('/api/v1/admin/users').then(r => r.data),
  })

  const { data: accountsPage, isLoading: loadingAccounts } = useQuery<Page<AdminAccount>>({
    queryKey: ['admin', 'accounts'],
    queryFn: () => api.get<Page<AdminAccount>>('/api/v1/admin/accounts?size=1000').then(r => r.data),
  })

  const { data: txPage, isLoading: loadingTx } = useQuery<Page<AdminTransaction>>({
    queryKey: ['admin', 'transactions'],
    queryFn: () => api.get<Page<AdminTransaction>>('/api/v1/admin/transactions?size=1000').then(r => r.data),
  })

  const totalBalance = accountsPage?.content.reduce((s, a) => s + a.balance, 0) ?? 0
  const activeAccounts = accountsPage?.content.filter(a => a.status === 'ACTIVE').length ?? 0
  const activeUsers = users?.filter(u => u.isActive).length ?? 0
  const pendingTx = txPage?.content.filter(t => t.status === 'PENDING').length ?? 0

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <ShieldCheck size={20} />
        </div>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Administration</h1>
          <p className="text-sm text-muted-foreground">Vue d'ensemble de la plateforme Solaris Bank</p>
        </div>
      </div>

      {/* KPI cards */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          title="Utilisateurs actifs"
          value={activeUsers}
          sub={`${users?.length ?? 0} au total`}
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
          title="Transactions en attente"
          value={pendingTx}
          sub={`${txPage?.totalElements ?? 0} au total`}
          icon={ArrowLeftRight}
          loading={loadingTx}
          color="amber"
        />
      </div>

      {/* Recent users */}
      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="text-sm font-semibold">Derniers inscrits</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loadingUsers
            ? <div className="space-y-3 p-4">{[1,2,3].map(i => <Skeleton key={i} className="h-10 w-full" />)}</div>
            : (
              <div className="divide-y divide-border">
                {users?.slice(0, 5).map(u => (
                  <div key={u.userId} className="flex items-center justify-between px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="flex size-8 items-center justify-center rounded-full bg-muted text-xs font-semibold uppercase text-muted-foreground">
                        {u.firstname[0]}{u.lastname[0]}
                      </div>
                      <div>
                        <p className="text-sm font-medium">{u.firstname} {u.lastname}</p>
                        <p className="text-xs text-muted-foreground">{u.email}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium ${
                        u.role === 'ADMIN'
                          ? 'border-primary/30 bg-primary/10 text-primary'
                          : 'border-border bg-muted text-muted-foreground'
                      }`}>
                        {u.role}
                      </span>
                      <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium ${
                        u.isActive
                          ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                          : 'border-red-200 bg-red-50 text-red-700'
                      }`}>
                        {u.isActive ? 'Actif' : 'Inactif'}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )
          }
        </CardContent>
      </Card>
    </div>
  )
}
