import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
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
import { Calculator, Clock, CheckCircle2, XCircle, TrendingUp } from 'lucide-react'

// ── Types ─────────────────────────────────────────────────────────────────────

interface SimulationResult {
  monthlyPayment: number
  totalRepayment: number
  totalInterest: number
  interestRate: number
}

interface Loan {
  id: string
  amount: number
  durationMonths: number
  monthlyPayment: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'DISBURSED' | 'CLOSED'
  purpose?: string
  createdAt: string
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const DURATION_OPTIONS = [3, 6, 12, 24, 36, 60, 84, 120]

function formatEur(value: number) {
  return value.toLocaleString('fr-FR', { style: 'currency', currency: 'EUR' })
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  })
}

function loanStatusBadge(status: Loan['status']) {
  switch (status) {
    case 'PENDING':
      return <Badge className="bg-yellow-100 text-yellow-800 hover:bg-yellow-100 border-0">En attente</Badge>
    case 'APPROVED':
      return <Badge className="bg-blue-100 text-blue-700 hover:bg-blue-100 border-0">Approuvé</Badge>
    case 'REJECTED':
      return <Badge className="bg-red-100 text-red-700 hover:bg-red-100 border-0">Refusé</Badge>
    case 'DISBURSED':
      return <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100 border-0">Déboursé</Badge>
    case 'CLOSED':
      return <Badge className="bg-gray-100 text-gray-600 hover:bg-gray-100 border-0">Clôturé</Badge>
  }
}

function statusIcon(status: Loan['status']) {
  switch (status) {
    case 'PENDING':
      return <Clock size={14} className="text-yellow-600" />
    case 'APPROVED':
      return <CheckCircle2 size={14} className="text-blue-600" />
    case 'REJECTED':
      return <XCircle size={14} className="text-red-600" />
    case 'DISBURSED':
      return <TrendingUp size={14} className="text-emerald-600" />
    case 'CLOSED':
      return <CheckCircle2 size={14} className="text-gray-400" />
  }
}

// ── Loan Row ──────────────────────────────────────────────────────────────────

