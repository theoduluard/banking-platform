import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import { getUserIdFromToken } from '@/lib/auth'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  BarChart3,
  TrendingDown,
  TrendingUp,
  ChevronLeft,
  ChevronRight,
  Calendar,
} from 'lucide-react'

// ── Types ──────────────────────────────────────────────────────────────────────

interface SpendingCategory {
  category: string
  total_debit: number
  total_credit: number
  transaction_count: number
}

interface MonthlySpendingResponse {
  year: number
  month: number
  categories: SpendingCategory[]
  total_debit: number
  total_credit: number
}

interface HistoryEntry {
  year: number
  month: number
  total_debit: number
  total_credit: number
  transaction_count?: number
}

interface SpendingHistoryResponse {
  history: HistoryEntry[]
}

// ── Helpers ────────────────────────────────────────────────────────────────────

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)
}

const MONTH_NAMES = [
  'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
  'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre',
]

function monthLabel(year: number, month: number): string {
  return `${MONTH_NAMES[month - 1]} ${year}`
}

// ── Summary cards ──────────────────────────────────────────────────────────────

function SummaryCards({
  totalDebit,
  totalCredit,
  isLoading,
}: {
  totalDebit: number
  totalCredit: number
  isLoading: boolean
}) {
  const net = totalCredit - totalDebit

  return (
    <div className="grid grid-cols-3 gap-4">
      {/* Total débits */}
      <Card>
        <CardContent className="p-4">
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <TrendingDown size={13} className="text-red-500" />
            Dépenses totales
          </div>
          {isLoading ? (
            <Skeleton className="mt-2 h-7 w-32" />
          ) : (
            <p className="mt-1.5 text-xl font-bold text-red-600">
              {formatCurrency(totalDebit)}
            </p>
          )}
        </CardContent>
      </Card>

      {/* Total crédits */}
      <Card>
        <CardContent className="p-4">
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <TrendingUp size={13} className="text-emerald-500" />
            Revenus totaux
          </div>
          {isLoading ? (
            <Skeleton className="mt-2 h-7 w-32" />
          ) : (
            <p className="mt-1.5 text-xl font-bold text-emerald-600">
              {formatCurrency(totalCredit)}
            </p>
          )}
        </CardContent>
      </Card>

      {/* Net */}
      <Card>
        <CardContent className="p-4">
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <BarChart3 size={13} className="text-primary" />
            Solde net
          </div>
          {isLoading ? (
            <Skeleton className="mt-2 h-7 w-32" />
          ) : (
            <p
              className={`mt-1.5 text-xl font-bold ${
                net >= 0 ? 'text-emerald-600' : 'text-red-600'
              }`}
            >
              {net >= 0 ? '+' : ''}
              {formatCurrency(net)}
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

// ── Category row ───────────────────────────────────────────────────────────────

function CategoryRow({
  category,
  maxDebit,
}: {
  category: SpendingCategory
  maxDebit: number
}) {
  const pct = maxDebit > 0 ? Math.round((category.total_debit / maxDebit) * 100) : 0

  return (
    <div className="space-y-1.5 py-3">
      <div className="flex items-center justify-between gap-4">
        <div className="flex min-w-0 flex-1 items-center gap-2.5">
          <span className="truncate text-sm font-medium">{category.category}</span>
          <Badge variant="secondary" className="shrink-0 text-[10px]">
            {category.transaction_count} opération{category.transaction_count !== 1 ? 's' : ''}
          </Badge>
        </div>
        <div className="flex shrink-0 items-center gap-4 text-right text-sm">
          <span className="font-semibold text-red-600">
            −{formatCurrency(category.total_debit)}
          </span>
          <span className="font-semibold text-emerald-600">
            +{formatCurrency(category.total_credit)}
          </span>
        </div>
      </div>
      {/* Percentage bar */}
      <div className="h-1.5 overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-red-400 transition-all duration-500"
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  )
}

// ── Monthly spending section ───────────────────────────────────────────────────

function MonthlySpending({ year, month, userId }: { year: number; month: number; userId: string | null }) {
  const { data, isLoading, isError } = useQuery<MonthlySpendingResponse>({
    queryKey: ['analytics', 'monthly', year, month],
    queryFn: async () => {
      const res = await api.get(`/api/v1/analytics/spending/monthly?year=${year}&month=${month}`)
      return res.data
    },
    staleTime: 2 * 60 * 1000,
    enabled: !!userId,
  })

  const categories = data?.categories ?? []
  const totalDebit = data?.total_debit ?? 0
  const totalCredit = data?.total_credit ?? 0
  const maxDebit = categories.length > 0 ? Math.max(...categories.map(c => c.total_debit)) : 0

  return (
    <div className="space-y-5">
      <SummaryCards
        totalDebit={totalDebit}
        totalCredit={totalCredit}
        isLoading={isLoading}
      />

      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center gap-2">
            <BarChart3 size={15} className="text-primary" />
            <CardTitle className="text-base">Répartition par catégorie</CardTitle>
          </div>
        </CardHeader>
        <CardContent className="pt-0">
          {isError && (
            <p className="py-8 text-center text-sm text-destructive">
              Impossible de charger les données analytiques.
            </p>
          )}

          {isLoading && (
            <div className="divide-y">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="space-y-2 py-3">
                  <div className="flex items-center justify-between">
                    <Skeleton className="h-4 w-32" />
                    <Skeleton className="h-4 w-40" />
                  </div>
                  <Skeleton className="h-1.5 w-full" />
                </div>
              ))}
            </div>
          )}

          {!isLoading && !isError && categories.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-12 text-center">
              <Calendar size={32} className="text-muted-foreground/40" />
              <p className="text-sm text-muted-foreground">
                Aucune donnée pour cette période
              </p>
            </div>
          )}

          {!isLoading && !isError && categories.length > 0 && (
            <div className="divide-y">
              {categories.map(cat => (
                <CategoryRow key={cat.category} category={cat} maxDebit={maxDebit} />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

// ── Spending history section ───────────────────────────────────────────────────

function SpendingHistory({ userId }: { userId: string | null }) {
  const { data, isLoading, isError } = useQuery<SpendingHistoryResponse>({
    queryKey: ['analytics', 'history'],
    queryFn: async () => {
      const res = await api.get('/api/v1/analytics/spending/history')
      return res.data
    },
    staleTime: 5 * 60 * 1000,
    enabled: !!userId,
  })

  const history = data?.history ?? []

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <TrendingUp size={15} className="text-primary" />
          <CardTitle className="text-base">Historique des dépenses</CardTitle>
        </div>
        <p className="text-xs text-muted-foreground">
          Vue d'ensemble de vos flux par mois
        </p>
      </CardHeader>
      <CardContent className="pt-0">
        {isError && (
          <p className="py-6 text-center text-sm text-destructive">
            Impossible de charger l'historique.
          </p>
        )}

        {isLoading && (
          <div className="space-y-2">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="flex items-center justify-between py-2">
                <Skeleton className="h-4 w-28" />
                <div className="flex gap-6">
                  <Skeleton className="h-4 w-24" />
                  <Skeleton className="h-4 w-24" />
                </div>
              </div>
            ))}
          </div>
        )}

        {!isLoading && !isError && history.length === 0 && (
          <p className="py-8 text-center text-sm text-muted-foreground">
            Aucune donnée pour cette période
          </p>
        )}

        {!isLoading && !isError && history.length > 0 && (
          <div className="overflow-hidden rounded-lg border">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="px-4 py-2.5 text-left font-medium text-muted-foreground">Période</th>
                  <th className="px-4 py-2.5 text-right font-medium text-muted-foreground">Dépenses</th>
                  <th className="px-4 py-2.5 text-right font-medium text-muted-foreground">Revenus</th>
                  <th className="px-4 py-2.5 text-right font-medium text-muted-foreground">Net</th>
                  {history[0]?.transaction_count !== undefined && (
                    <th className="px-4 py-2.5 text-right font-medium text-muted-foreground">Opérations</th>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y">
                {history.map(entry => {
                  const net = entry.total_credit - entry.total_debit
                  return (
                    <tr
                      key={`${entry.year}-${entry.month}`}
                      className="transition-colors hover:bg-muted/30"
                    >
                      <td className="px-4 py-2.5 font-medium">
                        {monthLabel(entry.year, entry.month)}
                      </td>
                      <td className="px-4 py-2.5 text-right text-red-600">
                        −{formatCurrency(entry.total_debit)}
                      </td>
                      <td className="px-4 py-2.5 text-right text-emerald-600">
                        +{formatCurrency(entry.total_credit)}
                      </td>
                      <td
                        className={`px-4 py-2.5 text-right font-semibold ${
                          net >= 0 ? 'text-emerald-600' : 'text-red-600'
                        }`}
                      >
                        {net >= 0 ? '+' : ''}
                        {formatCurrency(net)}
                      </td>
                      {entry.transaction_count !== undefined && (
                        <td className="px-4 py-2.5 text-right text-muted-foreground">
                          {entry.transaction_count}
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

// ── Page ───────────────────────────────────────────────────────────────────────

export default function AnalyticsPage() {
  const userId = getUserIdFromToken()
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1) // 1-indexed
  const [showHistory, setShowHistory] = useState(false)

  function prevMonth() {
    if (month === 1) {
      setYear(y => y - 1)
      setMonth(12)
    } else {
      setMonth(m => m - 1)
    }
  }

  function nextMonth() {
    const nextYear = month === 12 ? year + 1 : year
    const nextM = month === 12 ? 1 : month + 1
    // Do not navigate to future months
    if (
      nextYear > now.getFullYear() ||
      (nextYear === now.getFullYear() && nextM > now.getMonth() + 1)
    ) {
      return
    }
    setYear(nextYear)
    setMonth(nextM)
  }

  const isCurrentMonth =
    year === now.getFullYear() && month === now.getMonth() + 1

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10">
          <BarChart3 size={20} className="text-primary" />
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Analytique</h1>
          <p className="text-sm text-muted-foreground">
            Suivez vos dépenses et revenus par catégorie
          </p>
        </div>
      </div>

      {/* Month navigator */}
      <div className="flex items-center gap-3">
        <Button variant="outline" size="icon" onClick={prevMonth} title="Mois précédent">
          <ChevronLeft size={16} />
        </Button>
        <div className="flex min-w-[160px] items-center justify-center gap-2 rounded-lg border bg-muted/30 px-4 py-2">
          <Calendar size={14} className="text-muted-foreground" />
          <span className="text-sm font-semibold">{monthLabel(year, month)}</span>
        </div>
        <Button
          variant="outline"
          size="icon"
          onClick={nextMonth}
          disabled={isCurrentMonth}
          title="Mois suivant"
        >
          <ChevronRight size={16} />
        </Button>
      </div>

      {/* Monthly spending */}
      <MonthlySpending year={year} month={month} userId={userId} />

      {/* Spending history toggle */}
      <div>
        <Button
          variant="outline"
          className="gap-2 text-sm"
          onClick={() => setShowHistory(v => !v)}
        >
          <TrendingUp size={14} />
          {showHistory ? "Masquer l'historique" : "Voir l'historique complet"}
          <ChevronRight
            size={14}
            className={`transition-transform duration-200 ${showHistory ? 'rotate-90' : ''}`}
          />
        </Button>

        {showHistory && (
          <div className="mt-4">
            <SpendingHistory userId={userId} />
          </div>
        )}
      </div>
    </div>
  )
}
