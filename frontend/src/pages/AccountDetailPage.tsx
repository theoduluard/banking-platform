import { useQuery } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account, Page, Transaction } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import { ArrowLeftRight, ArrowLeft, TrendingDown, TrendingUp, Clock } from 'lucide-react'
import { useState } from 'react'

function formatAmount(amount: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount)
}

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

function statusBadge(status: Transaction['status']) {
  const map = {
    COMPLETED: { label: 'Complété',  variant: 'default'     },
    PENDING:   { label: 'En attente', variant: 'secondary'   },
    FAILED:    { label: 'Échoué',     variant: 'destructive' },
  } as const
  const { label, variant } = map[status]
  return <Badge variant={variant}>{label}</Badge>
}

function TransactionRow({ tx, accountId }: { tx: Transaction; accountId: string }) {
  const isDebit = tx.fromAccountId === accountId
  const sign    = isDebit ? '-' : '+'
  const Icon    = isDebit ? TrendingDown : TrendingUp

  return (
    <div className="flex items-center justify-between py-3">
      <div className="flex items-center gap-3">
        <div className={`flex size-8 items-center justify-center rounded-full ${isDebit ? 'bg-destructive/10 text-destructive' : 'bg-green-500/10 text-green-600'}`}>
          <Icon size={15} />
        </div>
        <div>
          <p className="text-sm font-medium">
            {tx.type === 'TRANSFER' ? (isDebit ? 'Virement émis' : 'Virement reçu') : tx.type}
          </p>
          <p className="text-xs text-muted-foreground">{formatDate(tx.createdAt)}</p>
        </div>
      </div>
      <div className="flex items-center gap-3">
        {statusBadge(tx.status)}
        <p className={`w-24 text-right tabular-nums text-sm font-semibold ${isDebit ? 'text-destructive' : 'text-green-600'}`}>
          {sign} {formatAmount(tx.amount, tx.currency)}
        </p>
      </div>
    </div>
  )
}

export default function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const userId = getUserIdFromToken()
  const [page, setPage] = useState(0)

  const { data: account, isLoading: loadingAccount } = useQuery<Account>({
    queryKey: ['account', id],
    queryFn:  () => api
      .get<Account[]>('/api/v1/accounts', { headers: { 'X-User-Id': userId } })
      .then(r => {
        const found = r.data.find(a => a.id === id)
        if (!found) throw new Error('Account not found')
        return found
      }),
    enabled: !!id && !!userId,
  })

  const { data: txPage, isLoading: loadingTx } = useQuery<Page<Transaction>>({
    queryKey: ['transactions', id, page],
    queryFn:  () => api
      .get<Page<Transaction>>(`/api/v1/transactions?accountId=${id}&page=${page}&size=10`)
      .then(r => r.data),
    enabled: !!id,
  })

  return (
    <div className="space-y-6">
      {/* Back */}
      <Button variant="ghost" size="sm" asChild className="-ml-2 gap-1 text-muted-foreground">
        <Link to="/dashboard">
          <ArrowLeft size={14} /> Retour
        </Link>
      </Button>

      {/* Account summary */}
      {loadingAccount ? (
        <Skeleton className="h-28 w-full rounded-xl" />
      ) : account ? (
        <Card>
          <CardHeader className="pb-2">
            <div className="flex items-start justify-between">
              <div>
                <CardTitle>{account.type === 'CHECKING' ? 'Compte courant' : 'Compte épargne'}</CardTitle>
                <p className="mt-0.5 font-mono text-xs text-muted-foreground">{account.iban}</p>
              </div>
              <Badge variant={account.status === 'ACTIVE' ? 'default' : 'secondary'}>
                {account.status === 'ACTIVE' ? 'Actif' : 'Fermé'}
              </Badge>
            </div>
          </CardHeader>
          <CardContent className="flex items-end justify-between pt-0">
            <p className="text-3xl font-bold tabular-nums">
              {formatAmount(account.balance, account.currency)}
            </p>
            <Button asChild size="sm" className="gap-1.5">
              <Link to="/transfer">
                <ArrowLeftRight size={14} />
                Virer
              </Link>
            </Button>
          </CardContent>
        </Card>
      ) : null}

      {/* Transaction history */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Historique des transactions</CardTitle>
        </CardHeader>
        <CardContent className="px-4">
          {loadingTx && <Skeleton className="h-40 w-full" />}

          {!loadingTx && txPage && txPage.content.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-8 text-center text-muted-foreground">
              <Clock size={32} />
              <p className="text-sm">Aucune transaction pour ce compte.</p>
            </div>
          )}

          {!loadingTx && txPage && txPage.content.length > 0 && (
            <div className="divide-y divide-border">
              {txPage.content.map(tx => (
                <TransactionRow key={tx.id} tx={tx} accountId={id!} />
              ))}
            </div>
          )}

          {/* Pagination */}
          {txPage && txPage.totalPages > 1 && (
            <>
              <Separator className="mt-4" />
              <div className="flex items-center justify-between pt-4 text-sm text-muted-foreground">
                <span>Page {txPage.number + 1} / {txPage.totalPages}</span>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" disabled={txPage.first}
                    onClick={() => setPage(p => p - 1)}>
                    Précédent
                  </Button>
                  <Button variant="outline" size="sm" disabled={txPage.last}
                    onClick={() => setPage(p => p + 1)}>
                    Suivant
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
