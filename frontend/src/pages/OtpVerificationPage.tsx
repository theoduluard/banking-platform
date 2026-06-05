import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import axios from 'axios'
import api from '@/lib/api'
import { setToken, setRole, getUserRoleFromToken } from '@/lib/auth'
import { Button } from '@/components/ui/button'
import Logo from '@/components/Logo'
import { ShieldCheck, RotateCcw, AlertCircle, Mail } from 'lucide-react'
import type { AuthResponse } from '@/types'

const OTP_VALIDITY_SECONDS = 10 * 60   // must match backend OTP_VALIDITY_MINUTES

export default function OtpVerificationPage() {
  const navigate = useNavigate()

  const [otp,        setOtp]        = useState<string[]>(Array(6).fill(''))
  const [error,      setError]      = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [resending,  setResending]  = useState(false)
  const [remaining,  setRemaining]  = useState(OTP_VALIDITY_SECONDS)

  const inputRefs = useRef<(HTMLInputElement | null)[]>(Array(6).fill(null))

  const sessionToken = sessionStorage.getItem('otpSessionToken')

  // Redirect back to login if there's no session token
  useEffect(() => {
    if (!sessionToken) navigate('/login', { replace: true })
  }, [sessionToken, navigate])

  // Countdown timer
  useEffect(() => {
    if (remaining <= 0) return
    const id = setInterval(() => setRemaining(r => r - 1), 1000)
    return () => clearInterval(id)
  }, [remaining])

  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60).toString().padStart(2, '0')
    const s = (secs % 60).toString().padStart(2, '0')
    return `${m}:${s}`
  }

  // ── OTP input handlers ──────────────────────────────────────────────────────

  function handleChange(index: number, value: string) {
    if (!/^\d?$/.test(value)) return
    const next = [...otp]
    next[index] = value
    setOtp(next)
    setError(null)
    if (value && index < 5) inputRefs.current[index + 1]?.focus()
  }

  function handleKeyDown(index: number, e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Backspace' && !otp[index] && index > 0) {
      inputRefs.current[index - 1]?.focus()
    }
  }

  function handlePaste(e: React.ClipboardEvent) {
    const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6)
    if (text.length === 6) {
      setOtp(text.split(''))
      inputRefs.current[5]?.focus()
    }
    e.preventDefault()
  }

  // ── Submit ──────────────────────────────────────────────────────────────────

  const handleSubmit = useCallback(async () => {
    const code = otp.join('')
    if (code.length < 6) {
      setError('Entrez les 6 chiffres du code.')
      return
    }
    if (!sessionToken) return

    setSubmitting(true)
    setError(null)
    try {
      const res = await api.post<AuthResponse>('/api/v1/auth/verify-otp', { sessionToken, code })
      sessionStorage.removeItem('otpSessionToken')

      setToken(res.data.accessToken)
      setRole(res.data.role)

      if (getUserRoleFromToken() === 'ADMIN') {
        navigate('/admin')
        return
      }

      try {
        const kyc = await api.get<{ submitted: boolean }>('/api/v1/accounts/kyc/status')
        navigate(kyc.data.submitted ? '/dashboard' : '/onboarding/kyc')
      } catch {
        navigate('/dashboard')
      }
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status
        const msg    = err.response?.data?.error as string | undefined
        if (status === 410) {
          setError('Le code a expiré. Veuillez vous reconnecter.')
        } else if (status === 401) {
          if (msg?.includes('Too many')) {
            setError('Trop de tentatives. Veuillez vous reconnecter.')
            setTimeout(() => navigate('/login'), 2000)
          } else {
            setError(msg ?? 'Code incorrect.')
            setOtp(Array(6).fill(''))
            inputRefs.current[0]?.focus()
          }
        } else {
          setError('Une erreur est survenue. Réessayez.')
        }
      }
    } finally {
      setSubmitting(false)
    }
  }, [otp, sessionToken, navigate])

  // Auto-submit when all 6 digits are filled
  useEffect(() => {
    if (otp.every(d => d !== '') && !submitting) {
      handleSubmit()
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [otp])

  // ── Resend ──────────────────────────────────────────────────────────────────

  async function handleResend() {
    if (!sessionToken || resending) return
    setResending(true)
    try {
      await api.post('/api/v1/auth/resend-otp', { sessionToken })
      setRemaining(OTP_VALIDITY_SECONDS)
      setOtp(Array(6).fill(''))
      setError(null)
      inputRefs.current[0]?.focus()
      toast.success('Nouveau code envoyé !')
    } catch {
      toast.error('Impossible de renvoyer le code.')
    } finally {
      setResending(false)
    }
  }

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-6 py-12">
      <div className="mb-8">
        <Logo size={36} />
      </div>

      <div className="w-full max-w-sm space-y-8">

        {/* Header */}
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="flex size-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <ShieldCheck size={28} />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Vérification en deux étapes</h1>
            <p className="mt-1.5 text-sm text-muted-foreground">
              Un code à 6 chiffres a été envoyé à votre adresse email.
            </p>
          </div>
        </div>

        {/* Error banner */}
        {error && (
          <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3.5">
            <AlertCircle size={16} className="mt-0.5 shrink-0 text-red-600" />
            <p className="text-sm text-red-800">{error}</p>
          </div>
        )}

        {/* OTP input — 6 boxes */}
        <div className="flex justify-center gap-3" onPaste={handlePaste}>
          {otp.map((digit, i) => (
            <input
              key={i}
              ref={el => { inputRefs.current[i] = el }}
              type="text"
              inputMode="numeric"
              maxLength={1}
              value={digit}
              onChange={e => handleChange(i, e.target.value)}
              onKeyDown={e => handleKeyDown(i, e)}
              className="h-14 w-12 rounded-xl border border-input bg-background text-center text-xl font-semibold
                         shadow-sm transition-all
                         focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30
                         disabled:opacity-50"
              disabled={submitting}
              autoFocus={i === 0}
            />
          ))}
        </div>

        {/* Timer */}
        <div className="flex items-center justify-center gap-1.5 text-sm text-muted-foreground">
          {remaining > 0 ? (
            <>
              <span>Code valable encore</span>
              <span className={`tabular-nums font-medium ${remaining < 60 ? 'text-red-600' : 'text-foreground'}`}>
                {formatTime(remaining)}
              </span>
            </>
          ) : (
            <span className="text-red-600 font-medium">Code expiré</span>
          )}
        </div>

        {/* Submit button */}
        <Button
          className="h-11 w-full text-sm font-medium"
          onClick={handleSubmit}
          disabled={submitting || otp.join('').length < 6}
        >
          {submitting ? 'Vérification…' : 'Valider le code'}
        </Button>

        {/* Resend + back to login */}
        <div className="flex flex-col items-center gap-3">
          <button
            type="button"
            onClick={handleResend}
            disabled={resending}
            className="flex items-center gap-1.5 text-sm text-muted-foreground underline-offset-4 hover:text-primary hover:underline disabled:opacity-50"
          >
            <Mail size={14} />
            {resending ? 'Envoi en cours…' : 'Renvoyer un nouveau code'}
          </button>

          <button
            type="button"
            onClick={() => { sessionStorage.removeItem('otpSessionToken'); navigate('/login') }}
            className="flex items-center gap-1.5 text-xs text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
          >
            <RotateCcw size={12} />
            Recommencer la connexion
          </button>
        </div>

      </div>
    </div>
  )
}
