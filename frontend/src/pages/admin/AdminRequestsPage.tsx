import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import api from '@/lib/api'
import type { SupportRequest, SupportRequestDetail, RequestStatus } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  MessageSquare, Send, Loader2, Clock, ArrowRight, CheckCircle2, XCircle,
  User, ShieldCheck, Paperclip, Filter,
} from 'lucide-react'
import { cn } from '@/lib/utils'

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

const REQUEST_TYPE_LABELS: Record<string, string> = {
  ACCOUNT_CLOSURE:  'Clôture de compte',
  DISPUTE:          'Contestation',
  DOCUMENT_REQUEST: 'Document',
  OTHER:            'Autre',
}

const STATUS_CONFIG: Record<RequestStatus, { label: string; className: string; icon: typeof Clock }> = {
  OPEN:        { label: 'Ouvert',   className: 'border-blue-200 bg-blue-50 text-blue-700',         icon: Clock },
  IN_PROGRESS: { label: 'En cours', className: 'border-amber-200 bg-amber-50 text-amber-700',      icon: ArrowRight },
  RESOLVED:    { label: 'Résolu',   className: 'border-emerald-200 bg-emerald-50 text-emerald-700', icon: CheckCircle2 },
  REJECTED:    { label: 'Refusé',   className: 'border-red-200 bg-red-50 text-red-700',             icon: XCircle },
}

interface FileData { base64: string; contentType: string; filename: string }

function readFileAsBase64(file: File): Promise<FileData> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const [prefix, base64] = (reader.result as string).split(',')
      resolve({ base64, contentType: prefix.replace('data:', '').replace(';base64', ''), filename: file.name })
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

// ── Request detail + reply dialog ─────────────────────────────────────────────

