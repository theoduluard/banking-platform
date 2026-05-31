import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { SupportRequest, SupportRequestDetail, RequestType } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from '@/components/ui/dialog'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  MessageSquare, Plus, Loader2, Send, Paperclip, User, ShieldCheck, Clock,
  CheckCircle2, XCircle, ArrowRight,
} from 'lucide-react'
import { cn } from '@/lib/utils'

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

const REQUEST_TYPES: { value: RequestType; label: string }[] = [
  { value: 'ACCOUNT_CLOSURE',   label: 'Clôture de compte' },
  { value: 'DISPUTE',           label: 'Contestation de transaction' },
  { value: 'DOCUMENT_REQUEST',  label: 'Demande de document' },
  { value: 'OTHER',             label: 'Autre demande' },
]

const STATUS_CONFIG: Record<SupportRequest['status'], { label: string; className: string; icon: typeof Clock }> = {
  OPEN:        { label: 'Ouvert',     className: 'border-blue-200 bg-blue-50 text-blue-700',      icon: Clock },
  IN_PROGRESS: { label: 'En cours',   className: 'border-amber-200 bg-amber-50 text-amber-700',   icon: ArrowRight },
  RESOLVED:    { label: 'Résolu',     className: 'border-emerald-200 bg-emerald-50 text-emerald-700', icon: CheckCircle2 },
  REJECTED:    { label: 'Refusé',     className: 'border-red-200 bg-red-50 text-red-700',          icon: XCircle },
}

interface FileData {
  base64: string
  contentType: string
  filename: string
}

function readFileAsBase64(file: File): Promise<FileData> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const dataUrl  = reader.result as string
      const [prefix, base64] = dataUrl.split(',')
      const contentType = prefix.replace('data:', '').replace(';base64', '')
      resolve({ base64, contentType, filename: file.name })
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

// ── Create request dialog ─────────────────────────────────────────────────────

const createSchema = z.object({
  type:    z.enum(['ACCOUNT_CLOSURE', 'DISPUTE', 'DOCUMENT_REQUEST', 'OTHER'] as const, { message: 'Type requis' }),
  subject: z.string().min(1, 'Objet requis').max(200, 'Maximum 200 caractères'),
  body:    z.string().min(1, 'Message requis').max(5000, 'Maximum 5000 caractères'),
})
type CreateForm = z.infer<typeof createSchema>

