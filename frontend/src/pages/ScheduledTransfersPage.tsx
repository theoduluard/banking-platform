import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { ScheduledTransfer } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button, buttonVariants } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogClose,
} from '@/components/ui/dialog'
import { ArrowLeft, CalendarClock, ArrowLeftRight, Trash2, RefreshCw } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useState } from 'react'

// ── Formatters ─────────────────────────────────────────────────────────────────

function formatAmount(amount: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount)
}

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium' }).format(new Date(iso))
}

// ── Row component ──────────────────────────────────────────────────────────────

function ScheduledTransferRow({
  transfer,
  onCancel,
  cancelling,
}: {
  transfer: ScheduledTransfer
  onCancel: () => void
  cancelling: boolean
}) {
  const [open, setOpen] = useState(false)

  return (
    <div className="flex items-center gap-4 py-4">
      {/* Icon */}
      <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
        <CalendarClock size={18} />
      </div>

      {/* Info */}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="text-sm font-semibold tabular-nums">
            {formatAmount(transfer.amount, transfer.currency)}
          </p>
          <span className={cn(
            'inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium',
            'bg-primary/10 text-primary border-primary/20',
          )}>
            {transfer.frequency === 'MONTHLY' ? 'Mensuel' : 'Hebdomadaire'}
          </span>
        </div>
        {transfer.description && (
          <p className="truncate text-xs text-muted-foreground/80 italic mt-0.5">
            {transfer.description}
          </p>
        )}
        <div className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
          <RefreshCw size={10} />
          <span>Prochaine exécution : <strong>{formatDate(transfer.nextExecutionDate)}</strong></span>
        </div>
        <div className="mt-0.5 flex items-center gap-3 text-[10px] text-muted-foreground/60 font-mono">
          <span title="Compte source">De : ···{transfer.fromAccountId.slice(-8)}</span>
          <ArrowLeftRight size={8} />
          <span title="Compte destinataire">Vers : ···{transfer.toAccountId.slice(-8)}</span>
        </div>
      </div>

      {/* Cancel button */}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogTrigger
          render={
            <Button
              variant="ghost"
              size="sm"
              className="shrink-0 gap-1.5 text-muted-foreground hover:text-destructive hover:bg-red-50"
            />
          }
        >
          <Trash2 size={14} />
          <span className="hidden sm:inline">Annuler</span>
        </DialogTrigger>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>Annuler ce virement programmé ?</DialogTitle>
            <DialogDescription>
              Le virement de <strong>{formatAmount(transfer.amount, transfer.currency)}</strong>{' '}
              ({transfer.frequency === 'MONTHLY' ? 'mensuel' : 'hebdomadaire'}) sera définitivement supprimé.
              Les exécutions déjà réalisées ne sont pas affectées.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose render={<Button variant="outline" />}>
              Garder
            </DialogClose>
            <Button
              variant="destructive"
              disabled={cancelling}
              onClick={() => { onCancel(); setOpen(false) }}
            >
              {cancelling ? 'Annulation…' : 'Confirmer l\'annulation'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

// ── Page ───────────────────────────────────────────────────────────────────────

export default function ScheduledTransfersPage() {
  const userId      = getUserIdFromToken()
  const queryClient = useQueryClient()

  const { data: transfers = [], isLoading } = useQuery<ScheduledTransfer[]>({
    queryKey: ['scheduled-transfers', userId],
    queryFn:  () => api
      .get<ScheduledTransfer[]>('/api/v1/scheduled-transfers', {
        headers: { 'X-User-Id': userId },
      })
      .then(r => r.data),
    enabled: !!userId,
  })

  const cancelMutation = useMutation({
    mutationFn: (id: string) =>
      api.delete(`/api/v1/scheduled-transfers/${id}`, {
        headers: { 'X-User-Id': userId },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scheduled-transfers', userId] })
      toast.success('Virement programmé annulé.')
    },
    onError: () => toast.error('Impossible d\'annuler ce virement.'),
  })

  return (
    <div className="mx-auto max-w-2xl space-y-6">

      {/* Back */}
      <Link
        to="/dashboard"
        className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }), '-ml-2 gap-1.5 text-muted-foreground')}
      >
        <ArrowLeft size={14} />
        <span>Retour</span>
      </Link>

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Virements programmés</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Vos virements récurrents actifs. Exécutés automatiquement à chaque échéance.
          </p>
        </div>
        <Link
          to="/transfer"
          className={cn(buttonVariants({ size: 'sm' }), 'gap-1.5')}
        >
          <CalendarClock size={14} />
          <span>Nouveau</span>
        </Link>
      </div>

      {/* List */}
      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="text-sm font-semibold">
            Virements actifs
            {transfers.length > 0 && (
              <span className="ml-2 inline-flex size-5 items-center justify-center rounded-full bg-primary/10 text-[10px] font-bold text-primary">
                {transfers.length}
              </span>
            )}
          </CardTitle>
        </CardHeader>

        <CardContent className="p-0">
          {isLoading && (
            <div className="space-y-4 p-4">
              {[1, 2].map(i => <Skeleton key={i} className="h-16 w-full" />)}
            </div>
          )}

          {!isLoading && transfers.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-14 text-center text-muted-foreground">
              <CalendarClock size={32} className="text-muted-foreground/40" />
              <div>
                <p className="text-sm font-medium">Aucun virement programmé</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Créez-en un depuis la page Virement en activant l'option "Programmer".
                </p>
              </div>
              <Link
                to="/transfer"
                className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'mt-1 gap-1.5')}
              >
                <CalendarClock size={13} />
                Créer un virement programmé
              </Link>
            </div>
          )}

          {!isLoading && transfers.length > 0 && (
            <div className="divide-y divide-border px-4">
              {transfers.map(t => (
                <ScheduledTransferRow
                  key={t.id}
                  transfer={t}
                  onCancel={() => cancelMutation.mutate(t.id)}
                  cancelling={cancelMutation.isPending}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
