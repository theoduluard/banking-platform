import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Beneficiary } from '@/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Users, Plus, Trash2, ArrowLeftRight } from 'lucide-react'
import { Link } from 'react-router-dom'

// ── Validation ────────────────────────────────────────────────────────────────

const schema = z.object({
  name: z.string().min(1, 'Nom requis').max(100, 'Maximum 100 caractères'),
  iban: z
    .string()
    .min(1, 'IBAN requis')
    .regex(/^[A-Z]{2}[0-9]{2}[A-Z0-9]{4,30}$/, 'Format IBAN invalide (ex: FR7630006000011234567890189)'),
})
type FormData = z.infer<typeof schema>

// ── Helpers ───────────────────────────────────────────────────────────────────

function maskIban(iban: string) {
  return `${iban.slice(0, 4)} ···· ${iban.slice(-4)}`
}

// ── Components ────────────────────────────────────────────────────────────────

function BeneficiaryRow({
  b,
  onDelete,
}: {
  b: Beneficiary
  onDelete: (id: string) => void
}) {
  return (
    <div className="flex items-center gap-3 py-3.5">
      <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary/10">
        <Users size={15} className="text-primary" />
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{b.name}</p>
        <p className="font-mono text-xs text-muted-foreground">{maskIban(b.iban)}</p>
      </div>

      <div className="flex items-center gap-1.5">
        <Button variant="ghost" size="sm" asChild className="h-8 gap-1.5 px-2.5 text-xs text-muted-foreground hover:text-primary">
          <Link to={`/transfer?iban=${b.iban}&name=${encodeURIComponent(b.name)}`}>
            <ArrowLeftRight size={13} />
            <span>Virer</span>
          </Link>
        </Button>
        <Button
          variant="ghost"
          size="sm"
          className="h-8 w-8 p-0 text-muted-foreground hover:text-destructive"
          onClick={() => onDelete(b.id)}
        >
          <Trash2 size={14} />
        </Button>
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function BeneficiariesPage() {
  const userId      = getUserIdFromToken()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)

  // ── Data ───────────────────────────────────────────────────────────────────

  const { data: beneficiaries = [], isLoading } = useQuery<Beneficiary[]>({
    queryKey: ['beneficiaries', userId],
    queryFn:  () => api.get<Beneficiary[]>('/api/v1/beneficiaries').then(r => r.data),
    enabled:  !!userId,
  })

  // ── Add ────────────────────────────────────────────────────────────────────

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const addMutation = useMutation({
    mutationFn: (data: FormData) =>
      api.post<Beneficiary>('/api/v1/beneficiaries', data).then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['beneficiaries', userId] })
      toast.success('Bénéficiaire ajouté')
      setOpen(false)
      reset()
    },
    onError: (err: { response?: { data?: { error?: string } } }) => {
      const msg = err.response?.data?.error ?? 'Erreur lors de l\'ajout'
      toast.error(msg)
    },
  })

  // ── Delete ─────────────────────────────────────────────────────────────────

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/api/v1/beneficiaries/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['beneficiaries', userId] })
      toast.success('Bénéficiaire supprimé')
    },
    onError: () => toast.error('Erreur lors de la suppression'),
  })

  function onSubmit(data: FormData) {
    addMutation.mutate({ ...data, iban: data.iban.toUpperCase().replace(/\s/g, '') })
  }

  return (
    <div className="space-y-6">

      {/* ── Header ────────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Bénéficiaires</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Enregistrez les IBAN de vos contacts pour des virements rapides.
          </p>
        </div>
        <Button size="sm" className="gap-1.5" onClick={() => setOpen(true)}>
          <Plus size={15} /> Ajouter
        </Button>
      </div>

      {/* ── List ──────────────────────────────────────────────────────────── */}
      <Card>
        <CardHeader className="border-b pb-4">
          <CardTitle className="text-sm font-semibold">
            Mes bénéficiaires
            {beneficiaries.length > 0 && (
              <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-[11px] font-normal text-muted-foreground">
                {beneficiaries.length}
              </span>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">

          {isLoading && (
            <div className="space-y-3 p-4">
              {[1, 2, 3].map(i => <Skeleton key={i} className="h-14 w-full" />)}
            </div>
          )}

          {!isLoading && beneficiaries.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-14 text-center text-muted-foreground">
              <div className="flex size-14 items-center justify-center rounded-2xl bg-muted">
                <Users size={24} className="opacity-50" />
              </div>
              <div>
                <p className="text-sm font-medium">Aucun bénéficiaire</p>
                <p className="mt-0.5 text-xs">Ajoutez l'IBAN d'un contact pour virer rapidement.</p>
              </div>
              <Button size="sm" variant="outline" className="gap-1.5 mt-1" onClick={() => setOpen(true)}>
                <Plus size={14} /> Ajouter un bénéficiaire
              </Button>
            </div>
          )}

          {!isLoading && beneficiaries.length > 0 && (
            <div className="divide-y divide-border px-4">
              {beneficiaries.map(b => (
                <BeneficiaryRow
                  key={b.id}
                  b={b}
                  onDelete={id => deleteMutation.mutate(id)}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* ── Add dialog ────────────────────────────────────────────────────── */}
      <Dialog open={open} onOpenChange={o => { if (!isSubmitting) setOpen(o) }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Users size={18} className="text-primary" />
              Nouveau bénéficiaire
            </DialogTitle>
            <DialogDescription>
              Entrez un nom et l'IBAN du destinataire.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-1">
            <div className="space-y-2">
              <Label htmlFor="name">Nom</Label>
              <Input
                id="name"
                {...register('name')}
                placeholder="Papa, Marie, Loyer Paris…"
                className="h-11"
              />
              {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
            </div>

            <div className="space-y-2">
              <Label htmlFor="iban">IBAN</Label>
              <Input
                id="iban"
                {...register('iban')}
                placeholder="FR76 3000 6000 0112 3456 7890 189"
                className="h-11 font-mono text-sm uppercase"
              />
              {errors.iban && <p className="text-xs text-destructive">{errors.iban.message}</p>}
            </div>

            <DialogFooter className="gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => { setOpen(false); reset() }}
                disabled={isSubmitting}
                className="flex-1"
              >
                Annuler
              </Button>
              <Button type="submit" disabled={isSubmitting} className="flex-1 gap-2">
                <Plus size={14} />
                <span>{isSubmitting ? 'Ajout…' : 'Ajouter'}</span>
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
