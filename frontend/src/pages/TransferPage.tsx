import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account } from '@/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ArrowLeftRight } from 'lucide-react'

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

  const { control, register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  async function onSubmit(data: FormData) {
    try {
      await api.post('/api/v1/transactions/transfer', data, {
        headers: { 'X-User-Id': userId },
      })
      toast.success('Virement initié ! En attente de traitement.')
      navigate('/dashboard')
    } catch {
      toast.error('Le virement a échoué. Vérifiez votre solde.')
    }
  }

  return (
    <div className="flex justify-center">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ArrowLeftRight size={20} />
            Effectuer un virement
          </CardTitle>
          <CardDescription>
            Les virements sont traités de manière asynchrone via Kafka.
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
            {/* From */}
            <div className="space-y-1">
              <Label>Compte source</Label>
              <Controller
                name="fromAccountId"
                control={control}
                render={({ field }) => (
                  <Select onValueChange={field.onChange} value={field.value}>
                    <SelectTrigger>
                      <SelectValue placeholder="Choisissez votre compte…" />
                    </SelectTrigger>
                    <SelectContent>
                      {accounts.map(a => (
                        <SelectItem key={a.id} value={a.id}>
                          <div className="flex items-center justify-between gap-4">
                            <span className="font-mono text-xs">{a.iban.slice(-8)}</span>
                            <span className="text-sm font-medium">
                              {formatAmount(a.balance, a.currency)}
                            </span>
                          </div>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
              {errors.fromAccountId && (
                <p className="text-xs text-destructive">{errors.fromAccountId.message}</p>
              )}
            </div>

            {/* To */}
            <div className="space-y-1">
              <Label>Compte destinataire (ID)</Label>
              <Input
                {...register('toAccountId')}
                placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                className="font-mono text-sm"
              />
              {errors.toAccountId && (
                <p className="text-xs text-destructive">{errors.toAccountId.message}</p>
              )}
            </div>

            {/* Amount */}
            <div className="space-y-1">
              <Label>Montant (EUR)</Label>
              <div className="relative">
                <Input
                  {...register('amount', { valueAsNumber: true })}
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="0.00"
                  className="pr-12 tabular-nums"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">
                  EUR
                </span>
              </div>
              {errors.amount && (
                <p className="text-xs text-destructive">{errors.amount.message}</p>
              )}
            </div>

            <Button type="submit" className="w-full gap-2" disabled={isSubmitting}>
              <ArrowLeftRight size={15} />
              {isSubmitting ? 'Envoi…' : 'Confirmer le virement'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
