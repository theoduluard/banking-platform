import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, useSearchParams } from 'react-router-dom'
import axios from 'axios'
import api from '@/lib/api'
import { Button, buttonVariants } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import Logo from '@/components/Logo'
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/

const schema = z.object({
  password: z.string()
    .min(8, '8 caractères minimum')
    .regex(PASSWORD_PATTERN, 'Doit contenir une majuscule, une minuscule, un chiffre et un caractère spécial (@$!%*?&)'),
  confirmPassword: z.string(),
}).refine((d) => d.password === d.confirmPassword, {
  message: 'Les mots de passe ne correspondent pas',
  path: ['confirmPassword'],
})
type FormData = z.infer<typeof schema>

type PageState = 'form' | 'success' | 'expired' | 'invalid'

export default function ResetPasswordPage() {
  const [searchParams]          = useSearchParams()
  const token                   = searchParams.get('token')
  const [pageState, setPageState] = useState<PageState>(token ? 'form' : 'invalid')

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  async function onSubmit({ password }: FormData) {
    try {
      await api.post('/api/v1/auth/reset-password', { token, password })
      setPageState('success')
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status
        setPageState(status === 410 ? 'expired' : 'invalid')
      } else {
        setPageState('invalid')
      }
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-6 py-12">
      <div className="w-full max-w-sm space-y-8">

        <div className="flex justify-center">
          <Logo size={36} />
        </div>

        {/* ── Success ─────────────────────────────────────────────────────── */}
        {pageState === 'success' && (
          <div className="space-y-6 text-center">
            <div className="flex justify-center">
              <div className="flex size-16 items-center justify-center rounded-2xl bg-emerald-100">
                <CheckCircle2 size={28} className="text-emerald-600" />
              </div>
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Mot de passe mis à jour</h1>
              <p className="mt-2 text-sm text-muted-foreground">
                Votre mot de passe a été réinitialisé avec succès.
                Vous pouvez maintenant vous connecter.
              </p>
            </div>
            <Link to="/login" className={cn(buttonVariants(), 'h-11 w-full')}>
              Se connecter
            </Link>
          </div>
        )}

        {/* ── Expired ─────────────────────────────────────────────────────── */}
        {pageState === 'expired' && (
          <div className="space-y-6 text-center">
            <div className="flex justify-center">
              <div className="flex size-16 items-center justify-center rounded-2xl bg-amber-100">
                <XCircle size={28} className="text-amber-600" />
              </div>
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Lien expiré</h1>
              <p className="mt-2 text-sm text-muted-foreground">
                Ce lien de réinitialisation a expiré (valide 1 heure).
                Faites une nouvelle demande depuis la page de connexion.
              </p>
            </div>
            <Link to="/forgot-password" className={cn(buttonVariants({ variant: 'outline' }), 'h-11 w-full')}>
              Nouvelle demande
            </Link>
          </div>
        )}

        {/* ── Invalid ─────────────────────────────────────────────────────── */}
        {pageState === 'invalid' && (
          <div className="space-y-6 text-center">
            <div className="flex justify-center">
              <div className="flex size-16 items-center justify-center rounded-2xl bg-red-100">
                <XCircle size={28} className="text-destructive" />
              </div>
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Lien invalide</h1>
              <p className="mt-2 text-sm text-muted-foreground">
                Ce lien est invalide ou a déjà été utilisé.
              </p>
            </div>
            <Link to="/forgot-password" className={cn(buttonVariants({ variant: 'outline' }), 'h-11 w-full')}>
              Nouvelle demande
            </Link>
          </div>
        )}

        {/* ── Form ────────────────────────────────────────────────────────── */}
        {pageState === 'form' && (
          <div className="space-y-7">
            <div>
              <h1 className="text-2xl font-semibold tracking-tight">Nouveau mot de passe</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Choisissez un nouveau mot de passe pour votre compte.
              </p>
            </div>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
              <div className="space-y-1.5">
                <Label htmlFor="password" className="text-sm font-medium">Nouveau mot de passe</Label>
                <Input
                  id="password"
                  type="password"
                  className="h-11"
                  {...register('password')}
                />
                {errors.password && (
                  <p className="text-xs text-destructive leading-relaxed">{errors.password.message}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="confirmPassword" className="text-sm font-medium">Confirmer le mot de passe</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  className="h-11"
                  {...register('confirmPassword')}
                />
                {errors.confirmPassword && (
                  <p className="text-xs text-destructive">{errors.confirmPassword.message}</p>
                )}
              </div>

              <Button
                type="submit"
                className="h-11 w-full text-sm font-medium"
                disabled={isSubmitting}
              >
                {isSubmitting
                  ? <><Loader2 size={14} className="animate-spin" /><span>Mise à jour…</span></>
                  : 'Réinitialiser le mot de passe'
                }
              </Button>
            </form>
          </div>
        )}

      </div>
    </div>
  )
}
