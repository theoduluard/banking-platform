import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import api from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { ArrowLeftRight, RefreshCw, DollarSign, TrendingUp } from 'lucide-react'

// ── Types ──────────────────────────────────────────────────────────────────────

interface RatesResponse {
  base: string
  rates: Record<string, number>
  updated_at?: string
  timestamp?: number
}

interface ConvertResponse {
  from: string
  to: string
  amount: number
  result: number
  rate: number
}

// ── Helpers ────────────────────────────────────────────────────────────────────

const BASE_CURRENCIES = ['EUR', 'USD', 'GBP'] as const
type BaseCurrency = (typeof BASE_CURRENCIES)[number]

function formatDate(value: string | number | undefined): string {
  if (!value) return '—'
  const date = typeof value === 'number' ? new Date(value * 1000) : new Date(value)
  return new Intl.DateTimeFormat('fr-FR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date)
}

function formatRate(rate: number): string {
  return new Intl.NumberFormat('fr-FR', {
    minimumFractionDigits: 4,
    maximumFractionDigits: 6,
  }).format(rate)
}

// ── Exchange rates table ───────────────────────────────────────────────────────

function RatesTable({ base }: { base: BaseCurrency }) {
  const { data, isLoading, isError, refetch, isFetching } = useQuery<RatesResponse>({
    queryKey: ['currencies', 'rates', base],
    queryFn: async () => {
      const res = await api.get(`/api/v1/currencies/rates?base=${base}`)
      return res.data
    },
    staleTime: 5 * 60 * 1000,
  })

  if (isError) {
    return (
      <p className="py-8 text-center text-sm text-destructive">
        Impossible de charger les taux de change.
      </p>
    )
  }

  const rows = data?.rates ? Object.entries(data.rates) : []
  const updatedAt = data?.updated_at ?? data?.timestamp

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs text-muted-foreground">
          {updatedAt ? `Mis à jour le ${formatDate(updatedAt)}` : ' '}
        </p>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => refetch()}
          disabled={isFetching}
          className="h-7 gap-1.5 text-xs"
        >
          <RefreshCw size={12} className={isFetching ? 'animate-spin' : ''} />
          Actualiser
        </Button>
      </div>

      <div className="overflow-hidden rounded-lg border">
        <table className="w-full text-sm">
          <thead className="bg-muted/50">
            <tr>
              <th className="px-4 py-2.5 text-left font-medium text-muted-foreground">Devise</th>
              <th className="px-4 py-2.5 text-right font-medium text-muted-foreground">Taux</th>
              <th className="px-4 py-2.5 text-right font-medium text-muted-foreground">Mis à jour</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {isLoading
              ? Array.from({ length: 8 }).map((_, i) => (
                  <tr key={i}>
                    <td className="px-4 py-2.5"><Skeleton className="h-4 w-12" /></td>
                    <td className="px-4 py-2.5 text-right"><Skeleton className="ml-auto h-4 w-20" /></td>
                    <td className="px-4 py-2.5 text-right"><Skeleton className="ml-auto h-4 w-28" /></td>
                  </tr>
                ))
              : rows.length === 0
              ? (
                  <tr>
                    <td colSpan={3} className="px-4 py-8 text-center text-muted-foreground">
                      Aucun taux disponible.
                    </td>
                  </tr>
                )
              : rows.map(([currency, rate]) => (
                  <tr key={currency} className="transition-colors hover:bg-muted/30">
                    <td className="px-4 py-2.5">
                      <span className="font-mono font-semibold">{currency}</span>
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono">{formatRate(rate)}</td>
                    <td className="px-4 py-2.5 text-right text-xs text-muted-foreground">
                      {formatDate(updatedAt)}
                    </td>
                  </tr>
                ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ── Currency converter ─────────────────────────────────────────────────────────

function CurrencyConverter() {
  const [from, setFrom] = useState('EUR')
  const [to, setTo] = useState('USD')
  const [amount, setAmount] = useState<string>('100')
  const [convertResult, setConvertResult] = useState<ConvertResponse | null>(null)
  const [isConverting, setIsConverting] = useState(false)

  async function handleConvert() {
    const numAmount = parseFloat(amount)
    if (!from.trim() || !to.trim() || isNaN(numAmount) || numAmount <= 0) {
      toast.error('Veuillez remplir tous les champs correctement.')
      return
    }
    setIsConverting(true)
    try {
      const res = await api.get(
        `/api/v1/currencies/convert?from=${from.toUpperCase()}&to=${to.toUpperCase()}&amount=${numAmount}`
      )
      setConvertResult(res.data)
    } catch {
      toast.error('Erreur lors de la conversion. Vérifiez les codes devise.')
    } finally {
      setIsConverting(false)
    }
  }

  function swapCurrencies() {
    setFrom(to)
    setTo(from)
    setConvertResult(null)
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-[1fr_auto_1fr] items-end gap-3">
        {/* From */}
        <div className="space-y-1.5">
          <Label htmlFor="conv-from">De</Label>
          <Input
            id="conv-from"
            value={from}
            onChange={e => { setFrom(e.target.value.toUpperCase()); setConvertResult(null) }}
            placeholder="EUR"
            className="font-mono uppercase"
            maxLength={3}
          />
        </div>

        {/* Swap button */}
        <Button
          variant="outline"
          size="icon"
          onClick={swapCurrencies}
          className="mb-0.5"
          title="Inverser"
        >
          <ArrowLeftRight size={14} />
        </Button>

        {/* To */}
        <div className="space-y-1.5">
          <Label htmlFor="conv-to">Vers</Label>
          <Input
            id="conv-to"
            value={to}
            onChange={e => { setTo(e.target.value.toUpperCase()); setConvertResult(null) }}
            placeholder="USD"
            className="font-mono uppercase"
            maxLength={3}
          />
        </div>
      </div>

      {/* Amount */}
      <div className="space-y-1.5">
        <Label htmlFor="conv-amount">Montant</Label>
        <Input
          id="conv-amount"
          type="number"
          min={0}
          value={amount}
          onChange={e => { setAmount(e.target.value); setConvertResult(null) }}
          placeholder="100"
        />
      </div>

      <Button
        onClick={handleConvert}
        disabled={isConverting}
        className="w-full gap-2"
      >
        {isConverting
          ? <RefreshCw size={14} className="animate-spin" />
          : <ArrowLeftRight size={14} />}
        Convertir
      </Button>

      {/* Result */}
      {convertResult && (
        <div className="rounded-lg border border-primary/20 bg-primary/5 px-5 py-4 text-center">
          <p className="text-xs text-muted-foreground">Résultat</p>
          <p className="mt-1 text-2xl font-bold tracking-tight">
            {new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(convertResult.amount)}{' '}
            <span className="text-muted-foreground">{convertResult.from}</span>
            {' = '}
            {new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(convertResult.result)}{' '}
            <span className="text-primary">{convertResult.to}</span>
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            Taux : 1 {convertResult.from} = {formatRate(convertResult.rate)} {convertResult.to}
          </p>
        </div>
      )}
    </div>
  )
}

// ── Page ───────────────────────────────────────────────────────────────────────

export default function CurrencyPage() {
  const [base, setBase] = useState<BaseCurrency>('EUR')

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10">
          <DollarSign size={20} className="text-primary" />
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Devises</h1>
          <p className="text-sm text-muted-foreground">
            Taux de change en temps réel et convertisseur de devises
          </p>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        {/* Left: rates table */}
        <Card>
          <CardHeader className="pb-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <TrendingUp size={16} className="text-primary" />
                <CardTitle className="text-base">Taux de change</CardTitle>
              </div>
              <Select
                value={base}
                onValueChange={v => setBase(v as BaseCurrency)}
              >
                <SelectTrigger className="h-8 w-24 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {BASE_CURRENCIES.map(c => (
                    <SelectItem key={c} value={c} className="text-xs font-mono">
                      {c}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <p className="text-xs text-muted-foreground">
              Devise de base : <span className="font-mono font-semibold">{base}</span>
            </p>
          </CardHeader>
          <CardContent className="pt-0">
            <RatesTable base={base} />
          </CardContent>
        </Card>

        {/* Right: converter */}
        <Card className="h-fit">
          <CardHeader className="pb-4">
            <div className="flex items-center gap-2">
              <ArrowLeftRight size={16} className="text-primary" />
              <CardTitle className="text-base">Convertisseur</CardTitle>
            </div>
            <p className="text-xs text-muted-foreground">
              Convertissez rapidement entre deux devises
            </p>
          </CardHeader>
          <CardContent className="pt-0">
            <CurrencyConverter />
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
