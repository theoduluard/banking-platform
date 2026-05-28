import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account } from '@/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ArrowLeftRight, ArrowLeft, ArrowDown, Info } from 'lucide-react'

function formatAmount(amount: number, currency: string) {
  return new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount)
}

const schema = z.object({
  fromAccountId: z.string().uuid('Compte source requis'),
  toAccountId:   z.string().uuid('Compte destinataire requis'),
  amount:        z.number({ message: 'Montant invalide' }).positive('Montant invalide'),
}).refine(d => d.fromAccountId !== d.toAccountId, {
  message: 'Les comptes source et destinataire doivent être différents',
  path: ['toAccountId'],
})

type FormData = z.infer<typeof schema>

export default function TransferPage() {
  const navigate = useNavigate()
  const userId   = getUserIdFromToken()

  const { data: accounts = [] } = useQuery<Account[]>({
    queryKey: ['accounts', userId],
    queryFn:  () => api
      .get<Account[]>('/api/v1/accounts', { headers: { 'X-User-Id': userId } })
      .then(r => r.data.filter(a => a.status === 'ACTIVE')),
    enabled: !!userId,
  })

  const { control, register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const fromId = watch('fromAccountId')
  const fromAccount = accounts.find(a => a.id === fromId)

  async function onSubmit(data: FormData) {
    try {
      await api.post('/api/v1/transactions/transfer', data, {
        headers: { 'X-User-Id': userId },
      })
      toast.success('Virement initié ! Traitement en cours.')
      navigate('/dashboard')
    } catch {
      toast.error('Le virement a échoué. Vérifiez votre solde.')
    }
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <Button variant="ghost" size="sm" asChild className="-ml-2 gap-1.5 text-muted-foreground">
        <Link to="/dashboard"><ArrowLeft size={14} /> Retour</Link>
      </Button>

      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Effectuer un virement</h1>
        <p className="mt-1 text-sm text-muted-foreground">Les virements sont traités de manière asynchrone.</p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        {/* From */}
        <div className="space-y-2">
          <Label className="text-sm font-medium">Compte source</Label>
          <Controller
            name="fromAccountId"
            control={control}
            render={({ field }) => (
              <Select onValueChange={field.onChange} value={field.value}>
                <SelectTrigger className="h-11">
                  <SelectValue placeholder="Choisissez votre compte…" />
                </SelectTrigger>
                <SelectContent>
                  {accounts.map(a => (
                    <SelectItem key={a.id} value={a.id}>
                      <div className="flex items-center justify-between gap-6">
                        <span className="text-sm">{a.type === 'CHECKING' ? 'Courant' : 'Épargne'}</span>
                        <span className="font-mono text-xs text-muted-foreground">{a.iban.slice(-8)}</span>
                        <span className="font-semibold tabular-nums">{formatAmount(a.balance, a.currency)}</span>
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
          {fromAccount && (
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Info size={11} />
              Solde disponible : <strong>{formatAmount(fromAccount.balance, fromAccount.currency)}</strong>
            </p>
          )}
          {errors.fromAccountId && <p className="text-xs text-destructive">{errors.fromAccountId.message}</p>}
        </div>

        {/* Visual arrow */}
        <div className="flex items-center justify-center">
          <div className="flex size-9 items-center justify-center rounded-full border bg-muted">
            <ArrowDown size={16} className="text-muted-foreground" />
          </div>
        </div>

        {/* To */}
        <div className="space-y-2">
          <Label htmlFor="toAccountId" className="text-sm font-medium">Compte destinataire (UUID)</Label>
          <Input
            id="toAccountId"
            {...register('toAccountId')}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            className="h-11 font-mono text-sm"
          />
          {errors.toAccountId && <p className="text-xs text-destructive">{errors.toAccountId.message}</p>}
        </div>

        {/* Amount */}
        <div className="space-y-2">
          <Label htmlFor="amount" className="text-sm font-medium">Montant</Label>
          <div className="relative">
            <Input
              id="amount"
              {...register('amount', { valueAsNumber: true })}
              type="number"
              min="0.01"
              step="0.01"
              placeholder="0,00"
              className="h-11 pr-14 tabular-nums text-base font-semibold"
            />
            <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-sm font-medium text-muted-foreground">
              EUR
            </span>
          </div>
          {errors.amount && <p className="text-xs text-destructive">{errors.amount.message}</p>}
        </div>

        {/* Info notice */}
        <Card className="border-muted bg-muted/40">
          <CardContent className="flex items-start gap-2.5 py-3 px-4">
            <Info size={14} className="mt-0.5 shrink-0 text-muted-foreground" />
            <p className="text-xs leading-relaxed text-muted-foreground">
              Le virement sera traité via notre infrastructure Kafka. Le solde sera mis à jour sous quelques secondes.
            </p>
          </CardContent>
        </Card>

        <Button
          type="submit"
          className="h-11 w-full gap-2 text-sm font-medium"
          disabled={isSubmitting}
        >
          <ArrowLeftRight size={15} />
          {isSubmitting ? 'Envoi en cours…' : 'Confirmer le virement'}
        </Button>
      </form>
    </div>
  )
}
