import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import api from '@/lib/api'
import type { AdminLoan, LoanStatus, Page } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import {
  Banknote,
  CheckCircle2,
  XCircle,
  Clock,
  AlertTriangle,
  User,
  Calendar,
  TrendingUp,
} from 'lucide-react'

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatAmount(n: number) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(n)
}

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium' }).format(new Date(iso))
}

function formatDateTime(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

const STATUS_CONFIG: Record<LoanStatus, { label: string; className: string }> = {
  PENDING:   { label: 'En attente',  className: 'border-amber-200  bg-amber-50  text-amber-700'   },
  APPROVED:  { label: 'Approuvé',   className: 'border-emerald-200 bg-emerald-50 text-emerald-700' },
  REJECTED:  { label: 'Refusé',     className: 'border-red-200     bg-red-50     text-red-700'    },
  DISBURSED: { label: 'Décaissé',   className: 'border-blue-200    bg-blue-50    text-blue-700'   },
  CLOSED:    { label: 'Clôturé',    className: 'border-gray-200    bg-gray-50    text-gray-600'   },
}

// ── Decision dialog ───────────────────────────────────────────────────────────

const decisionSchema = z.object({
  note: z.string().max(500, 'Maximum 500 caractères').optional(),
})
type DecisionForm = z.infer<typeof decisionSchema>

function DecisionDialog({
  loan,
  action,
  open,
  onClose,
}: {
  loan: AdminLoan
  action: 'APPROVE' | 'REJECT'
  open: boolean
  onClose: () => void
}) {
  const qc        = useQueryClient()
  const isApprove = action === 'APPROVE'

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<DecisionForm>({
    resolver: zodResolver(decisionSchema),
  })

  const mutation = useMutation({
    mutationFn: (data: DecisionForm) =>
      api.post(`/api/v1/admin/loans/${loan.id}/decision`, {
        action,
        note: data.note || null,
      }).then(r => r.data),
    onSuccess: () => {
      toast.success(isApprove ? 'Prêt approuvé avec succès' : 'Prêt refusé')
      qc.invalidateQueries({ queryKey: ['admin', 'loans'] })
      reset()
      onClose()
    },
    onError: () => toast.error('Une erreur est survenue'),
  })

  function handleClose() {
    if (!isSubmitting) { reset(); onClose() }
  }

  return (
    <Dialog open={open} onOpenChange={o => { if (!o) handleClose() }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {isApprove
              ? <CheckCircle2 size={18} className="text-emerald-600" />
              : <XCircle     size={18} className="text-red-600" />}
            {isApprove ? 'Approuver le prêt' : 'Refuser le prêt'}
          </DialogTitle>
          <DialogDescription>
            Demande de <span className="font-mono text-xs">{loan.userId.slice(0, 8)}…</span>
          </DialogDescription>
        </DialogHeader>

        {/* Loan summary */}
        <div className="grid grid-cols-3 gap-3 rounded-lg border bg-muted/40 p-3 text-center text-sm">
          <div>
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Montant</p>
            <p className="font-bold tabular-nums">{formatAmount(loan.amount)}</p>
          </div>
          <div>
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Durée</p>
            <p className="font-bold">{loan.durationMonths} mois</p>
          </div>
          <div>
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Mensualité</p>
            <p className="font-bold tabular-nums">{formatAmount(loan.monthlyPayment)}</p>
          </div>
        </div>

        {loan.purpose && (
          <div className="rounded-lg border bg-muted/30 px-3 py-2 text-sm">
            <p className="mb-0.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Objet</p>
            <p className="text-foreground">{loan.purpose}</p>
          </div>
        )}

        {!isApprove && (
          <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2.5">
            <AlertTriangle size={14} className="mt-0.5 shrink-0 text-red-600" />
            <p className="text-xs leading-relaxed text-red-700">
              Cette action <strong>refuse définitivement</strong> la demande. Le client sera notifié.
            </p>
          </div>
        )}

        <form onSubmit={handleSubmit(d => mutation.mutate(d))} className="space-y-4">
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="note">Note interne</Label>
              <span className="text-xs text-muted-foreground">Optionnel</span>
            </div>
            <Textarea
              id="note"
              {...register('note')}
              placeholder={isApprove
                ? 'Ex : Dossier complet, revenus vérifiés…'
                : 'Ex : Revenus insuffisants, dossier incomplet…'}
              rows={3}
              className="resize-none text-sm"
            />
            {errors.note && <p className="text-xs text-destructive">{errors.note.message}</p>}
          </div>

          <DialogFooter className="gap-2 pt-1">
            <Button type="button" variant="outline" onClick={handleClose} disabled={isSubmitting} className="flex-1">
              Annuler
            </Button>
            <Button
              type="submit"
              disabled={isSubmitting}
              className={`flex-1 gap-2 ${
                isApprove
                  ? 'bg-emerald-600 text-white hover:bg-emerald-700'
                  : 'bg-red-600 text-white hover:bg-red-700'
              }`}
            >
              {isApprove ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
              <span>{isSubmitting ? 'Traitement…' : isApprove ? 'Approuver' : 'Refuser'}</span>
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// ── Loan row ──────────────────────────────────────────────────────────────────

function LoanRow({
  loan,
  onDecision,
  compact = false,
}: {
  loan: AdminLoan
  onDecision?: (loan: AdminLoan, action: 'APPROVE' | 'REJECT') => void
  compact?: boolean
}) {
  const statusInfo = STATUS_CONFIG[loan.status]

  return (
    <div className="flex flex-wrap items-center gap-3 px-4 py-3.5">
      {/* Icon */}
      <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
        <Banknote size={15} />
      </div>

      {/* Info */}
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold tabular-nums">{formatAmount(loan.amount)}</p>
        <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <User size={10} />
            <span className="font-mono">{loan.userId.slice(0, 8)}…</span>
          </span>
          <span className="flex items-center gap-1">
            <Calendar size={10} />
            {loan.durationMonths} mois
          </span>
          <span className="flex items-center gap-1">
            <TrendingUp size={10} />
            {formatAmount(loan.monthlyPayment)}/mois
          </span>
          {!compact && (
            <span className="text-muted-foreground/60">·</span>
          )}
          {!compact && (
            <span>{formatDate(loan.createdAt)}</span>
          )}
        </div>
        {loan.purpose && !compact && (
          <p className="mt-0.5 truncate text-xs text-muted-foreground italic">{loan.purpose}</p>
        )}
      </div>

      {/* Status badge */}
      <span className={`shrink-0 rounded-full border px-2 py-0.5 text-[10px] font-medium ${statusInfo.className}`}>
        {statusInfo.label}
      </span>

      {/* Action buttons — pending only */}
      {loan.status === 'PENDING' && onDecision && (
        <div className="flex shrink-0 items-center gap-1.5">
          <Button
            variant="ghost"
            size="sm"
            className="h-8 gap-1 px-2.5 text-xs text-emerald-700 hover:bg-emerald-50"
            onClick={() => onDecision(loan, 'APPROVE')}
          >
            <CheckCircle2 size={13} />
            <span>Approuver</span>
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="h-8 gap-1 px-2.5 text-xs text-red-700 hover:bg-red-50"
            onClick={() => onDecision(loan, 'REJECT')}
          >
            <XCircle size={13} />
            <span>Refuser</span>
          </Button>
        </div>
      )}

      {/* Admin note */}
      {loan.adminNote && !compact && (
        <p className="w-full pl-12 text-xs text-muted-foreground italic">
          Note : {loan.adminNote}
        </p>
      )}
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

type Tab = 'pending' | 'all'

export default function AdminLoansPage() {
  const qc = useQueryClient()

  const [tab,  setTab]  = useState<Tab>('pending')
  const [page, setPage] = useState(0)

  const [decisionLoan,   setDecisionLoan]   = useState<AdminLoan | null>(null)
  const [decisionAction, setDecisionAction] = useState<'APPROVE' | 'REJECT'>('APPROVE')

  // Pending loans (always refetch every 30s)
  const { data: pendingLoans = [], isLoading: loadingPending } = useQuery<AdminLoan[]>({
    queryKey: ['admin', 'loans', 'pending'],
    queryFn: () => api.get<AdminLoan[]>('/api/v1/admin/loans/pending').then(r => r.data),
    refetchInterval: 30_000,
  })

  // All loans (paginated)
  const { data: loansPage, isLoading: loadingAll } = useQuery<Page<AdminLoan>>({
    queryKey: ['admin', 'loans', 'all', page],
    queryFn: () => api.get<Page<AdminLoan>>(`/api/v1/admin/loans?page=${page}&size=20`).then(r => r.data),
    enabled: tab === 'all',
  })

  function openDecision(loan: AdminLoan, action: 'APPROVE' | 'REJECT') {
    setDecisionLoan(loan)
    setDecisionAction(action)
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <Banknote size={20} />
        </div>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Prêts</h1>
          <p className="text-sm text-muted-foreground">
            {loadingPending ? '—' : `${pendingLoans.length} demande${pendingLoans.length > 1 ? 's' : ''} en attente`}
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 rounded-lg border bg-muted/40 p-1 w-fit">
        {([
          { key: 'pending', label: 'En attente', count: pendingLoans.length },
          { key: 'all',     label: 'Tous les prêts' },
        ] as const).map(({ key, label, count }) => (
          <button
            key={key}
            onClick={() => { setTab(key); setPage(0) }}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              tab === key
                ? 'bg-background text-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            {label}
            {count !== undefined && count > 0 && (
              <span className="rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700">
                {count}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* ── Pending tab ──────────────────────────────────────────────────── */}
      {tab === 'pending' && (
        <Card className={pendingLoans.length > 0 ? 'border-amber-200' : undefined}>
          <CardHeader className={`border-b pb-3 ${pendingLoans.length > 0 ? 'border-amber-200 bg-amber-50/60' : ''}`}>
            <CardTitle className={`flex items-center gap-2 text-sm font-semibold ${pendingLoans.length > 0 ? 'text-amber-800' : ''}`}>
              <Clock size={15} className={pendingLoans.length > 0 ? 'text-amber-600' : 'text-muted-foreground'} />
              Demandes en attente d'examen
              {!loadingPending && pendingLoans.length > 0 && (
                <span className="ml-auto rounded-full bg-amber-200 px-2 py-0.5 text-[10px] font-normal text-amber-800">
                  {pendingLoans.length}
                </span>
              )}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {loadingPending && (
              <div className="space-y-3 p-4">
                {[1, 2].map(i => <Skeleton key={i} className="h-16 w-full" />)}
              </div>
            )}

            {!loadingPending && pendingLoans.length === 0 && (
              <div className="flex items-center gap-2 px-4 py-8 text-sm text-muted-foreground">
                <CheckCircle2 size={16} className="text-emerald-500" />
                Aucune demande en attente.
              </div>
            )}

            {!loadingPending && pendingLoans.length > 0 && (
              <div className="divide-y divide-border">
                {pendingLoans.map(loan => (
                  <LoanRow key={loan.id} loan={loan} onDecision={openDecision} />
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* ── All loans tab ────────────────────────────────────────────────── */}
      {tab === 'all' && (
        <Card>
          <CardHeader className="border-b pb-4">
            <CardTitle className="text-sm font-semibold">
              {loansPage ? `${loansPage.totalElements} prêt${loansPage.totalElements > 1 ? 's' : ''}` : 'Tous les prêts'}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {loadingAll
              ? <div className="space-y-3 p-4">{[1,2,3,4].map(i => <Skeleton key={i} className="h-14 w-full" />)}</div>
              : !loansPage?.content.length
                ? (
                  <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
                    <Banknote size={28} className="opacity-40" />
                    <p className="text-sm">Aucun prêt.</p>
                  </div>
                )
                : (
                  <>
                    <div className="divide-y divide-border">
                      {loansPage.content.map(loan => (
                        <LoanRow
                          key={loan.id}
                          loan={loan}
                          onDecision={loan.status === 'PENDING' ? openDecision : undefined}
                        />
                      ))}
                    </div>

                    {/* Pagination */}
                    {loansPage.totalPages > 1 && (
                      <>
                        <Separator />
                        <div className="flex items-center justify-between px-4 py-3 text-xs text-muted-foreground">
                          <span>Page {loansPage.number + 1} / {loansPage.totalPages}</span>
                          <div className="flex gap-2">
                            <Button
                              variant="outline" size="sm" className="h-7 text-xs"
                              disabled={loansPage.first} onClick={() => setPage(p => p - 1)}
                            >
                              Précédent
                            </Button>
                            <Button
                              variant="outline" size="sm" className="h-7 text-xs"
                              disabled={loansPage.last} onClick={() => setPage(p => p + 1)}
                            >
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
      )}

      {/* Decision dialog */}
      {decisionLoan && (
        <DecisionDialog
          loan={decisionLoan}
          action={decisionAction}
          open={!!decisionLoan}
          onClose={() => setDecisionLoan(null)}
        />
      )}
    </div>
  )
}
