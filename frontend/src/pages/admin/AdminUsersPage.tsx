import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import api from '@/lib/api'
import type { AdminUser } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { Users, Search, UserCheck, UserX, ShieldCheck, ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react'
import { useState, useMemo, useEffect, useRef } from 'react'
import { cn } from '@/lib/utils'

// ── Constants ──────────────────────────────────────────────────────────────────

const PAGE_SIZE = 50

type SortKey   = 'name' | 'email' | 'date'
type SortDir   = 'asc' | 'desc'
type RoleFilter   = 'ALL' | 'CLIENT' | 'ADMIN'
type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE'

// ── Helpers ────────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium' }).format(new Date(iso))
}

function SortIcon({ col, sortKey, dir }: { col: SortKey; sortKey: SortKey; dir: SortDir }) {
  if (col !== sortKey) return <ChevronsUpDown size={12} className="text-muted-foreground/40" />
  return dir === 'asc'
    ? <ChevronUp  size={12} className="text-foreground" />
    : <ChevronDown size={12} className="text-foreground" />
}

// ── Component ──────────────────────────────────────────────────────────────────

export default function AdminUsersPage() {
  const qc = useQueryClient()

  // ── Filtering & sorting state ──────────────────────────────────────────────
  const [search,      setSearch]      = useState('')
  const [debouncedQ,  setDebouncedQ]  = useState('')
  const [roleFilter,  setRoleFilter]  = useState<RoleFilter>('ALL')
  const [statusFilter,setStatusFilter]= useState<StatusFilter>('ALL')
  const [sortKey,     setSortKey]     = useState<SortKey>('name')
  const [sortDir,     setSortDir]     = useState<SortDir>('asc')
  const [page,        setPage]        = useState(0)

  // Debounce search input (300 ms) — avoids re-rendering on every keystroke
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      setDebouncedQ(search)
      setPage(0) // reset to first page on new search
    }, 300)
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current) }
  }, [search])

  // Reset to first page whenever a filter changes
  useEffect(() => { setPage(0) }, [roleFilter, statusFilter, sortKey, sortDir])

  // ── Data fetching ──────────────────────────────────────────────────────────
  const { data: users = [], isLoading } = useQuery<AdminUser[]>({
    queryKey: ['admin', 'users'],
    queryFn: () => api.get<AdminUser[]>('/api/v1/admin/users').then(r => r.data),
    // Cache for 60 s — admin list changes rarely; this avoids re-fetching on
    // every filter change which are all done client-side.
    staleTime: 60_000,
  })

  const toggleStatus = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      api.patch(`/api/v1/admin/users/${id}/status?active=${active}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'users'] })
      toast.success('Statut mis à jour')
    },
    onError: () => toast.error('Impossible de mettre à jour le statut'),
  })

  // ── Client-side filtering & sorting ───────────────────────────────────────
  const filtered = useMemo(() => {
    let result = users

    // Text search
    if (debouncedQ) {
      const q = debouncedQ.toLowerCase()
      result = result.filter(u =>
        `${u.firstname} ${u.lastname} ${u.email}`.toLowerCase().includes(q)
      )
    }

    // Role filter
    if (roleFilter !== 'ALL') {
      result = result.filter(u => u.role === roleFilter)
    }

    // Status filter
    if (statusFilter === 'ACTIVE')   result = result.filter(u =>  u.isActive)
    if (statusFilter === 'INACTIVE') result = result.filter(u => !u.isActive)

    // Sorting
    result = [...result].sort((a, b) => {
      let cmp = 0
      if (sortKey === 'name')  cmp = `${a.firstname} ${a.lastname}`.localeCompare(`${b.firstname} ${b.lastname}`, 'fr')
      if (sortKey === 'email') cmp = a.email.localeCompare(b.email, 'fr')
      if (sortKey === 'date')  cmp = (a.createdAt ?? '').localeCompare(b.createdAt ?? '')
      return sortDir === 'asc' ? cmp : -cmp
    })

    return result
  }, [users, debouncedQ, roleFilter, statusFilter, sortKey, sortDir])

  // ── Pagination ─────────────────────────────────────────────────────────────
  const totalPages  = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages - 1)
  const pageItems   = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE)

  function toggleSort(key: SortKey) {
    if (key === sortKey) setSortDir(d => d === 'asc' ? 'desc' : 'asc')
    else { setSortKey(key); setSortDir('asc') }
  }

  // ── Summary counts ─────────────────────────────────────────────────────────
  const activeCount  = users.filter(u =>  u.isActive).length
  const inactiveCount= users.filter(u => !u.isActive).length
  const adminCount   = users.filter(u =>  u.role === 'ADMIN').length

  return (
    <div className="space-y-6">

      {/* ── Header ──────────────────────────────────────────────────────────── */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Users size={20} />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Utilisateurs</h1>
            <p className="text-sm text-muted-foreground">
              {users.length.toLocaleString('fr-FR')} compte{users.length > 1 ? 's' : ''} enregistré{users.length > 1 ? 's' : ''}
              {' · '}
              <span className="text-emerald-600">{activeCount} actif{activeCount > 1 ? 's' : ''}</span>
              {inactiveCount > 0 && (
                <span className="text-muted-foreground"> · {inactiveCount} inactif{inactiveCount > 1 ? 's' : ''}</span>
              )}
              {adminCount > 0 && (
                <span className="text-primary"> · {adminCount} admin{adminCount > 1 ? 's' : ''}</span>
              )}
            </p>
          </div>
        </div>
      </div>

      {/* ── Filters bar ─────────────────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center gap-3">

        {/* Search */}
        <div className="relative min-w-[200px] flex-1 max-w-sm">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Nom, prénom, email…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="h-9 pl-8 text-sm"
          />
        </div>

        {/* Status filter */}
        <div className="flex rounded-lg border bg-muted/30 p-0.5 gap-0.5 text-xs">
          {(['ALL', 'ACTIVE', 'INACTIVE'] as StatusFilter[]).map(f => (
            <button
              key={f}
              type="button"
              onClick={() => setStatusFilter(f)}
              className={cn(
                'rounded-md px-3 py-1.5 font-medium transition-all',
                statusFilter === f
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {f === 'ALL' ? 'Tous' : f === 'ACTIVE' ? 'Actifs' : 'Inactifs'}
            </button>
          ))}
        </div>

        {/* Role filter */}
        <div className="flex rounded-lg border bg-muted/30 p-0.5 gap-0.5 text-xs">
          {(['ALL', 'CLIENT', 'ADMIN'] as RoleFilter[]).map(f => (
            <button
              key={f}
              type="button"
              onClick={() => setRoleFilter(f)}
              className={cn(
                'rounded-md px-3 py-1.5 font-medium transition-all',
                roleFilter === f
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {f === 'ALL' ? 'Tous rôles' : f === 'CLIENT' ? 'Clients' : 'Admins'}
            </button>
          ))}
        </div>
      </div>

      {/* ── Table ───────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader className="border-b pb-3 pt-4">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-semibold">Liste des utilisateurs</CardTitle>
            {filtered.length !== users.length && (
              <span className="text-xs text-muted-foreground">
                {filtered.length.toLocaleString('fr-FR')} résultat{filtered.length > 1 ? 's' : ''} sur {users.length.toLocaleString('fr-FR')}
              </span>
            )}
          </div>
        </CardHeader>

        {/* Column headers */}
        {!isLoading && filtered.length > 0 && (
          <div className="hidden border-b bg-muted/30 px-4 py-2 text-[11px] font-medium uppercase tracking-wider text-muted-foreground sm:grid sm:grid-cols-[1fr_1fr_120px_80px_100px]">
            <button
              type="button"
              onClick={() => toggleSort('name')}
              className="flex items-center gap-1 hover:text-foreground transition-colors text-left"
            >
              Nom <SortIcon col="name" sortKey={sortKey} dir={sortDir} />
            </button>
            <button
              type="button"
              onClick={() => toggleSort('email')}
              className="flex items-center gap-1 hover:text-foreground transition-colors text-left"
            >
              Email <SortIcon col="email" sortKey={sortKey} dir={sortDir} />
            </button>
            <button
              type="button"
              onClick={() => toggleSort('date')}
              className="flex items-center gap-1 hover:text-foreground transition-colors"
            >
              Inscrit <SortIcon col="date" sortKey={sortKey} dir={sortDir} />
            </button>
            <span>Statut</span>
            <span />
          </div>
        )}

        <CardContent className="p-0">
          {isLoading && (
            <div className="space-y-3 p-4">
              {[1, 2, 3, 4, 5].map(i => <Skeleton key={i} className="h-14 w-full" />)}
            </div>
          )}

          {!isLoading && filtered.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
              <Users size={28} className="opacity-40" />
              <p className="text-sm">Aucun utilisateur trouvé.</p>
              {(debouncedQ || roleFilter !== 'ALL' || statusFilter !== 'ALL') && (
                <button
                  type="button"
                  onClick={() => { setSearch(''); setRoleFilter('ALL'); setStatusFilter('ALL') }}
                  className="mt-1 text-xs text-primary underline-offset-4 hover:underline"
                >
                  Réinitialiser les filtres
                </button>
              )}
            </div>
          )}

          {!isLoading && pageItems.length > 0 && (
            <div className="divide-y divide-border">
              {pageItems.map(u => (
                <UserRow
                  key={u.userId}
                  user={u}
                  onToggle={() => toggleStatus.mutate({ id: u.userId, active: !u.isActive })}
                  isPending={toggleStatus.isPending}
                />
              ))}
            </div>
          )}

          {/* ── Pagination ──────────────────────────────────────────────────── */}
          {!isLoading && totalPages > 1 && (
            <>
              <Separator />
              <div className="flex items-center justify-between px-4 py-3 text-xs text-muted-foreground">
                <span>
                  Page {currentPage + 1} / {totalPages}
                  {' · '}
                  {filtered.length.toLocaleString('fr-FR')} résultat{filtered.length > 1 ? 's' : ''}
                </span>
                <div className="flex items-center gap-1">
                  {/* First page */}
                  <Button variant="outline" size="sm" className="h-7 px-2 text-xs"
                    disabled={currentPage === 0} onClick={() => setPage(0)}>
                    «
                  </Button>
                  {/* Prev */}
                  <Button variant="outline" size="sm" className="h-7 px-2 text-xs"
                    disabled={currentPage === 0} onClick={() => setPage(p => p - 1)}>
                    ‹
                  </Button>

                  {/* Page numbers — show up to 5 pages around current */}
                  {Array.from({ length: totalPages }, (_, i) => i)
                    .filter(i => Math.abs(i - currentPage) <= 2 || i === 0 || i === totalPages - 1)
                    .reduce<(number | '…')[]>((acc, i, idx, arr) => {
                      if (idx > 0 && (i as number) - (arr[idx - 1] as number) > 1) acc.push('…')
                      acc.push(i)
                      return acc
                    }, [])
                    .map((item, idx) =>
                      item === '…'
                        ? <span key={`ellipsis-${idx}`} className="px-1 text-muted-foreground">…</span>
                        : (
                          <Button
                            key={item}
                            variant={item === currentPage ? 'default' : 'outline'}
                            size="sm"
                            className="h-7 min-w-[28px] px-2 text-xs"
                            onClick={() => setPage(item as number)}
                          >
                            {(item as number) + 1}
                          </Button>
                        )
                    )
                  }

                  {/* Next */}
                  <Button variant="outline" size="sm" className="h-7 px-2 text-xs"
                    disabled={currentPage >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
                    ›
                  </Button>
                  {/* Last page */}
                  <Button variant="outline" size="sm" className="h-7 px-2 text-xs"
                    disabled={currentPage >= totalPages - 1} onClick={() => setPage(totalPages - 1)}>
                    »
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

// ── UserRow ────────────────────────────────────────────────────────────────────

function UserRow({ user: u, onToggle, isPending }: {
  user: AdminUser
  onToggle: () => void
  isPending: boolean
}) {
  return (
    <div className="flex items-center gap-4 px-4 py-3.5 transition-colors hover:bg-muted/30">
      {/* Avatar */}
      <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold uppercase text-muted-foreground">
        {u.firstname[0]}{u.lastname[0]}
      </div>

      {/* Info */}
      <div className="min-w-0 flex-1 sm:grid sm:grid-cols-[1fr_1fr] sm:gap-x-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="truncate text-sm font-medium">{u.firstname} {u.lastname}</p>
            {u.role === 'ADMIN' && (
              <span className="inline-flex shrink-0 items-center gap-0.5 rounded-full bg-primary/10 px-1.5 py-0.5 text-[10px] font-semibold text-primary">
                <ShieldCheck size={9} /> Admin
              </span>
            )}
          </div>
          <p className="truncate text-xs text-muted-foreground">{u.email}</p>
        </div>
        <p className="hidden truncate text-xs text-muted-foreground sm:block sm:self-center">
          {u.createdAt ? formatDate(u.createdAt) : '—'}
        </p>
      </div>

      {/* Status badge */}
      <span className={cn(
        'hidden shrink-0 items-center rounded-full border px-2 py-0.5 text-[10px] font-medium sm:inline-flex',
        u.isActive
          ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
          : 'border-red-200 bg-red-50 text-red-700',
      )}>
        {u.isActive ? 'Actif' : 'Inactif'}
      </span>

      {/* Toggle — admins cannot be deactivated */}
      {u.role !== 'ADMIN' ? (
        <Button
          variant="ghost"
          size="sm"
          className={cn(
            'h-8 shrink-0 gap-1.5 text-xs',
            u.isActive
              ? 'text-red-600 hover:bg-red-50 hover:text-red-700'
              : 'text-emerald-700 hover:bg-emerald-50',
          )}
          onClick={onToggle}
          disabled={isPending}
        >
          {u.isActive
            ? <><UserX size={13} /><span>Désactiver</span></>
            : <><UserCheck size={13} /><span>Activer</span></>
          }
        </Button>
      ) : (
        <div className="h-8 w-[94px] shrink-0" /> /* spacer to keep column alignment */
      )}
    </div>
  )
}
