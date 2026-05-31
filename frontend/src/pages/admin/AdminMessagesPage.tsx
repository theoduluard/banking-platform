import { useState, useMemo, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import api from '@/lib/api'
import type { Message, AdminUser, Page } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from '@/components/ui/dialog'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  Bell, Plus, Send, Loader2, Info, AlertTriangle, FileText, CheckCircle2, XCircle,
  Search, X, User,
} from 'lucide-react'
import { cn } from '@/lib/utils'

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

function initials(u: AdminUser) {
  return `${u.firstname[0] ?? ''}${u.lastname[0] ?? ''}`.toUpperCase()
}

const TYPE_CONFIG: Record<Message['type'], { icon: typeof Info; color: string; bg: string; label: string }> = {
  INFO:       { icon: Info,          color: 'text-blue-600',    bg: 'bg-blue-50',    label: 'Information' },
  WARNING:    { icon: AlertTriangle, color: 'text-amber-600',   bg: 'bg-amber-50',   label: 'Avertissement' },
  DOCUMENT:   { icon: FileText,      color: 'text-purple-600',  bg: 'bg-purple-50',  label: 'Document' },
  APPROVAL:   { icon: CheckCircle2,  color: 'text-emerald-600', bg: 'bg-emerald-50', label: 'Approbation' },
  REJECTION:  { icon: XCircle,       color: 'text-red-600',     bg: 'bg-red-50',     label: 'Refus' },
}

// ── User search combobox ──────────────────────────────────────────────────────

