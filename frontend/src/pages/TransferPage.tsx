import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useState } from 'react'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account } from '@/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { ArrowLeftRight, ArrowLeft, ArrowDown, Info, AlertTriangle } from 'lucide-react'

function formatAmount(amount: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount)
}

const schema = z.object({
  fromAccountId: z.string().uuid('Compte source requis'),
  toAccountId:   z.string().uuid('Compte destinataire requis'),
  amount:        z.number({ message: 'Montant invalide' }).positive('Montant invalide'),
  description:   z.string().max(255, 'Maximum 255 caractères').optional(),
}).refine(d => d.fromAccountId !== d.toAccountId, {
  message: 'Les comptes source et destinataire doivent être différents',
  path: ['toAccountId'],
})

type FormData = z.infer<typeof schema>

export default function TransferPage() {
  const navigate = useNavigate()
  const userId   = getUserIdFromToken()

  // ── Idempotency key ────────────────────────────────────────────────────────
  // One UUID per form instance. Generated once when the page loads.
  // • On SUCCESS  → regenerate (so the next transfer gets a fresh key)
  // • On FAILURE  → keep the same key (retry is safe, server is idempotent)
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())

  // ── Confirmation modal state ───────────────────────────────────────────────
  // pendingTransfer holds validated form data while the modal is open.
  // It is null when the modal is closed.
  const [pendingTransfer, setPendingTransfer] = useState<FormData | null>(null)
  const [isSubmitting,    setIsSubmitting]    = useState(false)

  // ── Data ───────────────────────────────────────────────────────────────────
  const { data: accounts = [] } = useQuery<Account[]>({
    queryKey: ['accounts', userId],
    queryFn:  () => api
      .get<Account[]>('/api/v1/accounts', { headers: { 'X-User-Id': userId } })
      .then(r => r.data.filter(a => a.status === 'ACTIVE')),
    enabled: !!userId,
  })

  const { control, register, handleSubmit, watch, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const fromId      = watch('fromAccountId')
  const fromAccount = accounts.find(a => a.id === fromId)

  // ── Step 1 : form submit → open confirmation modal ─────────────────────────
  // Zod validation runs here via handleSubmit.
  // If valid, we store the data and open the recap modal — NO API call yet.
  function onRequestConfirm(data: FormData) {
    setPendingTransfer(data)
  }

  // ── Step 2 : user clicks "Confirmer" in modal → API call ──────────────────
  async function onConfirm() {
    if (!pendingTransfer) return
    setIsSubmitting(true)
    try {
      await api.post('/api/v1/transactions/transfer', pendingTransfer, {
        headers: {
          'X-User-Id':       userId,
          // The idempotency key ties this exact transfer attempt to one DB row.
          // If this request is replayed (network retry, double-click on mobile, etc.)
          // the server will return the existing transaction instead of creating a new one.
          'Idempotency-Key': idempotencyKey,
        },
      })
      toast.success('Virement initié ! Traitement en cours.')
      // SUCCESS → generate a new key so the NEXT transfer is independent
      setIdempotencyKey(crypto.randomUUID())
      navigate('/dashboard')
    } catch {
      toast.error('Le virement a échoué. Vérifiez votre solde.')
      // FAILURE → keep the same key: if the first attempt actually went through
      // silently (e.g. network timeout after server processed it), retrying with
      // the same key is safe — the server won't create a second transaction.
    } finally {
      setIsSubmitting(false)
      setPendingTransfer(null)
    }
  }

  // Short display of the destination UUID (e.g. "550e8400…6655")
  const toAccountDisplay = pendingTransfer
    ? `${pendingTransfer.toAccountId.slice(0, 8)}…${pendingTransfer.toAccountId.slice(-4)}`
    : ''

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <Button variant="ghost" size="sm" asChild className="-ml-2 gap-1.5 text-muted-foreground">
        <Link to="/dashboard"><ArrowLeft size={14} /> Retour</Link>
      </Button>

      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Effectuer un virement</h1>
        <p className="mt-1 text-sm text-muted-foreground">Les virements sont traités de manière asynchrone.</p>
      </div>

      {/* ── Transfer form ─────────────────────────────────────────────────── */}
      <form onSubmit={handleSubmit(onRequestConfirm)} className="space-y-5">

        {/* From */}
        <div className="space-y-2">
          <Label className="text-sm font-medium">Compte source</Label>
          <Controller
            name="fromAccountId"
            control={control}
            render={({ field }) => (
              <Select onValueChange={field.onChange} value={field.value}>
                <SelectTrigger className="h-11">
                  <SelectValue placeholder="Choisissez votre compte…" />
                </SelectTrigger>
                <SelectContent>
                  {accounts.map(a => (
                    <SelectItem key={a.id} value={a.id}>
                      <div className="flex items-center justify-between gap-6">
                        <span className="text-sm">{a.type === 'CHECKING' ? 'Courant' : 'Épargne'}</span>
                        <span className="font-mono text-xs text-muted-foreground">{a.iban.slice(-8)}</span>
                        <span className="font-semibold tabular-nums">{formatAmount(a.balance, a.currency)}</span>
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
          {fromAccount && (
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Info size={11} />
              Solde disponible : <strong>{formatAmount(fromAccount.balance, fromAccount.currency)}</strong>
            </p>
          )}
          {errors.fromAccountId && <p className="text-xs text-destructive">{errors.fromAccountId.message}</p>}
        </div>

        {/* Visual arrow */}
        <div className="flex items-center justify-center">
          <div className="flex size-9 items-center justify-center rounded-full border bg-muted">
            <ArrowDown size={16} className="text-muted-foreground" />
          </div>
        </div>

        {/* To */}
        <div className="space-y-2">
          <Label htmlFor="toAccountId" className="text-sm font-medium">Compte destinataire (UUID)</Label>
          <Input
            id="toAccountId"
            {...register('toAccountId')}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            className="h-11 font-mono text-sm"
          />
          {errors.toAccountId && <p className="text-xs text-destructive">{errors.toAccountId.message}</p>}
        </div>

        {/* Amount */}
        <div className="space-y-2">
          <Label htmlFor="amount" className="text-sm font-medium">Montant</Label>
          <div className="relative">
            <Input
              id="amount"
              {...register('amount', { valueAsNumber: true })}
              type="number"
              min="0.01"
              step="0.01"
              placeholder="0,00"
              className="h-11 pr-14 tabular-nums text-base font-semibold"
            />
            <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-sm font-medium text-muted-foreground">
              EUR
            </span>
          </div>
          {errors.amount && <p className="text-xs text-destructive">{errors.amount.message}</p>}
        </div>

        {/* Description (optional) */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <Label htmlFor="description" className="text-sm font-medium">Description</Label>
            <span className="text-xs text-muted-foreground">Optionnel</span>
          </div>
          <Textarea
            id="description"
            {...register('description')}
            placeholder="Remboursement dîner, loyer juillet…"
            rows={2}
            className="resize-none text-sm"
          />
          {errors.description && <p className="text-xs text-destructive">{errors.description.message}</p>}
        </div>

        {/* Info notice */}
        <Card className="border-muted bg-muted/40">
          <CardContent className="flex items-start gap-2.5 py-3 px-4">
            <Info size={14} className="mt-0.5 shrink-0 text-muted-foreground" />
            <p className="text-xs leading-relaxed text-muted-foreground">
              Le virement sera traité via notre infrastructure Kafka. Le solde sera mis à jour sous quelques secondes.
            </p>
          </CardContent>
        </Card>

        {/* Submit opens the modal — does NOT send any request */}
        <Button type="submit" className="h-11 w-full gap-2 text-sm font-medium">
          <ArrowLeftRight size={15} />
          Vérifier et confirmer
        </Button>
      </form>

      {/* ── Confirmation modal ────────────────────────────────────────────── */}
      <Dialog
        open={!!pendingTransfer}
        onOpenChange={open => { if (!open && !isSubmitting) setPendingTransfer(null) }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ArrowLeftRight size={18} className="text-primary" />
              Confirmer le virement
            </DialogTitle>
            <DialogDescription>
              Vérifiez les détails ci-dessous avant d'envoyer.
            </DialogDescription>
          </DialogHeader>

          {pendingTransfer && (
            <div className="space-y-3 py-1">
              {/* From account */}
              <div className="rounded-lg border bg-muted/40 px-4 py-3">
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Depuis</p>
                {fromAccount ? (
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">
                      {fromAccount.type === 'CHECKING' ? 'Compte courant' : 'Compte épargne'}
                    </span>
                    <span className="font-mono text-xs text-muted-foreground">
                      ···· {fromAccount.iban.slice(-8)}
                    </span>
                  </div>
                ) : (
                  <p className="font-mono text-xs">{pendingTransfer.fromAccountId}</p>
                )}
              </div>

              {/* To account */}
              <div className="rounded-lg border bg-muted/40 px-4 py-3">
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Vers</p>
                <p className="font-mono text-sm">{toAccountDisplay}</p>
              </div>

              {/* Amount — visually prominent */}
              <div className="rounded-lg border-2 border-primary/20 bg-primary/5 px-4 py-3 text-center">
                <p className="mb-0.5 text-xs font-medium text-muted-foreground">Montant</p>
                <p className="text-2xl font-bold tabular-nums text-primary">
                  {formatAmount(pendingTransfer.amount, 'EUR')}
                </p>
              </div>

              {/* Description — only shown if provided */}
              {pendingTransfer.description && (
                <div className="rounded-lg border bg-muted/40 px-4 py-3">
                  <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Description</p>
                  <p className="text-sm">{pendingTransfer.description}</p>
                </div>
              )}

              {/* Irreversibility warning */}
              <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5">
                <AlertTriangle size={14} className="mt-0.5 shrink-0 text-amber-600" />
                <p className="text-xs leading-relaxed text-amber-700">
                  Cette opération est <strong>irréversible</strong>. Vérifiez le compte destinataire avant de confirmer.
                </p>
              </div>
            </div>
          )}

          <DialogFooter className="gap-2 sm:gap-2">
            <Button
              variant="outline"
              onClick={() => setPendingTransfer(null)}
              disabled={isSubmitting}
              className="flex-1"
            >
              Annuler
            </Button>
            <Button
              onClick={onConfirm}
              disabled={isSubmitting}
              className="flex-1 gap-2"
            >
              <ArrowLeftRight size={14} />
              {isSubmitting ? 'Envoi en cours…' : 'Confirmer'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
