import { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import axios from 'axios'
import { removeToken } from '@/lib/auth'
import Logo from '@/components/Logo'
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'

type Status = 'loading' | 'success' | 'error'

export default function VerifyNewEmailPage() {
  const [searchParams] = useSearchParams()
  const navigate       = useNavigate()
  const token          = searchParams.get('token')

  const [status,  setStatus]  = useState<Status>('loading')
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!token) {
      setStatus('error')
      setMessage('Lien invalide — aucun token trouvé.')
      return
    }

    axios
      .get(`${import.meta.env.VITE_API_URL}/api/v1/auth/verify-new-email`, {
        params: { token },
      })
      .then(() => {
        setStatus('success')
        // All sessions have been revoked server-side — clear local state too
        removeToken()
      })
      .catch((err: unknown) => {
        const msg =
          (err as { response?: { data?: { error?: string } } })?.response?.data?.error
        setStatus('error')
        setMessage(msg ?? 'Lien invalide ou expiré.')
      })
  }, [token])

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-background px-4">
      <div className="w-full max-w-md rounded-2xl border border-border bg-card p-8 shadow-sm text-center space-y-6">
        <div className="flex justify-center">
          <Logo size={40} />
        </div>

        {status === 'loading' && (
          <>
            <Loader2 size={40} className="mx-auto animate-spin text-primary" />
            <p className="text-sm text-muted-foreground">Vérification en cours…</p>
          </>
        )}

        {status === 'success' && (
          <>
            <div className="flex size-16 items-center justify-center rounded-full bg-green-100 mx-auto">
              <CheckCircle2 size={32} className="text-green-600" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-foreground">Adresse email mise à jour !</h1>
              <p className="mt-2 text-sm text-muted-foreground">
                Votre nouvelle adresse email est maintenant active. Connectez-vous
                avec votre nouvelle adresse pour accéder à votre compte.
              </p>
            </div>
            <Button className="w-full" onClick={() => navigate('/login', { replace: true })}>
              Se connecter
            </Button>
          </>
        )}

        {status === 'error' && (
          <>
            <div className="flex size-16 items-center justify-center rounded-full bg-destructive/10 mx-auto">
              <XCircle size={32} className="text-destructive" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-foreground">Lien invalide</h1>
              <p className="mt-2 text-sm text-muted-foreground">{message}</p>
            </div>
            <div className="flex flex-col gap-2">
              <Button className="w-full" onClick={() => navigate('/settings', { replace: true })}>
                Retour aux paramètres
              </Button>
              <Button variant="outline" className="w-full" onClick={() => navigate('/login', { replace: true })}>
                Se connecter
              </Button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
