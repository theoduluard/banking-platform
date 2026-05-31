import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import api from '@/lib/api'
import type { Message, Page } from '@/types'
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
} from 'lucide-react'
import { cn } from '@/lib/utils'

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

const TYPE_CONFIG: Record<Message['type'], { icon: typeof Info; color: string; bg: string; label: string }> = {
  INFO:       { icon: Info,          color: 'text-blue-600',    bg: 'bg-blue-50',    label: 'Information' },
  WARNING:    { icon: AlertTriangle, color: 'text-amber-600',   bg: 'bg-amber-50',   label: 'Avertissement' },
  DOCUMENT:   { icon: FileText,      color: 'text-purple-600',  bg: 'bg-purple-50',  label: 'Document' },
  APPROVAL:   { icon: CheckCircle2,  color: 'text-emerald-600', bg: 'bg-emerald-50', label: 'Approbation' },
  REJECTION:  { icon: XCircle,       color: 'text-red-600',     bg: 'bg-red-50',     label: 'Refus' },
}

// ── Send dialog ───────────────────────────────────────────────────────────────

const sendSchema = z.object({
  userId:  z.string().uuid('UUID invalide'),
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
  const { register, handleSubmit, reset, setValue, formState: { errors, isSubmitting } } = useForm<SendForm>({
    resolver: zodResolver(sendSchema),
    defaultValues: { type: 'INFO' },
  })

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
          <div className="space-y-2">
            <Label htmlFor="userId">ID du client (UUID)</Label>
            <Input
              id="userId"
              {...register('userId')}
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
              className="h-11 font-mono text-sm"
            />
            {errors.userId && <p className="text-xs text-destructive">{errors.userId.message}</p>}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
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

            <div className="space-y-2">
              <Label htmlFor="subject">Objet</Label>
              <Input id="subject" {...register('subject')} placeholder="Objet du message" className="h-11" />
              {errors.subject && <p className="text-xs text-destructive">{errors.subject.message}</p>}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="body">Message</Label>
            <Textarea id="body" {...register('body')} rows={4} placeholder="Contenu du message…" className="resize-none" />
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
                        <p className="mt-0.5 font-mono text-[10px] text-muted-foreground/60">{msg.userId}</p>
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
