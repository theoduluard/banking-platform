import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { ShieldCheck, TrendingUp, Zap, MailWarning, AlertCircle } from 'lucide-react'
import axios from 'axios'
import api from '@/lib/api'
import { setToken, setRefreshToken, getUserRoleFromToken } from '@/lib/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import Logo from '@/components/Logo'
import type { AuthResponse } from '@/types'
import { cn } from '@/lib/utils'

const schema = z.object({
  email:    z.string().email('Email invalide'),
  password: z.string().min(1, 'Mot de passe requis'),
})
type FormData = z.infer<typeof schema>

const features = [
  { icon: ShieldCheck, title: 'Sécurité bancaire',       desc: 'Chiffrement de bout en bout, authentification forte' },
  { icon: TrendingUp,  title: 'Virements en temps réel', desc: 'Transactions rapides, confirmées en quelques secondes' },
  { icon: Zap,         title: 'Accès instantané',        desc: 'Toutes vos finances en un coup d\'œil' },
]

export default function LoginPage() {
  const navigate  = useNavigate()
  const [unverifiedEmail, setUnverifiedEmail]   = useState<string | null>(null)
  const [loginError,      setLoginError]        = useState<string | null>(null)
  const [resending,       setResending]         = useState(false)
  const [forgotHint,      setForgotHint]        = useState(false)

  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  const emailValue = watch('email', '')

  async function onSubmit(data: FormData) {
    setUnverifiedEmail(null)
    setLoginError(null)
    try {
      const res = await api.post<AuthResponse>('/api/v1/auth/login', data)
      setToken(res.data.accessToken)
      setRefreshToken(res.data.refreshToken)

      if (getUserRoleFromToken() === 'ADMIN') {
        navigate('/admin')
        return
      }

      // For clients: check KYC status to decide where to redirect
      try {
        const kyc = await api.get<{ submitted: boolean }>('/api/v1/accounts/kyc/status')
        navigate(kyc.data.submitted ? '/dashboard' : '/onboarding/kyc')
      } catch {
        // If the KYC check fails for any reason, fall back to dashboard
        navigate('/dashboard')
      }
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status
        const msg    = err.response?.data?.error as string | undefined
        if (status === 403 && msg?.toLowerCase().includes('not verified')) {
          // Show inline banner instead of a toast — lets user resend from this page
          setUnverifiedEmail(data.email)
        } else if (status === 401 || status === 400) {
          setLoginError('Email ou mot de passe incorrect.')
        } else if (!err.response) {
          setLoginError('Impossible de joindre le serveur. Vérifiez votre connexion.')
        } else {
          setLoginError(`Une erreur est survenue (${status}). Réessayez dans quelques instants.`)
        }
      } else {
        setLoginError('Une erreur inattendue est survenue.')
      }
    }
  }

  async function resendVerification() {
    if (!unverifiedEmail) return
    setResending(true)
    try {
      await api.post('/api/v1/auth/resend-verification', { email: unverifiedEmail })
      toast.success('Email de vérification renvoyé !')
    } catch {
      toast.error('Impossible de renvoyer l\'email.')
    } finally {
      setResending(false)
    }
  }

  return (
    <div className="flex min-h-screen">

      {/* ── Brand panel ─────────────────────────────────────────────────────── */}
      <div
        className="relative hidden w-2/5 flex-col justify-between overflow-hidden p-10 lg:flex"
        style={{
          backgroundImage: 'url(https://images.unsplash.com/photo-1470770841072-f978cf4d019e?q=80&w=1000&auto=format&fit=crop)',
          backgroundSize: 'cover',
          backgroundPosition: 'center',
        }}
      >
        {/* Dark overlay for text readability */}
        <div className="pointer-events-none absolute inset-0 bg-black/55" />

        <Logo size={38} className="relative z-10 [&_span]:text-white [&_span:last-child]:text-white/60" />

        <div className="relative z-10 space-y-8">
          <div>
            <h1 className="text-3xl font-semibold leading-tight text-white">
              Gérez votre argent<br />en toute confiance.
            </h1>
            <p className="mt-3 text-sm leading-relaxed text-white/60">
              Solaris Bank — votre banque en ligne, simple, moderne et sécurisée.
            </p>
          </div>

          <ul className="space-y-5">
            {features.map(({ icon: Icon, title, desc }) => (
              <li key={title} className="flex items-start gap-3">
                <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-white/10">
                  <Icon size={16} className="text-[oklch(0.78_0.145_82)]" />
                </div>
                <div>
                  <p className="text-sm font-medium text-white">{title}</p>
                  <p className="text-xs text-white/55">{desc}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <p className="relative z-10 text-xs text-white/30">
          © {new Date().getFullYear()} Solaris Bank. Tous droits réservés.
        </p>
      </div>

      {/* ── Form panel ──────────────────────────────────────────────────────── */}
      <div className="flex flex-1 flex-col items-center justify-center bg-background px-6 py-12">
        {/* Mobile logo */}
        <div className="mb-8 lg:hidden">
          <Logo size={36} />
        </div>

        <div className="w-full max-w-sm space-y-8">
          <div>
            <h2 className="text-2xl font-semibold tracking-tight">Bienvenue</h2>
            <p className="mt-1 text-sm text-muted-foreground">Connectez-vous à votre espace</p>
          </div>

          {/* ── Invalid credentials banner ──────────────────────────────── */}
          {loginError && (
            <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3.5">
              <AlertCircle size={16} className="mt-0.5 shrink-0 text-red-600" />
              <p className="text-sm text-red-800">{loginError}</p>
            </div>
          )}

          {/* ── Unverified email banner ─────────────────────────────────── */}
          {unverifiedEmail && (
            <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3.5">
              <MailWarning size={16} className="mt-0.5 shrink-0 text-amber-600" />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-amber-800">Email non vérifié</p>
                <p className="mt-0.5 text-xs text-amber-700">
                  Vérifiez votre boîte mail ou{' '}
                  <button
                    type="button"
                    onClick={resendVerification}
                    disabled={resending}
                    className="font-semibold underline underline-offset-2 hover:no-underline disabled:opacity-50"
                  >
                    {resending ? 'Envoi…' : 'renvoyez le lien d\'activation'}
                  </button>
                  .
                </p>
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
            <div className="space-y-1.5">
              <Label htmlFor="email" className="text-sm font-medium">Adresse email</Label>
              <Input
                id="email"
                type="email"
                placeholder="jean@example.com"
                className="h-11"
                {...register('email', { onChange: () => setLoginError(null) })}
              />
              {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <Label htmlFor="password" className="text-sm font-medium">Mot de passe</Label>
                <button
                  type="button"
                  onClick={() => {
                    if (!emailValue?.trim()) {
                      setForgotHint(true)
                      return
                    }
                    setForgotHint(false)
                    navigate(`/forgot-password?email=${encodeURIComponent(emailValue.trim())}`)
                  }}
                  className="text-xs text-muted-foreground underline-offset-4 hover:text-primary hover:underline"
                >
                  Mot de passe oublié ?
                </button>
              </div>
              <Input
                id="password"
                type="password"
                className="h-11"
                {...register('password', { onChange: () => setLoginError(null) })}
              />
              {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
              {forgotHint && !emailValue?.trim() && (
                <p className={cn('text-xs text-amber-600')}>
                  Saisissez votre adresse email ci-dessus pour réinitialiser votre mot de passe.
                </p>
              )}
            </div>

            <Button
              type="submit"
              className="h-11 w-full text-sm font-medium"
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Connexion en cours…' : 'Se connecter'}
            </Button>
          </form>

          <p className="text-center text-sm text-muted-foreground">
            Pas encore de compte ?{' '}
            <Link to="/register" className="font-medium text-primary underline-offset-4 hover:underline">
              Créer un compte
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