function RequestDetailDialog({
  requestId,
  open,
  onClose,
}: {
  requestId: string | null
  open: boolean
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [replyBody,   setReplyBody]   = useState('')
  const [newStatus,   setNewStatus]   = useState<RequestStatus | ''>('')
  const [attachment,  setAttachment]  = useState<FileData | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const { data: detail, isLoading } = useQuery<SupportRequestDetail>({
    queryKey: ['admin', 'request', requestId],
    queryFn:  () => api.get<SupportRequestDetail>(`/api/v1/admin/requests/${requestId}`).then(r => r.data),
    enabled: !!requestId && open,
  })

  const replyMutation = useMutation({
    mutationFn: () => api.post(`/api/v1/admin/requests/${requestId}/replies`, {
      body:                  replyBody,
      newStatus:             newStatus || null,
      attachmentBase64:      attachment?.base64 ?? null,
      attachmentContentType: attachment?.contentType ?? null,
      attachmentFilename:    attachment?.filename ?? null,
    }),
    onSuccess: () => {
      setReplyBody('')
      setNewStatus('')
      setAttachment(null)
      qc.invalidateQueries({ queryKey: ['admin', 'request', requestId] })
      qc.invalidateQueries({ queryKey: ['admin', 'requests'] })
      toast.success('Réponse envoyée')
    },
    onError: () => toast.error('Impossible d\'envoyer la réponse'),
  })

  async function handleFile(file: File | undefined) {
    if (!file) return
    if (file.size > 10 * 1024 * 1024) { toast.error('Fichier trop volumineux'); return }
    try { setAttachment(await readFileAsBase64(file)) }
    catch { toast.error('Impossible de lire le fichier') }
  }

  const statusInfo = detail ? STATUS_CONFIG[detail.status] : null

  return (
    <Dialog open={open} onOpenChange={o => { if (!o) onClose() }}>
      <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-hidden flex flex-col">
        <DialogHeader className="shrink-0">
          <div className="flex items-start justify-between gap-3">
            <DialogTitle className="text-base leading-tight">{detail?.subject ?? '…'}</DialogTitle>
            {statusInfo && (
              <span className={cn('shrink-0 rounded-full border px-2.5 py-0.5 text-[10px] font-medium', statusInfo.className)}>
                {statusInfo.label}
              </span>
            )}
          </div>
          {detail && (
            <p className="text-xs text-muted-foreground">
              {REQUEST_TYPE_LABELS[detail.type]} · Client <span className="font-mono">{detail.userId.slice(0, 8)}…</span>
            </p>
          )}
        </DialogHeader>

        {/* Thread */}
        <div className="flex-1 overflow-y-auto space-y-4 pr-1">
          {isLoading && (
            <div className="space-y-3">
              {[1, 2].map(i => <Skeleton key={i} className="h-20 w-full" />)}
            </div>
          )}

          {detail && (
            <>
              {/* Original */}
              <div className="rounded-xl border bg-muted/30 p-4">
                <div className="mb-2 flex items-center gap-2">
                  <div className="flex size-6 items-center justify-center rounded-full bg-muted">
                    <User size={12} className="text-muted-foreground" />
                  </div>
                  <span className="text-xs font-medium">Client</span>
                  <span className="text-xs text-muted-foreground">· {formatDate(detail.createdAt)}</span>
                </div>
                <p className="text-sm leading-relaxed whitespace-pre-wrap">{detail.body}</p>
                {detail.attachmentBase64 && (
                  <div className="mt-3">
                    <img src={`data:${detail.attachmentContentType};base64,${detail.attachmentBase64}`}
                      alt="Pièce jointe" className="max-h-48 rounded-lg border object-contain" />
                    <p className="mt-1 text-[10px] text-muted-foreground">{detail.attachmentFilename}</p>
                  </div>
                )}
              </div>

              {/* Replies */}
              {detail.replies.map(r => {
                const isAdmin = r.authorType === 'ADMIN'
                return (
                  <div key={r.id} className={cn('rounded-xl border p-4', isAdmin ? 'bg-primary/[0.04] border-primary/20' : 'bg-muted/30')}>
                    <div className="mb-2 flex items-center gap-2">
                      <div className={cn('flex size-6 items-center justify-center rounded-full', isAdmin ? 'bg-primary/15' : 'bg-muted')}>
                        {isAdmin ? <ShieldCheck size={12} className="text-primary" /> : <User size={12} className="text-muted-foreground" />}
                      </div>
                      <span className="text-xs font-medium">{isAdmin ? 'Admin' : 'Client'}</span>
                      <span className="text-xs text-muted-foreground">· {formatDate(r.createdAt)}</span>
                    </div>
                    <p className="text-sm leading-relaxed whitespace-pre-wrap">{r.body}</p>
                    {r.attachmentBase64 && (
                      <div className="mt-3">
                        <img src={`data:${r.attachmentContentType};base64,${r.attachmentBase64}`}
                          alt="Pièce jointe" className="max-h-48 rounded-lg border object-contain" />
                      </div>
                    )}
                  </div>
                )
              })}
            </>
          )}
        </div>

        {/* Reply form */}
        <div className="shrink-0 space-y-3 border-t pt-4">
          <div className="flex gap-2">
            <Select value={newStatus} onValueChange={v => setNewStatus(v as RequestStatus | '')}>
              <SelectTrigger className="h-9 w-44 text-xs">
                <SelectValue placeholder="Changer statut…" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">Inchangé</SelectItem>
                {Object.entries(STATUS_CONFIG).map(([k, v]) => (
                  <SelectItem key={k} value={k}>{v.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <Textarea
            value={replyBody}
            onChange={e => setReplyBody(e.target.value)}
            rows={3}
            placeholder="Votre réponse au client…"
            className="resize-none text-sm"
            disabled={replyMutation.isPending}
          />

          <input ref={fileRef} type="file" className="sr-only" onChange={e => handleFile(e.target.files?.[0])} />

          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              {attachment ? (
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Paperclip size={11} />
                  <span className="max-w-[160px] truncate">{attachment.filename}</span>
                  <button type="button" onClick={() => setAttachment(null)} className="hover:text-destructive">✕</button>
                </div>
              ) : (
                <button type="button" onClick={() => fileRef.current?.click()} className="flex items-center gap-1 text-xs text-muted-foreground hover:text-primary">
                  <Paperclip size={12} />
                  Joindre
                </button>
              )}
            </div>
            <Button
              size="sm"
              className="gap-1.5"
              disabled={!replyBody.trim() || replyMutation.isPending}
              onClick={() => replyMutation.mutate()}
            >
              {replyMutation.isPending ? <Loader2 size={13} className="animate-spin" /> : <Send size={13} />}
              <span>Répondre</span>
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function AdminRequestsPage() {
  const [page, setPage]         = useState(0)
  const [filter, setFilter]     = useState<RequestStatus | ''>('')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const { data: reqPage, isLoading } = useQuery<{ content: SupportRequest[]; totalElements: number; totalPages: number; number: number; first: boolean; last: boolean }>({
    queryKey: ['admin', 'requests', page, filter],
    queryFn: () =>
      api.get(`/api/v1/admin/requests?page=${page}&size=20${filter ? `&status=${filter}` : ''}`).then(r => r.data),
    refetchInterval: 30_000,
  })

  const { data: stats } = useQuery<{ openCount: number }>({
    queryKey: ['admin', 'requests', 'stats'],
    queryFn: () => api.get('/api/v1/admin/requests/stats').then(r => r.data),
    refetchInterval: 30_000,
  })

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex size-10 items-center justify-center rounded-xl bg-purple-100 text-purple-700">
          <MessageSquare size={20} />
        </div>
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Demandes clients</h1>
          <p className="text-sm text-muted-foreground">
            {reqPage ? `${reqPage.totalElements} demande${reqPage.totalElements > 1 ? 's' : ''}` : '—'}
            {(stats?.openCount ?? 0) > 0 && (
              <span className="ml-2 rounded-full bg-blue-100 px-2 py-0.5 text-[10px] font-medium text-blue-700">
                {stats!.openCount} ouverte{stats!.openCount > 1 ? 's' : ''}
              </span>
            )}
          </p>
        </div>
      </div>

      <Card>
        <CardHeader className="border-b pb-4">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-semibold">Toutes les demandes</CardTitle>
            <div className="flex items-center gap-1.5">
              <Filter size={13} className="text-muted-foreground" />
              <Select value={filter} onValueChange={v => { setFilter(v as RequestStatus | ''); setPage(0) }}>
                <SelectTrigger className="h-8 w-36 text-xs">
                  <SelectValue placeholder="Tous les statuts" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="">Tous les statuts</SelectItem>
                  {Object.entries(STATUS_CONFIG).map(([k, v]) => (
                    <SelectItem key={k} value={k}>{v.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading && (
            <div className="space-y-3 p-4">
              {[1, 2, 3].map(i => <Skeleton key={i} className="h-16 w-full" />)}
            </div>
          )}

          {!isLoading && reqPage?.content.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
              <MessageSquare size={28} className="opacity-40" />
              <p className="text-sm">Aucune demande.</p>
            </div>
          )}

          {!isLoading && reqPage && reqPage.content.length > 0 && (
            <>
              <div className="divide-y divide-border">
                {reqPage.content.map(req => {
                  const sinfo = STATUS_CONFIG[req.status]
                  return (
                    <button
                      key={req.id}
                      onClick={() => setSelectedId(req.id)}
                      className="flex w-full items-start gap-3 px-4 py-3.5 text-left transition-colors hover:bg-muted/40"
                    >
                      <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary/10">
                        <MessageSquare size={15} className="text-primary" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <p className="truncate text-sm font-medium">{req.subject}</p>
                          <span className={cn('shrink-0 rounded-full border px-2 py-0.5 text-[10px] font-medium', sinfo.className)}>
                            {sinfo.label}
                          </span>
                        </div>
                        <p className="mt-0.5 text-xs text-muted-foreground">
                          {REQUEST_TYPE_LABELS[req.type]} · <span className="font-mono">{req.userId.slice(0, 8)}…</span> · {formatDate(req.createdAt)}
                        </p>
                      </div>
                    </button>
                  )
                })}
              </div>

              {reqPage.totalPages > 1 && (
                <>
                  <Separator />
                  <div className="flex items-center justify-between px-4 py-3 text-xs text-muted-foreground">
                    <span>Page {reqPage.number + 1} / {reqPage.totalPages}</span>
                    <div className="flex gap-2">
                      <Button variant="outline" size="sm" className="h-7 text-xs"
                        disabled={reqPage.first} onClick={() => setPage(p => p - 1)}>Précédent</Button>
                      <Button variant="outline" size="sm" className="h-7 text-xs"
                        disabled={reqPage.last} onClick={() => setPage(p => p + 1)}>Suivant</Button>
                    </div>
                  </div>
                </>
              )}
            </>
          )}
        </CardContent>
      </Card>

      <RequestDetailDialog
        requestId={selectedId}
        open={!!selectedId}
        onClose={() => setSelectedId(null)}
      />
    </div>
  )
}
