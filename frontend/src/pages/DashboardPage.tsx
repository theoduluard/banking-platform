import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { PlusCircle, ArrowRight, Landmark } from 'lucide-react'

function formatAmount(amount: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount)
}

function AccountCard({ account }: { account: Account }) {
  return (
    <Card className="transition-shadow hover:shadow-md">
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between">
          <div>
            <CardTitle className="text-base">{account.type === 'CHECKING' ? 'Compte courant' : 'Compte épargne'}</CardTitle>
            <p className="mt-0.5 font-mono text-xs text-muted-foreground">{account.iban}</p>
          </div>
          <Badge variant={account.status === 'ACTIVE' ? 'default' : 'secondary'}>
            {account.status === 'ACTIVE' ? 'Actif' : 'Fermé'}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="flex items-end justify-between pt-0">
        <p className="text-2xl font-semibold tabular-nums">
          {formatAmount(account.balance, account.currency)}
        </p>
        <Button variant="ghost" size="sm" asChild className="gap-1 text-muted-foreground">
          <Link to={`/accounts/${account.id}`}>
            Voir <ArrowRight size={14} />
          </Link>
        </Button>
      </CardContent>
    </Card>
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

  const totalBalance = accounts?.reduce((sum, a) => sum + a.balance, 0) ?? 0
  const currency = accounts?.[0]?.currency ?? 'EUR'

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Tableau de bord</h1>
          <p className="text-sm text-muted-foreground">Vue d'ensemble de vos comptes</p>
        </div>
        <Button asChild size="sm" className="gap-1.5">
          <Link to="/accounts/new">
            <PlusCircle size={15} />
            Nouveau compte
          </Link>
        </Button>
      </div>

      {/* Total balance banner */}
      {!isLoading && accounts && accounts.length > 0 && (
        <Card className="bg-primary text-primary-foreground">
          <CardContent className="flex items-center gap-3 py-4">
            <Landmark size={28} />
            <div>
              <p className="text-sm opacity-80">Solde total</p>
              <p className="text-3xl font-bold tabular-nums">{formatAmount(totalBalance, currency)}</p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Account list */}
      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2">
          {[...Array(2)].map((_, i) => (
            <Card key={i}><CardContent className="p-6"><Skeleton className="h-24 w-full" /></CardContent></Card>
          ))}
        </div>
      )}

      {isError && (
        <p className="text-sm text-destructive">Impossible de charger vos comptes.</p>
      )}

      {!isLoading && accounts && accounts.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
            <Landmark size={40} className="text-muted-foreground" />
            <p className="text-muted-foreground">Vous n'avez pas encore de compte.</p>
            <Button asChild size="sm">
              <Link to="/accounts/new">Ouvrir un compte</Link>
            </Button>
          </CardContent>
        </Card>
      )}

      {!isLoading && accounts && accounts.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2">
          {accounts.map(a => <AccountCard key={a.id} account={a} />)}
        </div>
      )}
    </div>
  )
}
