import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { ShieldCheck, TrendingUp, Zap } from 'lucide-react'
import axios from 'axios'
import api from '@/lib/api'
import { setToken, setRefreshToken, getUserRoleFromToken } from '@/lib/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import Logo from '@/components/Logo'
import type { AuthResponse } from '@/types'

const schema = z.object({
  email:    z.string().email('Email invalide'),
  password: z.string().min(1, 'Mot de passe requis'),
})
type FormData = z.infer<typeof schema>

const features = [
  { icon: ShieldCheck, title: 'Sécurité bancaire',   desc: 'Chiffrement JWT + HTTPS bout en bout' },
  { icon: TrendingUp,  title: 'Virements en temps réel', desc: 'Saga Kafka — traitement asynchrone fiable' },
  { icon: Zap,         title: 'Accès instantané',    desc: 'Tableau de bord clair, actions rapides' },
]

export default function LoginPage() {
  const navigate = useNavigate()
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  async function onSubmit(data: FormData) {
    try {
      const res = await api.post<AuthResponse>('/api/v1/auth/login', data)
      setToken(res.data.accessToken)
      setRefreshToken(res.data.refreshToken)
      // Redirect admins to their panel, regular users to dashboard
      navigate(getUserRoleFromToken() === 'ADMIN' ? '/admin' : '/dashboard')
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status
        if (status === 401 || status === 400) {
          toast.error('Email ou mot de passe incorrect.')
        } else if (!err.response) {
          toast.error('Impossible de joindre le serveur.')
        } else {
          toast.error(`Erreur serveur (${status}).`)
        }
      } else {
        toast.error('Une erreur inattendue est survenue.')
      }
    }
  }

  return (
    <div className="flex min-h-screen">

      {/* ── Brand panel ─────────────────────────────────────────────────────── */}
      <div className="relative hidden w-2/5 flex-col justify-between overflow-hidden bg-primary p-10 lg:flex">
        {/* Subtle background decoration */}
        <div className="pointer-events-none absolute inset-0 overflow-hidden">
          <div className="absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/5" />
          <div className="absolute -bottom-32 -left-16 h-80 w-80 rounded-full bg-white/5" />
          <div className="absolute left-1/2 top-1/2 h-[500px] w-[500px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/5" />
        </div>

        <Logo size={38} className="relative z-10 [&_span]:text-white [&_span:last-child]:text-white/60" />

        <div className="relative z-10 space-y-8">
          <div>
            <h1 className="text-3xl font-semibold leading-tight text-white">
              Gérez votre argent<br />en toute confiance.
            </h1>
            <p className="mt-3 text-sm leading-relaxed text-white/60">
              Solaris Bank — infrastructure bancaire moderne, sécurisée, et pensée pour les développeurs.
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
          © {new Date().getFullYear()} Solaris Bank. Démo technique.
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
              {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <Label htmlFor="password" className="text-sm font-medium">Mot de passe</Label>
              </div>
              <Input
                id="password"
                type="password"
                className="h-11"
                {...register('password')}
              />
              {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
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
