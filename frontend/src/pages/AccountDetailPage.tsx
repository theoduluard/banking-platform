import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account, Page, Transaction } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import { ArrowLeftRight, ArrowLeft, ArrowDownLeft, ArrowUpRight, Clock, CreditCard, PiggyBank, RefreshCw } from 'lucide-react'
import { useState, useEffect } from 'react'

function formatAmount(amount: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount)
}
function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

function StatusBadge({ status }: { status: Transaction['status'] }) {
  const map = {
    COMPLETED: { label: 'Complété',   className: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
    PENDING:   { label: 'En attente', className: 'bg-amber-50 text-amber-700 border-amber-200' },
    FAILED:    { label: 'Échoué',     className: 'bg-red-50 text-red-700 border-red-200' },
  } as const
  const { label, className } = map[status]
  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium ${className}`}>
      {label}
    </span>
  )
}

function TransactionRow({ tx, accountId }: { tx: Transaction; accountId: string }) {
  const isDebit = tx.fromAccountId === accountId

  return (
    <div className="flex items-center gap-3 py-3.5">
      <div className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${
        isDebit ? 'bg-red-50 text-red-500' : 'bg-emerald-50 text-emerald-600'
      }`}>
        {isDebit
          ? <ArrowUpRight size={16} />
          : <ArrowDownLeft size={16} />
        }
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">
          {tx.type === 'TRANSFER'
            ? (isDebit ? 'Virement émis' : 'Virement reçu')
            : tx.type}
        </p>
        {tx.description && (
          <p className="truncate text-xs text-muted-foreground/80 italic">{tx.description}</p>
        )}
        <p className="text-xs text-muted-foreground">{formatDate(tx.createdAt)}</p>
      </div>

      <div className="flex shrink-0 flex-col items-end gap-1">
        <p className={`text-sm font-semibold tabular-nums ${
          isDebit ? 'text-red-600' : 'text-emerald-600'
        }`}>
          {isDebit ? '−' : '+'} {formatAmount(tx.amount, tx.currency)}
        </p>
        <StatusBadge status={tx.status} />
      </div>
    </div>
  )
}

export default function AccountDetailPage() {
  const { id }      = useParams<{ id: string }>()
  const userId      = getUserIdFromToken()
  const queryClient = useQueryClient()
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

  const { data: txPage, isLoading: loadingTx, isFetching: fetchingTx } = useQuery<Page<Transaction>>({
    queryKey: ['transactions', id, page],
    queryFn:  () => api
      .get<Page<Transaction>>(`/api/v1/transactions?accountId=${id}&page=${page}&size=10`)
      .then(r => r.data),
    enabled: !!id,
    // Poll every 3 s while at least one transaction is still PENDING.
    // The callback receives the latest query state so the interval is
    // re-evaluated after every fetch — it automatically stops when all
    // transactions have settled (COMPLETED / FAILED).
    refetchInterval: (query) => {
      const hasPending = query.state.data?.content.some(
        tx => tx.status === 'PENDING',
      ) ?? false
      return hasPending ? 3000 : false
    },
  })

  // When the last PENDING transaction settles, also refresh the account
  // balance so the displayed figure reflects the debit/credit immediately.
  const hasPending = txPage?.content.some(tx => tx.status === 'PENDING') ?? false
  useEffect(() => {
    if (!txPage || hasPending) return
    queryClient.invalidateQueries({ queryKey: ['account', id] })
    queryClient.invalidateQueries({ queryKey: ['accounts', userId] })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [txPage])

  const isChecking = account?.type === 'CHECKING'

  return (
    <div className="space-y-6">
      {/* Back */}
      <Button variant="ghost" size="sm" asChild className="-ml-2 gap-1.5 text-muted-foreground">
        <Link to="/dashboard"><ArrowLeft size={14} /> Retour</Link>
      </Button>

      {/* Account header */}
      {loadingAccount
        ? <Skeleton className="h-36 w-full rounded-2xl" />
        : account && (
          <div className={`relative overflow-hidden rounded-2xl p-6 text-white shadow-lg ${
            isChecking ? 'bg-primary' : 'bg-[oklch(0.50_0.14_82)]'
          }`}>
            <div className="pointer-events-none absolute inset-0 overflow-hidden">
              <div className="absolute -right-8 -top-8 h-40 w-40 rounded-full bg-white/10" />
            </div>
            <div className="relative z-10 flex items-start justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <div className="flex size-9 items-center justify-center rounded-lg bg-white/20">
                    {isChecking ? <CreditCard size={16} /> : <PiggyBank size={16} />}
                  </div>
                  <div>
                    <p className="font-semibold">{isChecking ? 'Compte courant' : 'Compte épargne'}</p>
                    <p className="font-mono text-[10px] tracking-wider text-white/60">{account.iban}</p>
                  </div>
                </div>
                <div className="mt-4">
                  <p className="text-xs text-white/60">Solde disponible</p>
                  <p className="mt-0.5 text-3xl font-bold tabular-nums">
                    {formatAmount(account.balance, account.currency)}
                  </p>
                </div>
              </div>
              <div className="flex flex-col items-end gap-2">
                <Badge variant="secondary" className="bg-white/20 text-white border-0 text-[10px]">
                  {account.status === 'ACTIVE' ? 'Actif' : 'Fermé'}
                </Badge>
                <Button size="sm" variant="secondary" asChild
                  className="mt-auto gap-1.5 bg-white/20 text-white hover:bg-white/30 border-0">
                  <Link to="/transfer">
                    <ArrowLeftRight size={13} /> Virer
                  </Link>
                </Button>
              </div>
            </div>
          </div>
        )
      }

      {/* Transactions */}
      <Card>
        <CardHeader className="border-b pb-4">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-semibold">Historique des transactions</CardTitle>
            {/* Pulse indicator: visible while polling for PENDING updates */}
            {hasPending && (
              <span className="flex items-center gap-1.5 text-[10px] font-medium text-amber-600">
                <RefreshCw size={11} className={fetchingTx ? 'animate-spin' : 'animate-pulse'} />
                Mise à jour en cours…
              </span>
            )}
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {loadingTx && (
            <div className="space-y-4 p-4">
              {[1,2,3].map(i => <Skeleton key={i} className="h-14 w-full" />)}
            </div>
          )}

          {!loadingTx && txPage?.content.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
              <Clock size={28} className="text-muted-foreground/50" />
              <p className="text-sm">Aucune transaction pour ce compte.</p>
            </div>
          )}

          {!loadingTx && txPage && txPage.content.length > 0 && (
            <div className="divide-y divide-border px-4">
              {txPage.content.map(tx => (
                <TransactionRow key={tx.id} tx={tx} accountId={id!} />
              ))}
            </div>
          )}

          {txPage && txPage.totalPages > 1 && (
            <>
              <Separator />
              <div className="flex items-center justify-between px-4 py-3 text-xs text-muted-foreground">
                <span>Page {txPage.number + 1} / {txPage.totalPages}</span>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" className="h-7 text-xs"
                    disabled={txPage.first} onClick={() => setPage(p => p - 1)}>
                    Précédent
                  </Button>
                  <Button variant="outline" size="sm" className="h-7 text-xs"
                    disabled={txPage.last} onClick={() => setPage(p => p + 1)}>
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
