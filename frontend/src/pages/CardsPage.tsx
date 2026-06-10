import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { CreditCard, Snowflake, Flame, PlusCircle, Lock } from 'lucide-react'

// ── Types ─────────────────────────────────────────────────────────────────────

interface BankCard {
  id: string
  maskedNumber: string
  cardholderName: string
  cardType: 'VIRTUAL' | 'PHYSICAL'
  status: 'ACTIVE' | 'FROZEN' | 'CANCELLED'
  expiryMonth: number
  expiryYear: number
  spendingLimit?: number
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function statusBadge(status: BankCard['status']) {
  switch (status) {
    case 'ACTIVE':
      return <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100 border-0">Actif</Badge>
    case 'FROZEN':
      return <Badge className="bg-orange-100 text-orange-700 hover:bg-orange-100 border-0">Gelé</Badge>
    case 'CANCELLED':
      return <Badge className="bg-red-100 text-red-700 hover:bg-red-100 border-0">Annulé</Badge>
  }
}

function typeBadge(type: BankCard['cardType']) {
  return type === 'VIRTUAL'
    ? <Badge variant="outline" className="text-xs">Virtuelle</Badge>
    : <Badge variant="outline" className="text-xs">Physique</Badge>
}

function formatExpiry(month: number, year: number) {
  return `${String(month).padStart(2, '0')}/${year}`
}

// ── Card Item ─────────────────────────────────────────────────────────────────

function CardItem({
  card,
  onFreeze,
  onUnfreeze,
  isActing,
}: {
  card: BankCard
  onFreeze: (id: string) => void
  onUnfreeze: (id: string) => void
  isActing: boolean
}) {
  const isFrozen    = card.status === 'FROZEN'
  const isCancelled = card.status === 'CANCELLED'

  return (
    <Card className="flex flex-col gap-4 p-5">
      {/* Top row */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10">
          <CreditCard size={18} className="text-primary" />
        </div>
        <div className="flex flex-wrap gap-1.5 justify-end">
          {typeBadge(card.cardType)}
          {statusBadge(card.status)}
        </div>
      </div>

      {/* Card number */}
      <p className="font-mono text-base tracking-widest text-foreground">
        {card.maskedNumber}
      </p>

      {/* Details */}
      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium truncate">{card.cardholderName}</p>
        <p className="text-xs text-muted-foreground">
          Expire&nbsp;{formatExpiry(card.expiryMonth, card.expiryYear)}
        </p>
        {card.spendingLimit != null && (
          <p className="text-xs text-muted-foreground">
            Plafond&nbsp;:&nbsp;
            <span className="font-medium text-foreground">
              {card.spendingLimit.toLocaleString('fr-FR', { style: 'currency', currency: 'EUR' })}
            </span>
          </p>
        )}
      </div>

      {/* Actions */}
      {!isCancelled && (
        <div className="pt-1">
          {isFrozen ? (
            <Button
              size="sm"
              variant="outline"
              className="w-full gap-2"
              onClick={() => onUnfreeze(card.id)}
              disabled={isActing}
            >
              <Flame size={14} />
              Dégeler
            </Button>
          ) : (
            <Button
              size="sm"
              variant="outline"
              className="w-full gap-2 text-orange-600 hover:text-orange-700 hover:border-orange-300"
              onClick={() => onFreeze(card.id)}
              disabled={isActing}
            >
              <Snowflake size={14} />
              Geler
            </Button>
          )}
        </div>
      )}

      {isCancelled && (
        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <Lock size={12} />
          Carte annulée
        </div>
      )}
    </Card>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function CardsPage() {
  const userId      = getUserIdFromToken()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)

  // ── Form state ─────────────────────────────────────────────────────────────
  const [accountId, setAccountId]           = useState('')
  const [cardholderName, setCardholderName] = useState('')
  const [cardType, setCardType]             = useState<'VIRTUAL' | 'PHYSICAL'>('VIRTUAL')
  const [spendingLimit, setSpendingLimit]   = useState('')

  // ── Fetch accounts (for dropdown) ──────────────────────────────────────────
  const { data: accounts = [] } = useQuery<Account[]>({
    queryKey: ['accounts'],
    queryFn: () => api.get<Account[]>('/api/v1/accounts').then(r => r.data),
    enabled: !!userId,
  })
  const activeAccounts = accounts.filter(a => a.status === 'ACTIVE')

  // ── Fetch cards ────────────────────────────────────────────────────────────
  const { data: cards = [], isLoading } = useQuery<BankCard[]>({
    queryKey: ['cards', userId],
    queryFn:  () => api.get<BankCard[]>('/api/v1/cards').then(r => r.data),
    enabled:  !!userId,
  })

  // ── Freeze ─────────────────────────────────────────────────────────────────
  const freezeMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/v1/cards/${id}/freeze`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cards'] })
      toast.success('Carte gelée')
    },
    onError: () => toast.error('Impossible de geler la carte'),
  })

  // ── Unfreeze ───────────────────────────────────────────────────────────────
  const unfreezeMutation = useMutation({
    mutationFn: (id: string) => api.post(`/api/v1/cards/${id}/unfreeze`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cards'] })
      toast.success('Carte dégelée')
    },
    onError: () => toast.error('Impossible de dégeler la carte'),
  })

  // ── Create card ────────────────────────────────────────────────────────────
  const createMutation = useMutation({
    mutationFn: (body: {
      accountId: string
      cardholderName: string
      cardType: 'VIRTUAL' | 'PHYSICAL'
      spendingLimit?: number
    }) => api.post<BankCard>('/api/v1/cards', body).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cards'] })
      toast.success('Carte créée')
      closeDialog()
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      const msg = err.response?.data?.error ?? 'Erreur lors de la création'
      toast.error(msg)
    },
  })

  function closeDialog() {
    setOpen(false)
    setAccountId('')
    setCardholderName('')
    setCardType('VIRTUAL')
    setSpendingLimit('')
  }

  function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!accountId || !cardholderName.trim()) {
      toast.error('Veuillez remplir tous les champs obligatoires')
      return
    }
    const body: Parameters<typeof createMutation.mutate>[0] = {
      accountId,
      cardholderName: cardholderName.trim(),
      cardType,
    }
    if (spendingLimit !== '') {
      const parsed = parseFloat(spendingLimit)
      if (!isNaN(parsed) && parsed > 0) body.spendingLimit = parsed
    }
    createMutation.mutate(body)
  }

  const isActingOnCard = freezeMutation.isPending || unfreezeMutation.isPending

  return (
    <div className="space-y-6">

      {/* ── Header ────────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Mes cartes</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Gérez vos cartes bancaires virtuelles et physiques.
          </p>
        </div>
        <Button size="sm" className="gap-1.5" onClick={() => setOpen(true)}>
          <PlusCircle size={15} />
          Nouvelle carte
        </Button>
      </div>

      {/* ── Loading ───────────────────────────────────────────────────────── */}
      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3].map(i => (
            <Skeleton key={i} className="h-56 w-full rounded-xl" />
          ))}
        </div>
      )}

      {/* ── Empty state ───────────────────────────────────────────────────── */}
      {!isLoading && cards.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-14 text-center text-muted-foreground">
            <div className="flex size-14 items-center justify-center rounded-2xl bg-muted">
              <CreditCard size={24} className="opacity-50" />
            </div>
            <div>
              <p className="text-sm font-medium">Aucune carte</p>
              <p className="mt-0.5 text-xs">Créez votre première carte pour commencer.</p>
            </div>
            <Button size="sm" variant="outline" className="gap-1.5 mt-1" onClick={() => setOpen(true)}>
              <PlusCircle size={14} />
              Nouvelle carte
            </Button>
          </CardContent>
        </Card>
      )}

      {/* ── Grid ─────────────────────────────────────────────────────────── */}
      {!isLoading && cards.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {cards.map(card => (
            <CardItem
              key={card.id}
              card={card}
              onFreeze={id => freezeMutation.mutate(id)}
              onUnfreeze={id => unfreezeMutation.mutate(id)}
              isActing={isActingOnCard}
            />
          ))}
        </div>
      )}

      {/* ── Create dialog ─────────────────────────────────────────────────── */}
      <Dialog open={open} onOpenChange={o => { if (!createMutation.isPending) setOpen(o) }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <CreditCard size={18} className="text-primary" />
              Nouvelle carte
            </DialogTitle>
            <DialogDescription>
              Renseignez les informations pour créer votre carte.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreate} className="space-y-4 py-1">
            <div className="space-y-2">
              <Label htmlFor="accountId">Compte</Label>
              <select
                id="accountId"
                value={accountId}
                onChange={e => setAccountId(e.target.value)}
                required
                className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:opacity-50"
              >
                <option value="" disabled>Sélectionner un compte…</option>
                {activeAccounts.map(a => (
                  <option key={a.id} value={a.id}>
                    {a.type === 'CHECKING' ? 'Compte courant' : 'Compte épargne'}
                    {' — '}
                    {a.iban.slice(0, 8)}···{a.iban.slice(-4)}
                  </option>
                ))}
                {activeAccounts.length === 0 && (
                  <option value="" disabled>Aucun compte actif disponible</option>
                )}
              </select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="cardholderName">Titulaire</Label>
              <Input
                id="cardholderName"
                value={cardholderName}
                onChange={e => setCardholderName(e.target.value)}
                placeholder="Prénom NOM"
                className="h-11"
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="cardType">Type de carte</Label>
              <select
                id="cardType"
                value={cardType}
                onChange={e => setCardType(e.target.value as 'VIRTUAL' | 'PHYSICAL')}
                className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
              >
                <option value="VIRTUAL">Virtuelle</option>
                <option value="PHYSICAL">Physique</option>
              </select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="spendingLimit">
                Plafond de dépenses&nbsp;
                <span className="text-muted-foreground font-normal">(optionnel, €)</span>
              </Label>
              <Input
                id="spendingLimit"
                type="number"
                min="0"
                step="0.01"
                value={spendingLimit}
                onChange={e => setSpendingLimit(e.target.value)}
                placeholder="500"
                className="h-11"
              />
            </div>

            <DialogFooter className="gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={closeDialog}
                disabled={createMutation.isPending}
                className="flex-1"
              >
                Annuler
              </Button>
              <Button type="submit" disabled={createMutation.isPending} className="flex-1 gap-2">
                <PlusCircle size={14} />
                {createMutation.isPending ? 'Création…' : 'Créer'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
