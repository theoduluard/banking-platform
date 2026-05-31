import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Message, Page } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Bell, CheckCheck, Info, AlertTriangle, FileText, CheckCircle2, XCircle, Paperclip } from 'lucide-react'
import { cn } from '@/lib/utils'

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

const TYPE_CONFIG: Record<Message['type'], { icon: typeof Info; color: string; bg: string }> = {
  INFO:       { icon: Info,          color: 'text-blue-600',    bg: 'bg-blue-50' },
  WARNING:    { icon: AlertTriangle, color: 'text-amber-600',   bg: 'bg-amber-50' },
  DOCUMENT:   { icon: FileText,      color: 'text-purple-600',  bg: 'bg-purple-50' },
  APPROVAL:   { icon: CheckCircle2,  color: 'text-emerald-600', bg: 'bg-emerald-50' },
  REJECTION:  { icon: XCircle,       color: 'text-red-600',     bg: 'bg-red-50' },
}

// ── Message detail dialog ─────────────────────────────────────────────────────

function MessageDialog({
  message,
  open,
  onClose,
}: {
  message: Message | null
  open: boolean
  onClose: () => void
}) {
  if (!message) return null

  const { icon: Icon, color, bg } = TYPE_CONFIG[message.type]

  return (
    <Dialog open={open} onOpenChange={o => { if (!o) onClose() }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2.5 text-base">
            <div className={cn('flex size-8 items-center justify-center rounded-lg', bg)}>
              <Icon size={16} className={color} />
            </div>
            {message.subject}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <p className="text-xs text-muted-foreground">{formatDate(message.createdAt)}</p>
          <p className="text-sm leading-relaxed whitespace-pre-wrap">{message.body}</p>

          {message.attachmentBase64 && (
            <div className="rounded-xl border border-border bg-muted/40 p-4">
              <p className="mb-3 flex items-center gap-1.5 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                <Paperclip size={11} />
                Pièce jointe — {message.attachmentFilename ?? 'document'}
              </p>
              <img
                src={`data:${message.attachmentContentType};base64,${message.attachmentBase64}`}
                alt="Pièce jointe"
                className="w-full rounded-lg border object-contain shadow-sm"
              />
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Fermer</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function MessagesPage() {
  const userId      = getUserIdFromToken()
  const qc          = useQueryClient()
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<Message | null>(null)

  const { data: msgPage, isLoading } = useQuery<Page<Message>>({
    queryKey: ['messages', userId, page],
    queryFn:  () => api.get<Page<Message>>(`/api/v1/messages?page=${page}&size=20`).then(r => r.data),
    enabled: !!userId,
  })

  const markRead = useMutation({
    mutationFn: (id: string) => api.patch(`/api/v1/messages/${id}/read`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['messages', userId] })
      qc.invalidateQueries({ queryKey: ['messages-unread', userId] })
    },
  })

  function openMessage(msg: Message) {
    setSelected(msg)
    if (!msg.isRead) markRead.mutate(msg.id)
  }

  const unreadCount = msgPage?.content.filter(m => !m.isRead).length ?? 0

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2.5">
          <h1 className="text-2xl font-semibold tracking-tight">Messages</h1>
          {unreadCount > 0 && (
            <span className="flex size-5 items-center justify-center rounded-full bg-primary text-[10px] font-bold text-primary-foreground">
              {unreadCount}
            </span>
          )}
        </div>
        <p className="mt-1 text-sm text-muted-foreground">Notifications et documents envoyés par notre équipe.</p>
      </div>

      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="text-sm font-semibold">
            Boîte de réception
            {msgPage && (
              <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-[11px] font-normal text-muted-foreground">
                {msgPage.totalElements}
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

          {!isLoading && msgPage?.content.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-14 text-center text-muted-foreground">
              <div className="flex size-14 items-center justify-center rounded-2xl bg-muted">
                <Bell size={24} className="opacity-40" />
              </div>
              <div>
                <p className="text-sm font-medium">Aucun message</p>
                <p className="mt-0.5 text-xs">Vous recevrez ici les notifications de notre équipe.</p>
              </div>
            </div>
          )}

          {!isLoading && msgPage && msgPage.content.length > 0 && (
            <div className="divide-y divide-border">
              {msgPage.content.map(msg => {
                const { icon: Icon, color, bg } = TYPE_CONFIG[msg.type]
                return (
                  <button
                    key={msg.id}
                    onClick={() => openMessage(msg)}
                    className={cn(
                      'flex w-full items-start gap-3 px-4 py-3.5 text-left transition-colors hover:bg-muted/40',
                      !msg.isRead && 'bg-primary/[0.03]',
                    )}
                  >
                    <div className={cn('mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-xl', bg)}>
                      <Icon size={16} className={color} />
                    </div>

                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <p className={cn('truncate text-sm', !msg.isRead ? 'font-semibold' : 'font-medium')}>
                          {msg.subject}
                        </p>
                        {!msg.isRead && (
                          <span className="size-2 shrink-0 rounded-full bg-primary" />
                        )}
                        {msg.attachmentBase64 && (
                          <Paperclip size={11} className="shrink-0 text-muted-foreground" />
                        )}
                      </div>
                      <p className="mt-0.5 line-clamp-1 text-xs text-muted-foreground">{msg.body}</p>
                    </div>

                    <p className="shrink-0 text-[10px] text-muted-foreground whitespace-nowrap">
                      {formatDate(msg.createdAt)}
                    </p>
                  </button>
                )
              })}
            </div>
          )}

          {/* Pagination */}
          {msgPage && msgPage.totalPages > 1 && (
            <>
              <Separator />
              <div className="flex items-center justify-between px-4 py-3 text-xs text-muted-foreground">
                <span>Page {msgPage.number + 1} / {msgPage.totalPages}</span>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" className="h-7 text-xs"
                    disabled={msgPage.first} onClick={() => setPage(p => p - 1)}>
                    Précédent
                  </Button>
                  <Button variant="outline" size="sm" className="h-7 text-xs"
                    disabled={msgPage.last} onClick={() => setPage(p => p + 1)}>
                    Suivant
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <MessageDialog message={selected} open={!!selected} onClose={() => setSelected(null)} />
    </div>
  )
}
