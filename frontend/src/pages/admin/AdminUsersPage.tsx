import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import api from '@/lib/api'
import type { AdminUser } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Input } from '@/components/ui/input'
import { Users, Search, UserCheck, UserX, ShieldCheck } from 'lucide-react'
import { useState } from 'react'

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium' }).format(new Date(iso))
}

export default function AdminUsersPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')

  const { data: users = [], isLoading } = useQuery<AdminUser[]>({
    queryKey: ['admin', 'users'],
    queryFn: () => api.get<AdminUser[]>('/api/v1/admin/users').then(r => r.data),
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

  const filtered = users.filter(u =>
    `${u.firstname} ${u.lastname} ${u.email}`.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Users size={20} />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Utilisateurs</h1>
            <p className="text-sm text-muted-foreground">{users.length} compte{users.length > 1 ? 's' : ''} enregistré{users.length > 1 ? 's' : ''}</p>
          </div>
        </div>
      </div>

      {/* Search */}
      <div className="relative max-w-sm">
        <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder="Rechercher un utilisateur…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="h-9 pl-8 text-sm"
        />
      </div>

      {/* Table */}
      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="text-sm font-semibold">Liste des utilisateurs</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading
            ? <div className="space-y-3 p-4">{[1,2,3,4].map(i => <Skeleton key={i} className="h-14 w-full" />)}</div>
            : filtered.length === 0
              ? (
                <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
                  <Users size={28} className="opacity-40" />
                  <p className="text-sm">Aucun utilisateur trouvé.</p>
                </div>
              )
              : (
                <div className="divide-y divide-border">
                  {filtered.map(u => (
                    <div key={u.userId} className="flex items-center gap-4 px-4 py-3.5">
                      {/* Avatar */}
                      <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold uppercase text-muted-foreground">
                        {u.firstname[0]}{u.lastname[0]}
                      </div>

                      {/* Info */}
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <p className="truncate text-sm font-medium">{u.firstname} {u.lastname}</p>
                          {u.role === 'ADMIN' && (
                            <span className="inline-flex items-center gap-0.5 rounded-full bg-primary/10 px-1.5 py-0.5 text-[10px] font-semibold text-primary">
                              <ShieldCheck size={9} />
                              Admin
                            </span>
                          )}
                        </div>
                        <p className="truncate text-xs text-muted-foreground">{u.email}</p>
                      </div>

                      {/* Date */}
                      <p className="hidden shrink-0 text-xs text-muted-foreground sm:block">
                        {u.createdAt ? formatDate(u.createdAt) : '—'}
                      </p>

                      {/* Status badge */}
                      <span className={`hidden shrink-0 items-center rounded-full border px-2 py-0.5 text-[10px] font-medium sm:inline-flex ${
                        u.isActive
                          ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                          : 'border-red-200 bg-red-50 text-red-700'
                      }`}>
                        {u.isActive ? 'Actif' : 'Inactif'}
                      </span>

                      {/* Toggle — admins cannot be deactivated */}
                      {u.role !== 'ADMIN' && (
                        <Button
                          variant="ghost"
                          size="sm"
                          className={`h-8 shrink-0 gap-1.5 text-xs ${
                            u.isActive
                              ? 'text-red-600 hover:bg-red-50 hover:text-red-700'
                              : 'text-emerald-700 hover:bg-emerald-50'
                          }`}
                          onClick={() => toggleStatus.mutate({ id: u.userId, active: !u.isActive })}
                          disabled={toggleStatus.isPending}
                        >
                          {u.isActive
                            ? <><UserX size={13} /> Désactiver</>
                            : <><UserCheck size={13} /> Activer</>
                          }
                        </Button>
                      )}
                    </div>
                  ))}
                </div>
              )
          }
        </CardContent>
      </Card>
    </div>
  )
}