function CreateRequestDialog({
  open,
  onClose,
  onSuccess,
}: {
  open: boolean
  onClose: () => void
  onSuccess: () => void
}) {
  const [attachment, setAttachment] = useState<FileData | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const { register, handleSubmit, reset, setValue, formState: { errors, isSubmitting } } = useForm<CreateForm>({
    resolver: zodResolver(createSchema),
  })

  async function handleFile(file: File | undefined) {
    if (!file) return
    if (file.size > 10 * 1024 * 1024) { toast.error('Fichier trop volumineux (max 10 Mo)'); return }
    try { setAttachment(await readFileAsBase64(file)) }
    catch { toast.error('Impossible de lire le fichier') }
  }

  const mutation = useMutation({
    mutationFn: (data: CreateForm) =>
      api.post('/api/v1/requests', {
        ...data,
        attachmentBase64:      attachment?.base64 ?? null,
        attachmentContentType: attachment?.contentType ?? null,
        attachmentFilename:    attachment?.filename ?? null,
      }),
    onSuccess: () => {
      toast.success('Demande envoyée')
      reset()
      setAttachment(null)
      onSuccess()
    },
    onError: () => toast.error('Impossible d\'envoyer la demande'),
  })

  function handleClose() {
    if (!isSubmitting) { reset(); setAttachment(null); onClose() }
  }

  return (
    <Dialog open={open} onOpenChange={o => { if (!o) handleClose() }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <MessageSquare size={18} className="text-primary" />
            Nouvelle demande
          </DialogTitle>
          <DialogDescription>
            Notre équipe vous répondra dans les meilleurs délais.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(d => mutation.mutate(d))} className="space-y-4">
          <div className="space-y-2">
            <Label>Type de demande</Label>
            <Select onValueChange={v => setValue('type', v as RequestType)}>
              <SelectTrigger className="h-11">
                <SelectValue placeholder="Sélectionner…" />
              </SelectTrigger>
              <SelectContent>
                {REQUEST_TYPES.map(t => (
                  <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.type && <p className="text-xs text-destructive">{errors.type.message}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="subject">Objet</Label>
            <Input id="subject" {...register('subject')} placeholder="Résumé de votre demande" className="h-11" />
            {errors.subject && <p className="text-xs text-destructive">{errors.subject.message}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="body">Message</Label>
            <Textarea
              id="body"
              {...register('body')}
              rows={4}
              placeholder="Décrivez votre demande en détail…"
              className="resize-none"
            />
            {errors.body && <p className="text-xs text-destructive">{errors.body.message}</p>}
          </div>

          {/* Attachment */}
          <div>
            <input
              ref={fileRef}
              type="file"
              className="sr-only"
              onChange={e => handleFile(e.target.files?.[0])}
            />
            {attachment ? (
              <div className="flex items-center gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-sm">
                <Paperclip size={13} className="text-muted-foreground" />
                <span className="flex-1 truncate text-xs">{attachment.filename}</span>
                <button type="button" onClick={() => setAttachment(null)} className="text-xs text-muted-foreground hover:text-destructive">
                  Supprimer
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => fileRef.current?.click()}
                className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-primary"
              >
                <Paperclip size={12} />
                Joindre un fichier (optionnel)
              </button>
            )}
          </div>

          <DialogFooter className="gap-2 pt-2">
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

// ── Request detail dialog ─────────────────────────────────────────────────────

function RequestDetailDialog({
  requestId,
  open,
  onClose,
}: {
  requestId: string | null
  open: boolean
  onClose: () => void
}) {
  const userId    = getUserIdFromToken()
  const qc        = useQueryClient()
  const [replyBody, setReplyBody] = useState('')
  const [attachment, setAttachment] = useState<FileData | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const { data: detail, isLoading } = useQuery<SupportRequestDetail>({
    queryKey: ['request', requestId],
    queryFn:  () => api.get<SupportRequestDetail>(`/api/v1/requests/${requestId}`).then(r => r.data),
    enabled: !!requestId && open,
  })

  const replyMutation = useMutation({
    mutationFn: () => api.post(`/api/v1/requests/${requestId}/replies`, {
      body:                  replyBody,
      attachmentBase64:      attachment?.base64 ?? null,
      attachmentContentType: attachment?.contentType ?? null,
      attachmentFilename:    attachment?.filename ?? null,
    }),
    onSuccess: () => {
      setReplyBody('')
      setAttachment(null)
      qc.invalidateQueries({ queryKey: ['request', requestId] })
      qc.invalidateQueries({ queryKey: ['requests', userId] })
      toast.success('Réponse envoyée')
    },
    onError: () => toast.error('Impossible d\'envoyer la réponse'),
  })

  async function handleFile(file: File | undefined) {
    if (!file) return
    if (file.size > 10 * 1024 * 1024) { toast.error('Fichier trop volumineux (max 10 Mo)'); return }
    try { setAttachment(await readFileAsBase64(file)) }
    catch { toast.error('Impossible de lire le fichier') }
  }

  const isClosed = detail?.status === 'RESOLVED' || detail?.status === 'REJECTED'
  const statusInfo = detail ? STATUS_CONFIG[detail.status] : null

  return (
    <Dialog open={open} onOpenChange={o => { if (!o) onClose() }}>
      <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-hidden flex flex-col">
        <DialogHeader className="shrink-0">
          <DialogTitle className="flex items-start gap-2 text-base">
            {detail?.subject ?? 'Demande'}
          </DialogTitle>
          {statusInfo && (
            <span className={cn('inline-flex w-fit items-center rounded-full border px-2.5 py-0.5 text-[10px] font-medium', statusInfo.className)}>
              {statusInfo.label}
            </span>
          )}
        </DialogHeader>

        <div className="flex-1 overflow-y-auto space-y-4 pr-1">
          {isLoading && (
            <div className="space-y-3">
              {[1, 2].map(i => <Skeleton key={i} className="h-20 w-full" />)}
            </div>
          )}

          {detail && (
            <>
              {/* Original request */}
              <div className="rounded-xl border bg-muted/30 p-4">
                <div className="mb-2 flex items-center gap-2">
                  <div className="flex size-6 items-center justify-center rounded-full bg-primary/10">
                    <User size={12} className="text-primary" />
                  </div>
                  <span className="text-xs font-medium">Vous</span>
                  <span className="text-xs text-muted-foreground">· {formatDate(detail.createdAt)}</span>
                </div>
                <p className="text-sm leading-relaxed whitespace-pre-wrap">{detail.body}</p>
                {detail.attachmentBase64 && (
                  <div className="mt-3">
                    <img
                      src={`data:${detail.attachmentContentType};base64,${detail.attachmentBase64}`}
                      alt="Pièce jointe"
                      className="max-h-40 rounded-lg border object-contain"
                    />
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
                        {isAdmin
                          ? <ShieldCheck size={12} className="text-primary" />
                          : <User size={12} className="text-muted-foreground" />}
                      </div>
                      <span className="text-xs font-medium">{isAdmin ? 'Équipe SolarisBank' : 'Vous'}</span>
                      <span className="text-xs text-muted-foreground">· {formatDate(r.createdAt)}</span>
                    </div>
                    <p className="text-sm leading-relaxed whitespace-pre-wrap">{r.body}</p>
                    {r.attachmentBase64 && (
                      <div className="mt-3">
                        <img
                          src={`data:${r.attachmentContentType};base64,${r.attachmentBase64}`}
                          alt="Pièce jointe"
                          className="max-h-40 rounded-lg border object-contain"
                        />
                        <p className="mt-1 text-[10px] text-muted-foreground">{r.attachmentFilename}</p>
                      </div>
                    )}
                  </div>
                )
              })}
            </>
          )}
        </div>

        {/* Reply form (only if not closed) */}
        {!isClosed && detail && (
          <div className="shrink-0 space-y-2 border-t pt-4">
            <Textarea
              value={replyBody}
              onChange={e => setReplyBody(e.target.value)}
              rows={3}
              placeholder="Votre réponse…"
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
        )}

        {isClosed && (
          <p className="shrink-0 border-t pt-3 text-center text-xs text-muted-foreground">
            Cette demande est clôturée.
          </p>
        )}
      </DialogContent>
    </Dialog>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function RequestsPage() {
  const userId = getUserIdFromToken()
  const qc     = useQueryClient()
  const [createOpen,  setCreateOpen]  = useState(false)
  const [selectedId,  setSelectedId]  = useState<string | null>(null)

  const { data: requests = [], isLoading } = useQuery<SupportRequest[]>({
    queryKey: ['requests', userId],
    queryFn:  () => api.get<SupportRequest[]>('/api/v1/requests').then(r => r.data),
    enabled: !!userId,
  })

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Demandes</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Contactez notre équipe pour toute question ou réclamation.
          </p>
        </div>
        <Button size="sm" className="gap-1.5" onClick={() => setCreateOpen(true)}>
          <Plus size={15} />
          <span>Nouvelle demande</span>
        </Button>
      </div>

      {/* List */}
      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="text-sm font-semibold">
            Mes demandes
            {requests.length > 0 && (
              <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-[11px] font-normal text-muted-foreground">
                {requests.length}
              </span>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading && (
            <div className="space-y-3 p-4">
              {[1, 2].map(i => <Skeleton key={i} className="h-16 w-full" />)}
            </div>
          )}

          {!isLoading && requests.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-14 text-center text-muted-foreground">
              <div className="flex size-14 items-center justify-center rounded-2xl bg-muted">
                <MessageSquare size={24} className="opacity-40" />
              </div>
              <div>
                <p className="text-sm font-medium">Aucune demande</p>
                <p className="mt-0.5 text-xs">Créez une demande pour contacter notre équipe.</p>
              </div>
              <Button size="sm" variant="outline" className="mt-1 gap-1.5" onClick={() => setCreateOpen(true)}>
                <Plus size={14} />
                Nouvelle demande
              </Button>
            </div>
          )}

          {!isLoading && requests.length > 0 && (
            <div className="divide-y divide-border">
              {requests.map(req => {
                const type  = REQUEST_TYPES.find(t => t.value === req.type)
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
                        {type?.label} · {formatDate(req.createdAt)}
                      </p>
                    </div>
                  </button>
                )
              })}
            </div>
          )}
        </CardContent>
      </Card>

      <CreateRequestDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onSuccess={() => {
          setCreateOpen(false)
          qc.invalidateQueries({ queryKey: ['requests', userId] })
        }}
      />

      <RequestDetailDialog
        requestId={selectedId}
        open={!!selectedId}
        onClose={() => setSelectedId(null)}
      />
    </div>
  )
}
