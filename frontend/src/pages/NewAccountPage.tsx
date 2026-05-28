import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ArrowLeft, CreditCard, PiggyBank, CheckCircle2 } from 'lucide-react'
import { cn } from '@/lib/utils'

const schema = z.object({
  type: z.enum(['CHECKING', 'SAVINGS'], { message: 'Choisissez un type' }),
})
type FormData = z.infer<typeof schema>

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
    description: 'Pour mettre de l\'argent de côté et faire fructifier votre épargne.',
    icon: PiggyBank,
    color: 'accent',
  },
]

export default function NewAccountPage() {
  const navigate = useNavigate()
  const userId   = getUserIdFromToken()

  const { control, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const selectedType = watch('type')

  async function onSubmit({ type }: FormData) {
    try {
      await api.post<Account>('/api/v1/accounts', { type }, {
        headers: { 'X-User-Id': userId },
      })
      toast.success('Compte ouvert avec succès !')
      navigate('/dashboard')
    } catch {
      toast.error('Impossible de créer le compte.')
    }
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <Button variant="ghost" size="sm" asChild className="-ml-2 gap-1.5 text-muted-foreground">
        <Link to="/dashboard"><ArrowLeft size={14} /> Retour</Link>
      </Button>

      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Ouvrir un compte</h1>
        <p className="mt-1 text-sm text-muted-foreground">Choisissez le type de compte que vous souhaitez ouvrir.</p>
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
                    <p className="font-semibold text-sm">{label}</p>
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
          className="h-11 w-full text-sm font-medium"
          disabled={isSubmitting || !selectedType}
        >
          {isSubmitting ? 'Ouverture en cours…' : `Ouvrir ${selectedType === 'CHECKING' ? 'le compte courant' : selectedType === 'SAVINGS' ? 'le compte épargne' : 'le compte'}`}
        </Button>
      </form>
    </div>
  )
}
