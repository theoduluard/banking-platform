import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import axios from 'axios'
import api from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import Logo from '@/components/Logo'

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/

const schema = z.object({
  firstname:       z.string().min(1, 'Prénom requis'),
  lastname:        z.string().min(1, 'Nom requis'),
  email:           z.string().email('Email invalide'),
  password:        z.string()
    .min(8, '8 caractères minimum')
    .regex(PASSWORD_PATTERN, 'Doit contenir une majuscule, une minuscule, un chiffre et un caractère spécial (@$!%*?&)'),
  confirmPassword: z.string(),
}).refine((d) => d.password === d.confirmPassword, {
  message: 'Les mots de passe ne correspondent pas',
  path: ['confirmPassword'],
})

type FormData = z.infer<typeof schema>

export default function RegisterPage() {
  const navigate = useNavigate()
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  async function onSubmit({ firstname, lastname, email, password }: FormData) {
    try {
      await api.post('/api/v1/auth/register', { firstname, lastname, email, password })
      toast.success('Compte créé ! Connectez-vous.')
      navigate('/login')
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status
        if (status === 409) {
          toast.error('Cet email est déjà utilisé.')
        } else if (status === 400) {
          toast.error('Données invalides. Vérifiez les champs.')
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
      <div className="relative hidden w-2/5 flex-col items-center justify-center overflow-hidden bg-primary p-10 lg:flex">
        <div className="pointer-events-none absolute inset-0 overflow-hidden">
          <div className="absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/5" />
          <div className="absolute -bottom-32 -left-16 h-80 w-80 rounded-full bg-white/5" />
        </div>
        <div className="relative z-10 text-center">
          <Logo size={56} variant="icon" className="mx-auto" />
          <h2 className="mt-6 text-2xl font-semibold text-white">Solaris Bank</h2>
          <p className="mt-2 text-sm text-white/55">
            Ouvrez votre compte en moins d'une minute.
          </p>
        </div>
      </div>

      {/* ── Form panel ──────────────────────────────────────────────────────── */}
      <div className="flex flex-1 flex-col items-center justify-center bg-background px-6 py-12">
        <div className="mb-8 lg:hidden">
          <Logo size={36} />
        </div>

        <div className="w-full max-w-sm space-y-7">
          <div>
            <h2 className="text-2xl font-semibold tracking-tight">Créer un compte</h2>
            <p className="mt-1 text-sm text-muted-foreground">Rejoignez Solaris Bank gratuitement</p>
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="firstname" className="text-sm font-medium">Prénom</Label>
                <Input id="firstname" placeholder="Jean" className="h-11" {...register('firstname')} />
                {errors.firstname && <p className="text-xs text-destructive">{errors.firstname.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="lastname" className="text-sm font-medium">Nom</Label>
                <Input id="lastname" placeholder="Dupont" className="h-11" {...register('lastname')} />
                {errors.lastname && <p className="text-xs text-destructive">{errors.lastname.message}</p>}
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="email" className="text-sm font-medium">Adresse email</Label>
              <Input id="email" type="email" placeholder="jean@example.com" className="h-11" {...register('email')} />
              {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="password" className="text-sm font-medium">Mot de passe</Label>
              <Input id="password" type="password" className="h-11" {...register('password')} />
              {errors.password && (
                <p className="text-xs text-destructive leading-relaxed">{errors.password.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="confirmPassword" className="text-sm font-medium">Confirmer le mot de passe</Label>
              <Input id="confirmPassword" type="password" className="h-11" {...register('confirmPassword')} />
              {errors.confirmPassword && (
                <p className="text-xs text-destructive">{errors.confirmPassword.message}</p>
              )}
            </div>

            <Button type="submit" className="h-11 w-full text-sm font-medium" disabled={isSubmitting}>
              {isSubmitting ? 'Création en cours…' : 'Créer mon compte'}
            </Button>
          </form>

          <p className="text-center text-sm text-muted-foreground">
            Déjà un compte ?{' '}
            <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">
              Se connecter
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
