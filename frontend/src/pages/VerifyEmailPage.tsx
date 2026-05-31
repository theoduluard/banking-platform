import { useEffect, useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import axios from 'axios'
import api from '@/lib/api'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import Logo from '@/components/Logo'
import { CheckCircle2, XCircle, Loader2, MailX } from 'lucide-react'

type Status = 'loading' | 'success' | 'expired' | 'invalid'

export default function VerifyEmailPage() {
  const [searchParams]    = useSearchParams()
  const [status, setStatus] = useState<Status>('loading')
  const token = searchParams.get('token')

  useEffect(() => {
    if (!token) { setStatus('invalid'); return }

    api.get(`/api/v1/auth/verify-email?token=${encodeURIComponent(token)}`)
      .then(() => setStatus('success'))
      .catch((err) => {
        if (axios.isAxiosError(err)) {
          const httpStatus = err.response?.status
          // 410 Gone = token expired, 404 = token unknown/already used
          setStatus(httpStatus === 410 ? 'expired' : 'invalid')
        } else {
          setStatus('invalid')
        }
      })
  // Only run once on mount — token from URL won't change
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-6">
      <div className="w-full max-w-sm space-y-8 text-center">

        <div className="flex justify-center">
          <Logo size={36} />
        </div>

        {status === 'loading' && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <div className="flex size-16 items-center justify-center rounded-2xl bg-muted">
                <Loader2 size={28} className="animate-spin text-muted-foreground" />
              </div>
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Vérification en cours…</h1>
              <p className="mt-1 text-sm text-muted-foreground">Validation de votre lien d'activation.</p>
            </div>
          </div>
        )}

        {status === 'success' && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <div className="flex size-16 items-center justify-center rounded-2xl bg-emerald-100">
                <CheckCircle2 size={28} className="text-emerald-600" />
              </div>
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Email vérifié !</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Votre compte est activé. Vous pouvez maintenant vous connecter.
              </p>
            </div>
            <Link to="/login" className={cn(buttonVariants(), 'h-11 w-full')}>
              Se connecter
            </Link>
          </div>
        )}

        {status === 'expired' && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <div className="flex size-16 items-center justify-center rounded-2xl bg-amber-100">
                <MailX size={28} className="text-amber-600" />
              </div>
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Lien expiré</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Ce lien de vérification a expiré (valide 24 h).
                Reconnectez-vous pour en recevoir un nouveau.
              </p>
            </div>
            <Link to="/login" className={cn(buttonVariants({ variant: 'outline' }), 'h-11 w-full')}>
              Retour à la connexion
            </Link>
          </div>
        )}

        {status === 'invalid' && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <div className="flex size-16 items-center justify-center rounded-2xl bg-red-100">
                <XCircle size={28} className="text-destructive" />
              </div>
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Lien invalide</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Ce lien est invalide ou a déjà été utilisé.
              </p>
            </div>
            <Link to="/login" className={cn(buttonVariants({ variant: 'outline' }), 'h-11 w-full')}>
              Retour à la connexion
            </Link>
          </div>
        )}

      </div>
    </div>
  )
}
