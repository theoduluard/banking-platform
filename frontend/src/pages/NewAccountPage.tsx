import { useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import api from '@/lib/api'
import { getUserIdFromToken } from '@/lib/auth'
import type { Account } from '@/types'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ArrowLeft, CreditCard, PiggyBank, CheckCircle2, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

const typeSchema = z.object({
  type: z.enum(['CHECKING', 'SAVINGS'], { message: 'Choisissez un type' }),
})
type TypeForm = z.infer<typeof typeSchema>

const accountTypes = [
  {
    value: 'CHECKING' as const,
    label: 'Compte courant',
    description: 'Pour vos dépenses quotidiennes, paiements et virements.',
    icon: CreditCard,
    color: 'primary',
  },
  {
    value: 'SAVINGS' as const,
    label: 'Compte épargne',
    description: "Pour mettre de l'argent de côté et faire fructifier votre épargne.",
    icon: PiggyBank,
    color: 'accent',
  },
]

export default function NewAccountPage() {
  const navigate     = useNavigate()
  const queryClient  = useQueryClient()
  const [submitting, setSubmitting] = useState(false)

  const { control, handleSubmit, watch, formState: { errors } } = useForm<TypeForm>({
    resolver: zodResolver(typeSchema),
  })

  const selectedType = watch('type')

  async function onSubmit({ type }: TypeForm) {
    setSubmitting(true)
    try {
      await api.post<Account>('/api/v1/accounts', { type })
      await queryClient.invalidateQueries({ queryKey: ['accounts', getUserIdFromToken()] })
      toast.success('Compte ouvert, en attente de validation.')
      navigate('/dashboard')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
        ?? 'Impossible de créer le compte.'
      toast.error(msg)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <Link
        to="/dashboard"
        className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }), '-ml-2 gap-1.5 text-muted-foreground')}
      >
        <ArrowLeft size={14} />
        <span>Retour</span>
      </Link>

      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Ouvrir un compte</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Choisissez le type de compte que vous souhaitez ouvrir.
        </p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        <Controller
          name="type"
          control={control}
          render={({ field }) => (
            <div className="grid gap-3 sm:grid-cols-2">
              {accountTypes.map(({ value, label, description, icon: Icon, color }) => {
                const selected = field.value === value
                return (
                  <button
                    key={value}
                    type="button"
                    onClick={() => field.onChange(value)}
                    className={cn(
                      'relative rounded-xl border-2 p-5 text-left transition-all duration-150',
                      selected
                        ? color === 'primary'
                          ? 'border-primary bg-primary/5 shadow-sm'
                          : 'border-[oklch(0.78_0.145_82)] bg-[oklch(0.78_0.145_82)]/8 shadow-sm'
                        : 'border-border bg-card hover:border-primary/40 hover:bg-muted/30',
                    )}
                  >
                    {selected && (
                      <CheckCircle2
                        size={16}
                        className={`absolute right-3 top-3 ${
                          color === 'primary' ? 'text-primary' : 'text-[oklch(0.55_0.14_82)]'
                        }`}
                      />
                    )}
                    <div className={`mb-3 inline-flex size-10 items-center justify-center rounded-lg ${
                      color === 'primary' ? 'bg-primary/10 text-primary' : 'bg-[oklch(0.78_0.145_82)]/20 text-[oklch(0.50_0.14_82)]'
                    }`}>
                      <Icon size={18} />
                    </div>
                    <p className="text-sm font-semibold">{label}</p>
                    <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{description}</p>
                  </button>
                )
              })}
            </div>
          )}
        />
        {errors.type && <p className="text-xs text-destructive">{errors.type.message}</p>}

        <Button
          type="submit"
          className="h-11 w-full gap-1.5 text-sm font-medium"
          disabled={submitting || !selectedType}
        >
          {submitting ? (
            <><Loader2 size={14} className="animate-spin" /><span>Ouverture…</span></>
          ) : (
            <span>
              {selectedType === 'CHECKING'
                ? 'Ouvrir le compte courant'
                : selectedType === 'SAVINGS'
                ? 'Ouvrir le compte épargne'
                : 'Ouvrir le compte'}
            </span>
          )}
        </Button>
      </form>

      <Card className="border-amber-200 bg-amber-50">
        <CardContent className="px-4 py-3">
          <p className="text-xs leading-relaxed text-amber-800">
            Les nouveaux comptes sont activés après validation par notre équipe (généralement sous 24 h).
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