function UserSearchCombobox({
  value,
  onChange,
  error,
}: {
  value: string
  onChange: (userId: string) => void
  error?: string
}) {
  const [query,        setQuery]        = useState('')
  const [open,         setOpen]         = useState(false)
  const [highlighted,  setHighlighted]  = useState(0)
  const containerRef = useRef<HTMLDivElement>(null)
  const inputRef     = useRef<HTMLInputElement>(null)
  const listRef      = useRef<HTMLUListElement>(null)

  // Fetch all users once (shared cache with AdminUsersPage)
  const { data: allUsers = [], isLoading: loadingUsers } = useQuery<AdminUser[]>({
    queryKey: ['admin', 'users'],
    queryFn:  () => api.get<AdminUser[]>('/api/v1/admin/users').then(r => r.data),
    staleTime: 5 * 60_000,
  })

  const clients = useMemo(() => allUsers.filter(u => u.role === 'CLIENT'), [allUsers])

  // Resolve currently-selected user object from uuid
  const selected = useMemo(
    () => clients.find(u => u.userId === value) ?? null,
    [clients, value],
  )

  // Intelligent filter: firstname, lastname, reversed, email — partial match on any token
  const filtered = useMemo(() => {
    const q = query.toLowerCase().trim()
    if (!q) return clients
    return clients.filter(u => {
      const tokens = [
        u.firstname,
        u.lastname,
        `${u.firstname} ${u.lastname}`,
        `${u.lastname} ${u.firstname}`,
        u.email,
      ]
      return tokens.some(t => t.toLowerCase().includes(q))
    })
  }, [clients, query])

  // Close on outside click
  useEffect(() => {
    function onMouseDown(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onMouseDown)
    return () => document.removeEventListener('mousedown', onMouseDown)
  }, [])

  // Reset highlighted when list changes
  useEffect(() => { setHighlighted(0) }, [filtered])

  // Scroll highlighted item into view
  useEffect(() => {
    if (!listRef.current) return
    const item = listRef.current.children[highlighted] as HTMLElement | undefined
    item?.scrollIntoView({ block: 'nearest' })
  }, [highlighted])

  function handleSelect(user: AdminUser) {
    onChange(user.userId)
    setQuery('')
    setOpen(false)
  }

  function handleClear() {
    onChange('')
    setQuery('')
    setTimeout(() => inputRef.current?.focus(), 0)
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (!open && (e.key === 'ArrowDown' || e.key === 'Enter')) {
      setOpen(true)
      return
    }
    if (e.key === 'ArrowDown')  { e.preventDefault(); setHighlighted(h => Math.min(h + 1, filtered.length - 1)) }
    if (e.key === 'ArrowUp')    { e.preventDefault(); setHighlighted(h => Math.max(h - 1, 0)) }
    if (e.key === 'Enter')      { e.preventDefault(); if (filtered[highlighted]) handleSelect(filtered[highlighted]) }
    if (e.key === 'Escape')     { setOpen(false) }
  }

  // ── Highlighted text helper ───────────────────────────────────────────────
  function highlight(text: string) {
    if (!query.trim()) return <span>{text}</span>
    const re = new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
    const parts = text.split(re)
    return (
      <>
        {parts.map((p, i) =>
          re.test(p)
            ? <mark key={i} className="bg-primary/15 text-primary rounded-[2px] font-medium not-italic">{p}</mark>
            : <span key={i}>{p}</span>
        )}
      </>
    )
  }

  return (
    <div ref={containerRef} className="relative">
      {/* Selected pill OR search input */}
      {selected ? (
        <div className={cn(
          'flex h-11 w-full items-center gap-2.5 rounded-md border bg-background px-3',
          error ? 'border-destructive' : 'border-input',
        )}>
          {/* Avatar initials */}
          <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary/15 text-[10px] font-semibold text-primary">
            {initials(selected)}
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">
              {selected.firstname} {selected.lastname}
            </p>
            <p className="truncate text-[10px] text-muted-foreground">{selected.email}</p>
          </div>
          <button
            type="button"
            onClick={handleClear}
            className="shrink-0 rounded p-0.5 text-muted-foreground hover:text-foreground"
            aria-label="Effacer la sélection"
          >
            <X size={14} />
          </button>
        </div>
      ) : (
        <div className="relative">
          <Search
            size={14}
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
          />
          <Input
            ref={inputRef}
            value={query}
            onChange={e => { setQuery(e.target.value); setOpen(true) }}
            onFocus={() => setOpen(true)}
            onKeyDown={handleKeyDown}
            placeholder="Rechercher un client par nom ou e-mail…"
            className={cn('h-11 pl-9 pr-3', error && 'border-destructive')}
            autoComplete="off"
          />
          {loadingUsers && (
            <Loader2 size={13} className="absolute right-3 top-1/2 -translate-y-1/2 animate-spin text-muted-foreground" />
          )}
        </div>
      )}

      {/* Dropdown */}
      {open && !selected && (
        <div className="absolute z-50 mt-1 w-full rounded-md border bg-popover shadow-md">
          <ul
            ref={listRef}
            className="max-h-56 overflow-y-auto py-1"
            role="listbox"
          >
            {filtered.length === 0 ? (
              <li className="flex items-center gap-2 px-3 py-3 text-sm text-muted-foreground">
                <User size={14} className="opacity-50" />
                {query ? 'Aucun client trouvé' : 'Aucun client enregistré'}
              </li>
            ) : (
              filtered.map((u, idx) => (
                <li
                  key={u.userId}
                  role="option"
                  aria-selected={idx === highlighted}
                  onMouseDown={e => { e.preventDefault(); handleSelect(u) }}
                  onMouseEnter={() => setHighlighted(idx)}
                  className={cn(
                    'flex cursor-pointer items-center gap-2.5 px-3 py-2.5 text-sm',
                    idx === highlighted ? 'bg-accent text-accent-foreground' : 'hover:bg-accent/50',
                  )}
                >
                  {/* Avatar */}
                  <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-[11px] font-semibold text-primary">
                    {initials(u)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium leading-tight">
                      {highlight(`${u.firstname} ${u.lastname}`)}
                    </p>
                    <p className="truncate text-xs text-muted-foreground leading-tight">
                      {highlight(u.email)}
                    </p>
                  </div>
                  {!u.isActive && (
                    <span className="shrink-0 rounded-full border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-[9px] font-medium text-amber-700">
                      Inactif
                    </span>
                  )}
                </li>
              ))
            )}
          </ul>
        </div>
      )}

      {error && <p className="mt-1 text-xs text-destructive">{error}</p>}
    </div>
  )
}

// ── Send dialog ───────────────────────────────────────────────────────────────

const sendSchema = z.object({
  userId:  z.string().uuid('Veuillez sélectionner un client'),
  subject: z.string().min(1, 'Objet requis').max(200),
  body:    z.string().min(1, 'Message requis').max(5000),
  type:    z.enum(['INFO', 'WARNING', 'DOCUMENT', 'APPROVAL', 'REJECTION'] as const),
})
type SendForm = z.infer<typeof sendSchema>

function SendMessageDialog({
  open,
  onClose,
  onSuccess,
}: {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}) {
  const { handleSubmit, reset, setValue, watch, formState: { errors, isSubmitting } } = useForm<SendForm>({
    resolver: zodResolver(sendSchema),
    defaultValues: { type: 'INFO', userId: '' },
  })

  const userId = watch('userId')

  const mutation = useMutation({
    mutationFn: (data: SendForm) => api.post('/api/v1/admin/messages', data),
    onSuccess: () => {
      toast.success('Message envoyé')
      reset()
      onSuccess()
    },
    onError: () => toast.error('Impossible d\'envoyer le message'),
  })

  function handleClose() {
    if (!isSubmitting) { reset(); onClose() }
  }

  return (
    <Dialog open={open} onOpenChange={o => { if (!o) handleClose() }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Bell size={18} className="text-primary" />
            Envoyer un message
          </DialogTitle>
          <DialogDescription>
            Le message apparaîtra dans la boîte de réception du client.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(d => mutation.mutate(d))} className="space-y-4">

          {/* Client search */}
          <div className="space-y-1.5">
            <Label>Client destinataire</Label>
            <UserSearchCombobox
              value={userId}
              onChange={id => setValue('userId', id, { shouldValidate: !!id })}
              error={errors.userId?.message}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Type</Label>
              <Select defaultValue="INFO" onValueChange={v => setValue('type', v as SendForm['type'])}>
                <SelectTrigger className="h-11">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {Object.entries(TYPE_CONFIG).map(([k, v]) => (
                    <SelectItem key={k} value={k}>{v.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="subject">Objet</Label>
              <Input
                id="subject"
                onChange={e => setValue('subject', e.target.value, { shouldValidate: true })}
                placeholder="Objet du message"
                className="h-11"
              />
              {errors.subject && <p className="text-xs text-destructive">{errors.subject.message}</p>}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="body">Message</Label>
            <Textarea
              id="body"
              onChange={e => setValue('body', e.target.value, { shouldValidate: true })}
              rows={4}
              placeholder="Contenu du message…"
              className="resize-none"
            />
            {errors.body && <p className="text-xs text-destructive">{errors.body.message}</p>}
          </div>

          <DialogFooter className="gap-2 pt-1">
            <Button type="button" variant="outline" onClick={handleClose} disabled={isSubmitting} className="flex-1">
              Annuler
            </Button>
            <Button type="submit" disabled={isSubmitting} className="flex-1 gap-2">
              {isSubmitting ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
              <span>{isSubmitting ? 'Envoi…' : 'Envoyer'}</span>
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function AdminMessagesPage() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [sendOpen, setSendOpen] = useState(false)

  const { data: msgPage, isLoading } = useQuery<Page<Message>>({
    queryKey: ['admin', 'messages', page],
    queryFn: () => api.get<Page<Message>>(`/api/v1/admin/messages?page=${page}&size=20`).then(r => r.data),
  })

  // Reuse the users cache (shared with UserSearchCombobox) to resolve userId → name
  const { data: allUsers = [] } = useQuery<AdminUser[]>({
    queryKey: ['admin', 'users'],
    queryFn:  () => api.get<AdminUser[]>('/api/v1/admin/users').then(r => r.data),
    staleTime: 5 * 60_000,
  })
  const userMap = useMemo(
    () => new Map(allUsers.map(u => [u.userId, u])),
    [allUsers],
  )

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-blue-100 text-blue-700">
            <Bell size={20} />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Messages</h1>
            <p className="text-sm text-muted-foreground">
              {msgPage ? `${msgPage.totalElements} message${msgPage.totalElements > 1 ? 's' : ''}` : '—'}
            </p>
          </div>
        </div>
        <Button size="sm" className="gap-1.5" onClick={() => setSendOpen(true)}>
          <Plus size={15} />
          <span>Envoyer un message</span>
        </Button>
      </div>

      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="text-sm font-semibold">Tous les messages</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading && (
            <div className="space-y-3 p-4">
              {[1, 2, 3].map(i => <Skeleton key={i} className="h-14 w-full" />)}
            </div>
          )}

          {!isLoading && msgPage?.content.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
              <Bell size={28} className="opacity-40" />
              <p className="text-sm">Aucun message envoyé.</p>
            </div>
          )}

          {!isLoading && msgPage && msgPage.content.length > 0 && (
            <>
              <div className="divide-y divide-border">
                {msgPage.content.map(msg => {
                  const { icon: Icon, color, bg, label } = TYPE_CONFIG[msg.type]
                  return (
                    <div key={msg.id} className="flex items-start gap-3 px-4 py-3.5">
                      <div className={cn('mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-xl', bg)}>
                        <Icon size={16} className={color} />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <p className="truncate text-sm font-medium">{msg.subject}</p>
                          <span className={cn('shrink-0 rounded-full border px-2 py-0.5 text-[10px] font-medium', bg, color, 'border-current/20')}>
                            {label}
                          </span>
                          {!msg.isRead && (
                            <span className="size-1.5 shrink-0 rounded-full bg-primary" />
                          )}
                        </div>
                        <p className="mt-0.5 line-clamp-1 text-xs text-muted-foreground">{msg.body}</p>
                        {(() => {
                          const u = userMap.get(msg.userId)
                          return u ? (
                            <p className="mt-0.5 text-[10px] text-muted-foreground/70">
                              → {u.firstname} {u.lastname}
                              <span className="ml-1 opacity-50">({u.email})</span>
                            </p>
                          ) : (
                            <p className="mt-0.5 font-mono text-[10px] text-muted-foreground/50">{msg.userId}</p>
                          )
                        })()}
                      </div>
                      <p className="shrink-0 text-[10px] text-muted-foreground whitespace-nowrap">
                        {formatDate(msg.createdAt)}
                      </p>
                    </div>
                  )
                })}
              </div>

              {msgPage.totalPages > 1 && (
                <>
                  <Separator />
                  <div className="flex items-center justify-between px-4 py-3 text-xs text-muted-foreground">
                    <span>Page {msgPage.number + 1} / {msgPage.totalPages}</span>
                    <div className="flex gap-2">
                      <Button variant="outline" size="sm" className="h-7 text-xs"
                        disabled={msgPage.first} onClick={() => setPage(p => p - 1)}>Précédent</Button>
                      <Button variant="outline" size="sm" className="h-7 text-xs"
                        disabled={msgPage.last} onClick={() => setPage(p => p + 1)}>Suivant</Button>
                    </div>
                  </div>
                </>
              )}
            </>
          )}
        </CardContent>
      </Card>

      <SendMessageDialog
        open={sendOpen}
        onClose={() => setSendOpen(false)}
        onSuccess={() => {
          setSendOpen(false)
          qc.invalidateQueries({ queryKey: ['admin', 'messages'] })
        }}
      />
    </div>
  )
}
