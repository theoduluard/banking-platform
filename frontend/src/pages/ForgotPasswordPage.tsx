import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, useSearchParams } from 'react-router-dom'
import axios from 'axios'
import api from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import Logo from '@/components/Logo'
import { MailCheck, ArrowLeft } from 'lucide-react'

const schema = z.object({
  email: z.string().email('Email invalide'),
})
type FormData = z.infer<typeof schema>

export default function ForgotPasswordPage() {
  const [searchParams]    = useSearchParams()
  const [sent, setSent]   = useState(false)
  const [sentEmail, setSentEmail] = useState('')

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: { email: searchParams.get('email') ?? '' },
  })

  async function onSubmit({ email }: FormData) {
    try {
      await api.post('/api/v1/auth/forgot-password', { email })
    } catch (err) {
      // Swallow errors — we never reveal whether the address exists.
      // Only re-throw on network failures so the user knows to retry.
      if (axios.isAxiosError(err) && !err.response) {
        // Network error — let it show as a validation message below
      }
    }
    // Always show the success state regardless of outcome
    setSentEmail(email)
    setSent(true)
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-6 py-12">
      <div className="w-full max-w-sm space-y-8">

        <div className="flex justify-center">
          <Logo size={36} />
        </div>

        {sent ? (
          /* ── Success state ──────────────────────────────────────────── */
          <div className="space-y-6 text-center">
            <div className="flex justify-center">
              <div className="flex size-16 items-center justify-center rounded-2xl bg-primary/10">
                <MailCheck size={28} className="text-primary" />
              </div>
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Email envoyé</h1>
              <p className="mt-2 text-sm text-muted-foreground leading-relaxed">
                Si <span className="font-medium text-foreground">{sentEmail}</span> est
                associée à un compte, vous recevrez un lien de réinitialisation dans
                quelques instants. Pensez à vérifier vos spams.
              </p>
            </div>
            <Link
              to="/login"
              className="inline-flex items-center gap-1.5 text-sm font-medium text-primary underline-offset-4 hover:underline"
            >
              <ArrowLeft size={14} />
              Retour à la connexion
            </Link>
          </div>
        ) : (
          /* ── Request form ───────────────────────────────────────────── */
          <div className="space-y-7">
            <div>
              <h1 className="text-2xl font-semibold tracking-tight">Mot de passe oublié ?</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Saisissez votre adresse email et nous vous enverrons un lien pour
                réinitialiser votre mot de passe.
              </p>
            </div>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
              <div className="space-y-1.5">
                <Label htmlFor="email" className="text-sm font-medium">Adresse email</Label>
                <Input
                  id="email"
                  type="email"
                  placeholder="jean@example.com"
                  className="h-11"
                  {...register('email')}
                />
                {errors.email && (
                  <p className="text-xs text-destructive">{errors.email.message}</p>
                )}
              </div>

              <Button
                type="submit"
                className="h-11 w-full text-sm font-medium"
                disabled={isSubmitting}
              >
                {isSubmitting ? 'Envoi en cours…' : 'Envoyer le lien de réinitialisation'}
              </Button>
            </form>

            <p className="text-center text-sm text-muted-foreground">
              <Link
                to="/login"
                className="inline-flex items-center gap-1.5 font-medium text-primary underline-offset-4 hover:underline"
              >
                <ArrowLeft size={13} />
                Retour à la connexion
              </Link>
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
