import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import api from '@/lib/api'
import type { AdminAccount, AdminOperationRequest, Page } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { CreditCard, PiggyBank, ArrowDownToLine, ArrowUpFromLine, AlertTriangle } from 'lucide-react'

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatAmount(n: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(n)
}
function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short' }).format(new Date(iso))
}

const STATUS_LABELS: Record<string, { label: string; className: string }> = {
  ACTIVE:  { label: 'Actif',  className: 'border-emerald-200 bg-emerald-50 text-emerald-700' },
  BLOCKED: { label: 'Bloqué', className: 'border-amber-200 bg-amber-50 text-amber-700' },
  CLOSED:  { label: 'Fermé',  className: 'border-red-200 bg-red-50 text-red-700' },
}

// ── Dialog schema ─────────────────────────────────────────────────────────────

const operationSchema = z.object({
  amount:      z.number({ message: 'Montant invalide' }).positive('Le montant doit être positif'),
  description: z.string().max(255, 'Maximum 255 caractères').optional(),
})
type OperationForm = z.infer<typeof operationSchema>

type OperationType = 'deposit' | 'withdrawal'

// ── Operation dialog ──────────────────────────────────────────────────────────

function OperationDialog({
  account,
  type,
  open,
  onClose,
  onSuccess,
}: {
  account: AdminAccount
  type: OperationType
  open: boolean
  onClose: () => void
  onSuccess: () => void
}) {
  const isDeposit = type === 'deposit'
  const color     = isDeposit ? 'emerald' : 'amber'

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<OperationForm>({
    resolver: zodResolver(operationSchema),
  })

  const mutation = useMutation({
    mutationFn: (data: AdminOperationRequest) =>
      api.post(`/api/v1/admin/transactions/${type}`, data).then(r => r.data),
    onSuccess: () => {
      toast.success(isDeposit ? 'Dépôt effectué avec succès' : 'Retrait effectué avec succès')
      reset()
      onSuccess()
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      const msg = err.response?.data?.error ?? 'Une erreur est survenue'
      toast.error(msg)
    },
  })

  function onSubmit(data: OperationForm) {
    mutation.mutate({
      accountId: account.id,
      amount:    data.amount,
      description: data.description,
    })
  }

  function handleClose() {
    if (!isSubmitting) { reset(); onClose() }
  }

  const accountLabel = account.type === 'CHECKING' ? 'Compte courant' : 'Compte épargne'

  return (
    <Dialog open={open} onOpenChange={o => { if (!o) handleClose() }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {isDeposit
              ? <ArrowDownToLine size={18} className="text-emerald-600" />
              : <ArrowUpFromLine size={18} className="text-amber-600" />}
            {isDeposit ? 'Créditer le compte' : 'Débiter le compte'}
          </DialogTitle>
          <DialogDescription>
            {accountLabel} — <span className="font-mono text-xs">{account.iban.slice(0, 4)} ···· {account.iban.slice(-4)}</span>
          </DialogDescription>
        </DialogHeader>

        {/* Current balance */}
        <div className="rounded-lg border bg-muted/40 px-4 py-3 text-center">
          <p className="mb-0.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Solde actuel</p>
          <p className="text-xl font-bold tabular-nums">{formatAmount(account.balance, account.currency)}</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {/* Amount */}
          <div className="space-y-2">
            <Label htmlFor="amount">Montant</Label>
            <div className="relative">
              <Input
                id="amount"
                type="number"
                min="0.01"
                step="0.01"
                placeholder="0,00"
                {...register('amount', { valueAsNumber: true })}
                className="h-11 pr-14 tabular-nums text-base font-semibold"
              />
              <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-sm font-medium text-muted-foreground">
                EUR
              </span>
            </div>
            {errors.amount && <p className="text-xs text-destructive">{errors.amount.message}</p>}
          </div>

          {/* Description */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="description">Motif</Label>
              <span className="text-xs text-muted-foreground">Optionnel</span>
            </div>
            <Textarea
              id="description"
              {...register('description')}
              placeholder="Ajustement manuel, correction d'erreur…"
              rows={2}
              className="resize-none text-sm"
            />
            {errors.description && <p className="text-xs text-destructive">{errors.description.message}</p>}
          </div>

          {/* Warning for withdrawal */}
          {!isDeposit && (
            <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5">
              <AlertTriangle size={14} className="mt-0.5 shrink-0 text-amber-600" />
              <p className="text-xs leading-relaxed text-amber-700">
                Cette opération <strong>débite</strong> le solde du client. Assurez-vous que c'est intentionnel.
              </p>
            </div>
          )}

          <DialogFooter className="gap-2 pt-1">
            <Button type="button" variant="outline" onClick={handleClose} disabled={isSubmitting} className="flex-1">
              Annuler
            </Button>
            <Button
              type="submit"
              disabled={isSubmitting}
              className={`flex-1 gap-2 ${
                isDeposit
                  ? 'bg-emerald-600 text-white hover:bg-emerald-700'
                  : 'bg-amber-600 text-white hover:bg-amber-700'
              }`}
            >
              {isDeposit
                ? <ArrowDownToLine size={14} />
                : <ArrowUpFromLine size={14} />}
              <span>{isSubmitting ? 'Traitement…' : isDeposit ? 'Créditer' : 'Débiter'}</span>
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function AdminAccountsPage() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)

  // Operation dialog state
  const [dialogAccount, setDialogAccount]  = useState<AdminAccount | null>(null)
  const [dialogType,    setDialogType]     = useState<OperationType>('deposit')

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

  function openDialog(account: AdminAccount, type: OperationType) {
    setDialogAccount(account)
    setDialogType(type)
  }

  function handleOperationSuccess() {
    setDialogAccount(null)
    qc.invalidateQueries({ queryKey: ['admin', 'accounts'] })
  }

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
                      const canAdjust  = a.status !== 'CLOSED'
                      return (
                        <div key={a.id} className="flex items-center gap-3 px-4 py-3.5">
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

                          {/* Admin balance operations */}
                          {canAdjust && (
                            <div className="flex shrink-0 items-center gap-1">
                              <Button
                                variant="ghost"
                                size="sm"
                                className="h-8 gap-1 px-2 text-xs text-emerald-700 hover:bg-emerald-50"
                                onClick={() => openDialog(a, 'deposit')}
                                title="Créditer ce compte"
                              >
                                <ArrowDownToLine size={13} />
                                <span className="hidden lg:inline">Créditer</span>
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                className="h-8 gap-1 px-2 text-xs text-amber-700 hover:bg-amber-50"
                                onClick={() => openDialog(a, 'withdrawal')}
                                title="Débiter ce compte"
                              >
                                <ArrowUpFromLine size={13} />
                                <span className="hidden lg:inline">Débiter</span>
                              </Button>
                            </div>
                          )}

                          {/* Quick block / unblock */}
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

      {/* Operation dialog */}
      {dialogAccount && (
        <OperationDialog
          account={dialogAccount}
          type={dialogType}
          open={!!dialogAccount}
          onClose={() => setDialogAccount(null)}
          onSuccess={handleOperationSuccess}
        />
      )}
    </div>
  )
}
