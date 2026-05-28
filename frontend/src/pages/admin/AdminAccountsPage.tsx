import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import api from '@/lib/api'
import type { AdminAccount, Page } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import { CreditCard, PiggyBank } from 'lucide-react'

function formatAmount(n: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(n)
}
function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short' }).format(new Date(iso))
}

const STATUS_LABELS: Record<string, { label: string; className: string }> = {
  ACTIVE:  { label: 'Actif',    className: 'border-emerald-200 bg-emerald-50 text-emerald-700' },
  BLOCKED: { label: 'Bloqué',   className: 'border-amber-200 bg-amber-50 text-amber-700' },
  CLOSED:  { label: 'Fermé',    className: 'border-red-200 bg-red-50 text-red-700' },
}

export default function AdminAccountsPage() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)

  const { data: accountsPage, isLoading } = useQuery<Page<AdminAccount>>({
    queryKey: ['admin', 'accounts', page],
    queryFn: () => api.get<Page<AdminAccount>>(`/api/v1/admin/accounts?page=${page}&size=20`).then(r => r.data),
  })

  const updateStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      api.patch(`/api/v1/admin/accounts/${id}/status?status=${status}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'accounts'] })
      toast.success('Statut du compte mis à jour')
    },
    onError: () => toast.error('Impossible de mettre à jour le statut'),
  })

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700">
          <CreditCard size={20} />
        </div>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Comptes</h1>
          <p className="text-sm text-muted-foreground">
            {accountsPage ? `${accountsPage.totalElements} compte${accountsPage.totalElements > 1 ? 's' : ''}` : '—'}
          </p>
        </div>
      </div>

      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="text-sm font-semibold">Tous les comptes</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading
            ? <div className="space-y-3 p-4">{[1,2,3,4].map(i => <Skeleton key={i} className="h-14 w-full" />)}</div>
            : !accountsPage?.content.length
              ? (
                <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
                  <CreditCard size={28} className="opacity-40" />
                  <p className="text-sm">Aucun compte.</p>
                </div>
              )
              : (
                <>
                  <div className="divide-y divide-border">
                    {accountsPage.content.map(a => {
                      const isChecking = a.type === 'CHECKING'
                      const statusInfo = STATUS_LABELS[a.status] ?? STATUS_LABELS.ACTIVE
                      return (
                        <div key={a.id} className="flex items-center gap-4 px-4 py-3.5">
                          {/* Icon */}
                          <div className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${
                            isChecking ? 'bg-primary/10 text-primary' : 'bg-[oklch(0.78_0.145_82)]/20 text-[oklch(0.50_0.14_82)]'
                          }`}>
                            {isChecking ? <CreditCard size={15} /> : <PiggyBank size={15} />}
                          </div>

                          {/* Info */}
                          <div className="min-w-0 flex-1">
                            <p className="truncate text-sm font-medium font-mono">{a.iban}</p>
                            <p className="text-xs text-muted-foreground">
                              {isChecking ? 'Courant' : 'Épargne'} · créé le {formatDate(a.createdAt)}
                            </p>
                          </div>

                          {/* Balance */}
                          <p className="shrink-0 text-sm font-semibold tabular-nums">
                            {formatAmount(a.balance, a.currency)}
                          </p>

                          {/* Status badge */}
                          <span className={`hidden shrink-0 items-center rounded-full border px-2 py-0.5 text-[10px] font-medium sm:inline-flex ${statusInfo.className}`}>
                            {statusInfo.label}
                          </span>

                          {/* Quick-block */}
                          {a.status === 'ACTIVE' && (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="h-8 shrink-0 text-xs text-amber-700 hover:bg-amber-50"
                              onClick={() => updateStatus.mutate({ id: a.id, status: 'BLOCKED' })}
                              disabled={updateStatus.isPending}
                            >
                              Bloquer
                            </Button>
                          )}
                          {a.status === 'BLOCKED' && (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="h-8 shrink-0 text-xs text-emerald-700 hover:bg-emerald-50"
                              onClick={() => updateStatus.mutate({ id: a.id, status: 'ACTIVE' })}
                              disabled={updateStatus.isPending}
                            >
                              Débloquer
                            </Button>
                          )}
                        </div>
                      )
                    })}
                  </div>

                  {/* Pagination */}
                  {accountsPage.totalPages > 1 && (
                    <>
                      <Separator />
                      <div className="flex items-center justify-between px-4 py-3 text-xs text-muted-foreground">
                        <span>Page {accountsPage.number + 1} / {accountsPage.totalPages}</span>
                        <div className="flex gap-2">
                          <Button variant="outline" size="sm" className="h-7 text-xs"
                            disabled={accountsPage.first} onClick={() => setPage(p => p - 1)}>
                            Précédent
                          </Button>
                          <Button variant="outline" size="sm" className="h-7 text-xs"
                            disabled={accountsPage.last} onClick={() => setPage(p => p + 1)}>
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
