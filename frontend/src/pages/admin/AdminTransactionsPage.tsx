import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import api from '@/lib/api'
import type { AdminTransaction, Page } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import { ArrowLeftRight, ArrowUpRight, ArrowDownLeft, Clock } from 'lucide-react'

function formatAmount(n: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(n)
}
function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso))
}
function shortId(id: string) {
  return id.slice(0, 8) + '…'
}

const STATUS_MAP = {
  COMPLETED: { label: 'Complété',   className: 'border-emerald-200 bg-emerald-50 text-emerald-700' },
  PENDING:   { label: 'En attente', className: 'border-amber-200 bg-amber-50 text-amber-700' },
  FAILED:    { label: 'Échoué',     className: 'border-red-200 bg-red-50 text-red-700' },
  CANCELLED: { label: 'Annulé',     className: 'border-border bg-muted text-muted-foreground' },
} as const

export default function AdminTransactionsPage() {
  const [page, setPage] = useState(0)

  const { data: txPage, isLoading } = useQuery<Page<AdminTransaction>>({
    queryKey: ['admin', 'transactions', page],
    queryFn: () =>
      api.get<Page<AdminTransaction>>(`/api/v1/admin/transactions?page=${page}&size=20`).then(r => r.data),
  })

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-xl bg-blue-100 text-blue-700">
          <ArrowLeftRight size={20} />
        </div>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Transactions</h1>
          <p className="text-sm text-muted-foreground">
            {txPage ? `${txPage.totalElements} transaction${txPage.totalElements > 1 ? 's' : ''}` : '—'}
          </p>
        </div>
      </div>

      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="text-sm font-semibold">Toutes les transactions</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading
            ? <div className="space-y-3 p-4">{[1,2,3,4].map(i => <Skeleton key={i} className="h-14 w-full" />)}</div>
            : !txPage?.content.length
              ? (
                <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
                  <Clock size={28} className="opacity-40" />
                  <p className="text-sm">Aucune transaction.</p>
                </div>
              )
              : (
                <>
                  <div className="divide-y divide-border">
                    {txPage.content.map(tx => {
                      const statusInfo = STATUS_MAP[tx.status] ?? STATUS_MAP.PENDING
                      return (
                        <div key={tx.id} className="flex items-center gap-3 px-4 py-3.5">
                          {/* Direction icon */}
                          <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
                            <ArrowLeftRight size={15} />
                          </div>

                          {/* Accounts */}
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                              <ArrowUpRight size={11} className="text-red-500 shrink-0" />
                              <span className="font-mono">{shortId(tx.fromAccountId)}</span>
                              <span className="mx-1 text-border">→</span>
                              <ArrowDownLeft size={11} className="text-emerald-500 shrink-0" />
                              <span className="font-mono">{shortId(tx.toAccountId)}</span>
                            </div>
                            <p className="mt-0.5 text-xs text-muted-foreground">{formatDate(tx.createdAt)}</p>
                          </div>

                          {/* Amount */}
                          <p className="shrink-0 text-sm font-semibold tabular-nums">
                            {formatAmount(tx.amount, tx.currency)}
                          </p>

                          {/* Status */}
                          <span className={`hidden shrink-0 items-center rounded-full border px-2 py-0.5 text-[10px] font-medium sm:inline-flex ${statusInfo.className}`}>
                            {statusInfo.label}
                          </span>
                        </div>
                      )
                    })}
                  </div>

                  {/* Pagination */}
                  {txPage.totalPages > 1 && (
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
                </>
              )
          }
        </CardContent>
      </Card>
    </div>
  )
}
