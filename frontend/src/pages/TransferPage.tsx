import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useState, useEffect } from 'react'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account, Beneficiary } from '@/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
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
import { cn } from '@/lib/utils'
import {
  ArrowLeftRight, ArrowLeft, ArrowDown,
  Info, AlertTriangle, Users, CreditCard, Keyboard,
  Loader2, CheckCircle2, XCircle,
} from 'lucide-react'

// ── Types & schema ─────────────────────────────────────────────────────────────

type DestType = 'own' | 'beneficiary' | 'manual'

const schema = z.object({
  fromAccountId: z.string().uuid('Compte source requis'),
  amount:        z.number({ message: 'Montant invalide' }).positive('Montant invalide'),
  description:   z.string().max(255, 'Maximum 255 caractères').optional(),
})
type FormData = z.infer<typeof schema>

// ── Helpers ────────────────────────────────────────────────────────────────────

function formatAmount(amount: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount)
}

// ── Component ──────────────────────────────────────────────────────────────────

export default function TransferPage() {
  const navigate       = useNavigate()
  const [searchParams] = useSearchParams()
  const userId         = getUserIdFromToken()

  // ── Idempotency key ──────────────────────────────────────────────────────
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())

  // ── Destination state ────────────────────────────────────────────────────
  const [destType,       setDestType]       = useState<DestType>('own')
  const [toAccountId,    setToAccountId]    = useState<string | null>(null)
  const [destLabel,      setDestLabel]      = useState<string>('')   // display in modal
  const [manualIban,     setManualIban]     = useState('')
  const [ibanStatus,     setIbanStatus]     = useState<'idle'|'resolving'|'ok'|'error'>('idle')
  const [ibanError,      setIbanError]      = useState('')
  const [selectedBeneficiaryIban, setSelectedBeneficiaryIban] = useState<string>('')

  // ── Modal ────────────────────────────────────────────────────────────────
  const [pendingTransfer, setPendingTransfer] = useState<FormData | null>(null)
  const [isSubmitting,    setIsSubmitting]    = useState(false)

  // ── Queries ──────────────────────────────────────────────────────────────
  const { data: accounts = [] } = useQuery<Account[]>({
    queryKey: ['accounts', userId],
    queryFn:  () => api
      .get<Account[]>('/api/v1/accounts', { headers: { 'X-User-Id': userId } })
      .then(r => r.data.filter(a => a.status === 'ACTIVE')),
    enabled: !!userId,
  })

  const { data: beneficiaries = [] } = useQuery<Beneficiary[]>({
    queryKey: ['beneficiaries', userId],
    queryFn:  () => api.get<Beneficiary[]>('/api/v1/beneficiaries').then(r => r.data),
    enabled:  !!userId,
  })

  // ── Pre-fill from /beneficiaries "Virer" button ──────────────────────────
  useEffect(() => {
    const iban = searchParams.get('iban')
    const name = searchParams.get('name')
    if (iban && name) {
      setDestType('manual')
      setManualIban(iban)
      setDestLabel(name)
      resolveIban(iban, name)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // ── Form ──────────────────────────────────────────────────────────────────
  const { control, register, handleSubmit, watch, formState: { errors } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const fromId      = watch('fromAccountId')
  const fromAccount = accounts.find(a => a.id === fromId)

  // ── IBAN resolution ───────────────────────────────────────────────────────

  async function resolveIban(iban: string, label?: string) {
    const clean = iban.toUpperCase().replace(/\s/g, '')
    if (clean.length < 8) return

    setIbanStatus('resolving')
    setIbanError('')
    setToAccountId(null)

    try {
      const { data } = await api.get<{ accountId: string }>(`/api/v1/accounts/iban/${clean}`)
      setToAccountId(data.accountId)
      setDestLabel(label ?? clean)
      setIbanStatus('ok')
    } catch {
      setIbanStatus('error')
      setIbanError('IBAN introuvable dans notre système')
      setToAccountId(null)
    }
  }

  // ── Destination type switch ───────────────────────────────────────────────

  function switchDestType(type: DestType) {
    setDestType(type)
    setToAccountId(null)
    setIbanStatus('idle')
    setIbanError('')
    setManualIban('')
    setSelectedBeneficiaryIban('')
    setDestLabel('')
  }

  // ── Step 1: validate form → open modal ───────────────────────────────────

  function onRequestConfirm(data: FormData) {
    if (!toAccountId) {
      toast.error('Veuillez sélectionner un compte destinataire valide.')
      return
    }
    if (data.fromAccountId === toAccountId) {
      toast.error('Le compte source et le compte destinataire doivent être différents.')
      return
    }
    setPendingTransfer(data)
  }

  // ── Step 2: modal confirmed → API call ───────────────────────────────────

  async function onConfirm() {
    if (!pendingTransfer || !toAccountId) return
    setIsSubmitting(true)
    try {
      await api.post('/api/v1/transactions/transfer', {
        fromAccountId: pendingTransfer.fromAccountId,
        toAccountId,
        amount:        pendingTransfer.amount,
        description:   pendingTransfer.description,
      }, {
        headers: {
          'X-User-Id':       userId,
          'Idempotency-Key': idempotencyKey,
        },
      })
      toast.success('Virement initié ! Traitement en cours.')
      setIdempotencyKey(crypto.randomUUID())
      navigate('/dashboard')
    } catch {
      toast.error('Le virement a échoué. Vérifiez votre solde.')
    } finally {
      setIsSubmitting(false)
      setPendingTransfer(null)
    }
  }

  // ── Destination tabs ──────────────────────────────────────────────────────

  const destTabs: { type: DestType; label: string; icon: React.ElementType }[] = [
    { type: 'own',         label: 'Mes comptes',    icon: CreditCard },
    { type: 'beneficiary', label: 'Bénéficiaires',  icon: Users },
    { type: 'manual',      label: 'IBAN manuel',    icon: Keyboard },
  ]

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <Button variant="ghost" size="sm" asChild className="-ml-2 gap-1.5 text-muted-foreground">
        <Link to="/dashboard"><ArrowLeft size={14} /> Retour</Link>
      </Button>

      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Effectuer un virement</h1>
        <p className="mt-1 text-sm text-muted-foreground">Les virements sont traités de manière asynchrone.</p>
      </div>

      <form onSubmit={handleSubmit(onRequestConfirm)} className="space-y-5">

        {/* ── Source ──────────────────────────────────────────────────────── */}
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

        {/* Arrow */}
        <div className="flex items-center justify-center">
          <div className="flex size-9 items-center justify-center rounded-full border bg-muted">
            <ArrowDown size={16} className="text-muted-foreground" />
          </div>
        </div>

        {/* ── Destination type tabs ────────────────────────────────────────── */}
        <div className="space-y-3">
          <Label className="text-sm font-medium">Destinataire</Label>

          <div className="flex rounded-lg border bg-muted/30 p-1 gap-1">
            {destTabs.map(({ type, label, icon: Icon }) => (
              <button
                key={type}
                type="button"
                onClick={() => switchDestType(type)}
                className={cn(
                  'flex flex-1 items-center justify-center gap-1.5 rounded-md px-2 py-1.5 text-xs font-medium transition-all',
                  destType === type
                    ? 'bg-background text-foreground shadow-sm'
                    : 'text-muted-foreground hover:text-foreground',
                )}
              >
                <Icon size={13} />
                {label}
              </button>
            ))}
          </div>

          {/* ── Own accounts ────────────────────────────────────────────── */}
          {destType === 'own' && (
            <Select
              value={toAccountId ?? ''}
              onValueChange={id => {
                setToAccountId(id)
                const acc = accounts.find(a => a.id === id)
                setDestLabel(acc ? `${acc.type === 'CHECKING' ? 'Courant' : 'Épargne'} ···· ${acc.iban.slice(-4)}` : id)
              }}
            >
              <SelectTrigger className="h-11">
                <SelectValue placeholder="Choisissez un compte…" />
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

          {/* ── Beneficiaries ────────────────────────────────────────────── */}
          {destType === 'beneficiary' && (
            <>
              {beneficiaries.length === 0 ? (
                <Card className="border-dashed">
                  <CardContent className="flex items-center justify-between py-3 px-4">
                    <p className="text-sm text-muted-foreground">Aucun bénéficiaire enregistré.</p>
                    <Button size="sm" variant="outline" asChild className="gap-1.5 text-xs">
                      <Link to="/beneficiaries">Ajouter</Link>
                    </Button>
                  </CardContent>
                </Card>
              ) : (
                <Select
                  value={selectedBeneficiaryIban}
                  onValueChange={iban => {
                    setSelectedBeneficiaryIban(iban)
                    const b = beneficiaries.find(b => b.iban === iban)
                    resolveIban(iban, b?.name)
                  }}
                >
                  <SelectTrigger className="h-11">
                    <SelectValue placeholder="Choisissez un bénéficiaire…" />
                  </SelectTrigger>
                  <SelectContent>
                    {beneficiaries.map(b => (
                      <SelectItem key={b.id} value={b.iban}>
                        <div className="flex items-center gap-3">
                          <span className="font-medium">{b.name}</span>
                          <span className="font-mono text-xs text-muted-foreground">
                            {b.iban.slice(0, 4)} ···· {b.iban.slice(-4)}
                          </span>
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              <IbanStatusBadge status={ibanStatus} error={ibanError} />
            </>
          )}

          {/* ── Manual IBAN ──────────────────────────────────────────────── */}
          {destType === 'manual' && (
            <>
              <div className="flex gap-2">
                <Input
                  value={manualIban}
                  onChange={e => {
                    setManualIban(e.target.value)
                    setIbanStatus('idle')
                    setToAccountId(null)
                  }}
                  onBlur={() => manualIban.trim() && resolveIban(manualIban)}
                  placeholder="FR76 3000 6000 0112 3456 7890 189"
                  className="h-11 font-mono text-sm uppercase"
                />
                <Button
                  type="button"
                  variant="outline"
                  className="h-11 shrink-0"
                  onClick={() => resolveIban(manualIban)}
                  disabled={ibanStatus === 'resolving' || !manualIban.trim()}
                >
                  {ibanStatus === 'resolving'
                    ? <Loader2 size={15} className="animate-spin" />
                    : 'Vérifier'}
                </Button>
              </div>
              <IbanStatusBadge status={ibanStatus} error={ibanError} />
            </>
          )}
        </div>

        {/* ── Amount ──────────────────────────────────────────────────────── */}
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

        {/* ── Description ─────────────────────────────────────────────────── */}
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

        {/* ── Info ────────────────────────────────────────────────────────── */}
        <Card className="border-muted bg-muted/40">
          <CardContent className="flex items-start gap-2.5 py-3 px-4">
            <Info size={14} className="mt-0.5 shrink-0 text-muted-foreground" />
            <p className="text-xs leading-relaxed text-muted-foreground">
              Le virement sera traité via notre infrastructure Kafka. Le solde sera mis à jour sous quelques secondes.
            </p>
          </CardContent>
        </Card>

        <Button
          type="submit"
          className="h-11 w-full gap-2 text-sm font-medium"
          disabled={!toAccountId}
        >
          <ArrowLeftRight size={15} />
          Vérifier et confirmer
        </Button>
      </form>

      {/* ── Confirmation modal ─────────────────────────────────────────────── */}
      <Dialog
        open={!!pendingTransfer}
        onOpenChange={o => { if (!o && !isSubmitting) setPendingTransfer(null) }}
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
              {/* Source */}
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

              {/* Destination */}
              <div className="rounded-lg border bg-muted/40 px-4 py-3">
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Vers</p>
                <p className="text-sm font-medium">{destLabel || toAccountId}</p>
              </div>

              {/* Amount */}
              <div className="rounded-lg border-2 border-primary/20 bg-primary/5 px-4 py-3 text-center">
                <p className="mb-0.5 text-xs font-medium text-muted-foreground">Montant</p>
                <p className="text-2xl font-bold tabular-nums text-primary">
                  {formatAmount(pendingTransfer.amount, 'EUR')}
                </p>
              </div>

              {/* Description */}
              {pendingTransfer.description && (
                <div className="rounded-lg border bg-muted/40 px-4 py-3">
                  <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Description</p>
                  <p className="text-sm">{pendingTransfer.description}</p>
                </div>
              )}

              {/* Warning */}
              <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5">
                <AlertTriangle size={14} className="mt-0.5 shrink-0 text-amber-600" />
                <p className="text-xs leading-relaxed text-amber-700">
                  Cette opération est <strong>irréversible</strong>. Vérifiez le destinataire avant de confirmer.
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
            <Button onClick={onConfirm} disabled={isSubmitting} className="flex-1 gap-2">
              <ArrowLeftRight size={14} />
              {isSubmitting ? 'Envoi…' : 'Confirmer'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

// ── Status badge for IBAN resolution ──────────────────────────────────────────

function IbanStatusBadge({ status, error }: { status: string; error: string }) {
  if (status === 'resolving') {
    return (
      <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <Loader2 size={11} className="animate-spin" /> Vérification en cours…
      </p>
    )
  }
  if (status === 'ok') {
    return (
      <p className="flex items-center gap-1.5 text-xs text-emerald-600">
        <CheckCircle2 size={12} /> IBAN valide — compte trouvé
      </p>
    )
  }
  if (status === 'error') {
    return (
      <p className="flex items-center gap-1.5 text-xs text-destructive">
        <XCircle size={12} /> {error}
      </p>
    )
  }
  return null
}