function LoanRow({ loan }: { loan: Loan }) {
  return (
    <div className="flex items-start gap-3 py-4">
      <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary/10">
        {statusIcon(loan.status)}
      </div>

      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-medium">{formatEur(loan.amount)}</span>
          <span className="text-xs text-muted-foreground">·</span>
          <span className="text-xs text-muted-foreground">{loan.durationMonths}&nbsp;mois</span>
          {loan.purpose && (
            <>
              <span className="text-xs text-muted-foreground">·</span>
              <span className="text-xs text-muted-foreground truncate max-w-[160px]">{loan.purpose}</span>
            </>
          )}
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-xs text-muted-foreground">
            {formatEur(loan.monthlyPayment)}&nbsp;/&nbsp;mois
          </span>
          <span className="text-xs text-muted-foreground">·</span>
          <span className="text-xs text-muted-foreground">{formatDate(loan.createdAt)}</span>
        </div>
      </div>

      <div className="shrink-0">
        {loanStatusBadge(loan.status)}
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function LoansPage() {
  const userId      = getUserIdFromToken()
  const queryClient = useQueryClient()

  // ── Simulation state ───────────────────────────────────────────────────────
  const [simAmount, setSimAmount]     = useState('')
  const [simDuration, setSimDuration] = useState<number>(12)
  const [simResult, setSimResult]     = useState<SimulationResult | null>(null)

  // ── Application dialog state ───────────────────────────────────────────────
  const [applyOpen, setApplyOpen]   = useState(false)
  const [accountId, setAccountId]   = useState('')
  const [purpose, setPurpose]       = useState('')

  // ── Fetch loans ────────────────────────────────────────────────────────────
  const { data: loans = [], isLoading } = useQuery<Loan[]>({
    queryKey: ['loans', userId],
    queryFn:  () => api.get<Loan[]>('/api/v1/loans').then(r => r.data),
    enabled:  !!userId,
  })

  // ── Simulate ───────────────────────────────────────────────────────────────
  const simulateMutation = useMutation({
    mutationFn: (body: { amount: number; durationMonths: number }) =>
      api.post<SimulationResult>('/api/v1/loans/simulate', body).then(r => r.data),
    onSuccess: (data) => {
      setSimResult(data)
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      const msg = err.response?.data?.error ?? 'Erreur lors de la simulation'
      toast.error(msg)
    },
  })

  function handleSimulate(e: React.FormEvent) {
    e.preventDefault()
    const amount = parseFloat(simAmount)
    if (isNaN(amount) || amount <= 0) {
      toast.error('Montant invalide')
      return
    }
    simulateMutation.mutate({ amount, durationMonths: simDuration })
  }

  // ── Apply for loan ─────────────────────────────────────────────────────────
  const applyMutation = useMutation({
    mutationFn: (body: {
      accountId: string
      purpose: string
      amount: number
      durationMonths: number
    }) => api.post<Loan>('/api/v1/loans', body).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loans'] })
      toast.success('Demande de prêt soumise')
      closeApplyDialog()
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      const msg = err.response?.data?.error ?? 'Erreur lors de la demande'
      toast.error(msg)
    },
  })

  function closeApplyDialog() {
    setApplyOpen(false)
    setAccountId('')
    setPurpose('')
  }

  function handleApply(e: React.FormEvent) {
    e.preventDefault()
    if (!accountId.trim()) {
      toast.error('Veuillez renseigner l\'ID du compte')
      return
    }
    if (!simResult) return
    applyMutation.mutate({
      accountId: accountId.trim(),
      purpose: purpose.trim(),
      amount: parseFloat(simAmount),
      durationMonths: simDuration,
    })
  }

  return (
    <div className="space-y-6">

      {/* ── Header ────────────────────────────────────────────────────────── */}
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Prêts</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Simulez et demandez un prêt adapté à vos besoins.
        </p>
      </div>

      {/* ── Simulation section ─────────────────────────────────────────────── */}
      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="flex items-center gap-2 text-sm font-semibold">
            <Calculator size={15} className="text-primary" />
            Simulateur de prêt
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-5">
          <form onSubmit={handleSimulate} className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="simAmount">Montant (€)</Label>
                <Input
                  id="simAmount"
                  type="number"
                  min="1"
                  step="0.01"
                  value={simAmount}
                  onChange={e => { setSimAmount(e.target.value); setSimResult(null) }}
                  placeholder="10 000"
                  className="h-11"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="simDuration">Durée</Label>
                <select
                  id="simDuration"
                  value={simDuration}
                  onChange={e => { setSimDuration(Number(e.target.value)); setSimResult(null) }}
                  className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
                >
                  {DURATION_OPTIONS.map(d => (
                    <option key={d} value={d}>
                      {d}&nbsp;mois
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <Button
              type="submit"
              disabled={simulateMutation.isPending}
              className="gap-2"
            >
              <Calculator size={15} />
              {simulateMutation.isPending ? 'Calcul…' : 'Simuler'}
            </Button>
          </form>

          {/* Simulation result */}
          {simResult && (
            <div className="mt-5 rounded-xl border border-primary/20 bg-primary/5 p-4 space-y-3">
              <p className="text-sm font-semibold text-primary">Résultat de la simulation</p>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <div>
                  <p className="text-xs text-muted-foreground">Mensualité</p>
                  <p className="mt-0.5 text-base font-semibold">{formatEur(simResult.monthlyPayment)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Total remboursé</p>
                  <p className="mt-0.5 text-base font-semibold">{formatEur(simResult.totalRepayment)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Coût du crédit</p>
                  <p className="mt-0.5 text-base font-semibold">{formatEur(simResult.totalInterest)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Taux annuel</p>
                  <p className="mt-0.5 text-base font-semibold">
                    {(simResult.interestRate * 100).toFixed(2)}&nbsp;%
                  </p>
                </div>
              </div>
              <Button
                size="sm"
                className="gap-2 mt-1"
                onClick={() => setApplyOpen(true)}
              >
                <TrendingUp size={14} />
                Faire une demande
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      {/* ── My loans section ───────────────────────────────────────────────── */}
      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="text-sm font-semibold">
            Mes prêts
            {loans.length > 0 && (
              <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-[11px] font-normal text-muted-foreground">
                {loans.length}
              </span>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">

          {isLoading && (
            <div className="space-y-3 p-4">
              {[1, 2, 3].map(i => <Skeleton key={i} className="h-16 w-full" />)}
            </div>
          )}

          {!isLoading && loans.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-14 text-center text-muted-foreground">
              <div className="flex size-14 items-center justify-center rounded-2xl bg-muted">
                <TrendingUp size={24} className="opacity-50" />
              </div>
              <div>
                <p className="text-sm font-medium">Aucun prêt</p>
                <p className="mt-0.5 text-xs">Utilisez le simulateur pour faire votre première demande.</p>
              </div>
            </div>
          )}

          {!isLoading && loans.length > 0 && (
            <div className="divide-y divide-border px-4">
              {loans.map(loan => (
                <LoanRow key={loan.id} loan={loan} />
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* ── Apply dialog ───────────────────────────────────────────────────── */}
      <Dialog open={applyOpen} onOpenChange={o => { if (!applyMutation.isPending) setApplyOpen(o) }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <TrendingUp size={18} className="text-primary" />
              Demande de prêt
            </DialogTitle>
            <DialogDescription>
              Complétez votre demande basée sur la simulation.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleApply} className="space-y-4 py-1">

            {/* Pre-filled summary */}
            {simResult && (
              <div className="rounded-lg border bg-muted/40 px-4 py-3 space-y-1">
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Montant</span>
                  <span className="font-medium">{formatEur(parseFloat(simAmount))}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Durée</span>
                  <span className="font-medium">{simDuration}&nbsp;mois</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Mensualité</span>
                  <span className="font-medium">{formatEur(simResult.monthlyPayment)}</span>
                </div>
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="applyAccountId">ID du compte</Label>
              <Input
                id="applyAccountId"
                value={accountId}
                onChange={e => setAccountId(e.target.value)}
                placeholder="UUID du compte à créditer"
                className="h-11 font-mono text-sm"
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="purpose">
                Motif&nbsp;
                <span className="text-muted-foreground font-normal">(optionnel)</span>
              </Label>
              <Input
                id="purpose"
                value={purpose}
                onChange={e => setPurpose(e.target.value)}
                placeholder="Achat immobilier, travaux, voiture…"
                className="h-11"
              />
            </div>

            <DialogFooter className="gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={closeApplyDialog}
                disabled={applyMutation.isPending}
                className="flex-1"
              >
                Annuler
              </Button>
              <Button type="submit" disabled={applyMutation.isPending} className="flex-1 gap-2">
                <CheckCircle2 size={14} />
                {applyMutation.isPending ? 'Envoi…' : 'Soumettre'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
