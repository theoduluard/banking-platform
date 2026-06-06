import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { FileDown, FileText, Download, Clock, CreditCard, PiggyBank } from 'lucide-react'

// ─── Types ────────────────────────────────────────────────────────────────────

interface DocumentHistoryEntry {
  id: string
  filename: string
  documentType: string
  generatedAt: string
  accountId?: string
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatAmount(amount: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount)
}

function formatDate(dateStr: string) {
  return new Intl.DateTimeFormat('fr-FR', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(dateStr))
}

function maskIban(iban: string) {
  return `${iban.slice(0, 8)}···${iban.slice(-4)}`
}

function AccountTypeIcon({ type }: { type: Account['type'] }) {
  return type === 'CHECKING'
    ? <CreditCard size={16} className="text-primary" />
    : <PiggyBank  size={16} className="text-[oklch(0.78_0.145_82)]" />
}

// ─── Account Card ─────────────────────────────────────────────────────────────

interface AccountCardProps {
  account: Account
  isDownloading: boolean
  onDownload: (accountId: string) => void
}

function AccountCard({ account, isDownloading, onDownload }: AccountCardProps) {
  const isChecking = account.type === 'CHECKING'

  return (
    <Card className="relative overflow-hidden transition-all duration-200 hover:shadow-md">
      {/* Accent stripe */}
      <div className={`absolute inset-y-0 left-0 w-1 rounded-l-xl ${
        isChecking ? 'bg-primary' : 'bg-[oklch(0.78_0.145_82)]'
      }`} />

      <CardHeader className="pb-3 pl-6">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2.5">
            <div className={`flex size-9 items-center justify-center rounded-lg ${
              isChecking ? 'bg-primary/10' : 'bg-[oklch(0.78_0.145_82)]/15'
            }`}>
              <AccountTypeIcon type={account.type} />
            </div>
            <div>
              <CardTitle className="text-sm font-semibold">
                {isChecking ? 'Compte courant' : 'Compte épargne'}
              </CardTitle>
              <p className="mt-0.5 font-mono text-[10px] tracking-wide text-muted-foreground">
                {maskIban(account.iban)}
              </p>
            </div>
          </div>

          <Badge
            variant="secondary"
            className={cn('text-[10px] border', {
              'bg-emerald-50 text-emerald-700 border-emerald-200': account.status === 'ACTIVE',
              'bg-amber-50 text-amber-700 border-amber-200':       account.status === 'PENDING_APPROVAL',
              'bg-red-50 text-red-700 border-red-200':             account.status === 'REJECTED',
              'bg-muted text-muted-foreground border-border':
                account.status === 'CLOSED' || account.status === 'BLOCKED',
            })}
          >
            {account.status === 'ACTIVE'            ? 'Actif'
              : account.status === 'PENDING_APPROVAL' ? 'En attente'
              : account.status === 'REJECTED'         ? 'Rejeté'
              : 'Inactif'}
          </Badge>
        </div>
      </CardHeader>

      <CardContent className="pl-6">
        <div className="flex items-center justify-between">
          <p className="text-xl font-bold tabular-nums">
            {formatAmount(account.balance, account.currency)}
          </p>

          <Button
            size="sm"
            variant="outline"
            disabled={isDownloading || account.status !== 'ACTIVE'}
            onClick={() => onDownload(account.id)}
            className="gap-1.5"
          >
            {isDownloading ? (
              <>
                <Download size={14} className="animate-bounce" />
                Téléchargement…
              </>
            ) : (
              <>
                <FileDown size={14} />
                Télécharger le RIB
              </>
            )}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

// ─── History Item ─────────────────────────────────────────────────────────────

function HistoryItem({ entry }: { entry: DocumentHistoryEntry }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border bg-card px-4 py-3 transition-colors hover:bg-muted/40">
      <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
        <FileText size={16} className="text-primary" />
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{entry.filename}</p>
        <p className="mt-0.5 text-[11px] text-muted-foreground capitalize">
          {entry.documentType.replace(/_/g, ' ').toLowerCase()}
        </p>
      </div>

      <div className="flex shrink-0 items-center gap-1.5 text-[11px] text-muted-foreground">
        <Clock size={12} />
        {formatDate(entry.generatedAt)}
      </div>
    </div>
  )
}

// ─── Skeletons ────────────────────────────────────────────────────────────────

function AccountCardSkeleton() {
  return (
    <Card className="overflow-hidden">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2.5">
            <Skeleton className="size-9 rounded-lg" />
            <div className="space-y-1.5">
              <Skeleton className="h-3.5 w-28" />
              <Skeleton className="h-2.5 w-20" />
            </div>
          </div>
          <Skeleton className="h-4 w-14 rounded-full" />
        </div>
      </CardHeader>
      <CardContent>
        <div className="flex items-center justify-between">
          <Skeleton className="h-6 w-28" />
          <Skeleton className="h-8 w-36 rounded-md" />
        </div>
      </CardContent>
    </Card>
  )
}

function HistoryItemSkeleton() {
  return (
    <div className="flex items-center gap-3 rounded-lg border bg-card px-4 py-3">
      <Skeleton className="size-9 shrink-0 rounded-lg" />
      <div className="flex-1 space-y-1.5">
        <Skeleton className="h-3.5 w-40" />
        <Skeleton className="h-2.5 w-24" />
      </div>
      <Skeleton className="h-3 w-32" />
    </div>
  )
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function DocumentsPage() {
  const userId = getUserIdFromToken()

  // Track which account IDs are currently being downloaded
  const [downloadingIds, setDownloadingIds] = useState<Set<string>>(new Set())

  // Fetch accounts
  const {
    data: accounts,
    isLoading: accountsLoading,
    isError: accountsError,
  } = useQuery<Account[]>({
    queryKey: ['accounts'],
    queryFn: async () => {
      const res = await api.get('/api/v1/accounts')
      return res.data
    },
    enabled: !!userId,
  })

  // Fetch document history
  const {
    data: history,
    isLoading: historyLoading,
    isError: historyError,
  } = useQuery<DocumentHistoryEntry[]>({
    queryKey: ['documents-history'],
    queryFn: async () => {
      const res = await api.get('/api/v1/documents/history')
      return res.data
    },
    enabled: !!userId,
  })

  // Download a RIB PDF for a given account
  const downloadRib = async (accountId: string) => {
    setDownloadingIds(prev => new Set(prev).add(accountId))
    try {
      const response = await api.get(`/api/v1/documents/rib/${accountId}`, {
        responseType: 'blob',
      })
      const url = URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }))
      const a = document.createElement('a')
      a.href = url
      a.download = `RIB_${accountId.slice(0, 8).toUpperCase()}.pdf`
      a.click()
      URL.revokeObjectURL(url)
      toast.success('RIB téléchargé avec succès')
    } catch {
      toast.error('Erreur lors du téléchargement du RIB')
    } finally {
      setDownloadingIds(prev => {
        const next = new Set(prev)
        next.delete(accountId)
        return next
      })
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-10 px-4 py-8">

      {/* ── Header ── */}
      <div className="flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10">
          <FileDown size={20} className="text-primary" />
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Documents</h1>
          <p className="text-sm text-muted-foreground">
            Téléchargez vos relevés d'identité bancaire (RIB)
          </p>
        </div>
      </div>

      {/* ── My accounts ── */}
      <section className="space-y-4">
        <h2 className="flex items-center gap-2 text-base font-semibold">
          <CreditCard size={16} className="text-muted-foreground" />
          Mes comptes
        </h2>

        {accountsLoading && (
          <div className="space-y-3">
            <AccountCardSkeleton />
            <AccountCardSkeleton />
          </div>
        )}

        {accountsError && (
          <Card className="border-destructive/40 bg-destructive/5">
            <CardContent className="py-6 text-center text-sm text-destructive">
              Impossible de charger vos comptes. Veuillez réessayer.
            </CardContent>
          </Card>
        )}

        {!accountsLoading && !accountsError && accounts && accounts.length === 0 && (
          <Card>
            <CardContent className="py-10 text-center text-sm text-muted-foreground">
              Aucun compte trouvé.
            </CardContent>
          </Card>
        )}

        {!accountsLoading && !accountsError && accounts && accounts.length > 0 && (
          <div className="space-y-3">
            {accounts.map(account => (
              <AccountCard
                key={account.id}
                account={account}
                isDownloading={downloadingIds.has(account.id)}
                onDownload={downloadRib}
              />
            ))}
          </div>
        )}
      </section>

      {/* ── Download history ── */}
      <section className="space-y-4">
        <h2 className="flex items-center gap-2 text-base font-semibold">
          <Clock size={16} className="text-muted-foreground" />
          Historique des téléchargements
        </h2>

        {historyLoading && (
          <div className="space-y-2">
            <HistoryItemSkeleton />
            <HistoryItemSkeleton />
            <HistoryItemSkeleton />
          </div>
        )}

        {historyError && (
          <p className="text-sm text-destructive">
            Impossible de charger l'historique. Veuillez réessayer.
          </p>
        )}

        {!historyLoading && !historyError && history && history.length === 0 && (
          <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed py-10 text-center">
            <FileText size={28} className="text-muted-foreground/50" />
            <p className="text-sm text-muted-foreground">Aucun document généré</p>
          </div>
        )}

        {!historyLoading && !historyError && history && history.length > 0 && (
          <div className="space-y-2">
            {history.map(entry => (
              <HistoryItem key={entry.id} entry={entry} />
            ))}
          </div>
        )}
      </section>

    </div>
  )
}
