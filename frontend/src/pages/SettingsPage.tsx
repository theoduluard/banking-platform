import { useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import api from '@/lib/api'
import { removeToken } from '@/lib/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Lock, Mail, ShieldCheck, CheckCircle2, Eye, EyeOff, ArrowLeft } from 'lucide-react'

// ── OTP input (6 boxes) ───────────────────────────────────────────────────────
function OtpInput({ value, onChange }: { value: string[]; onChange: (v: string[]) => void }) {
  const refs = useRef<(HTMLInputElement | null)[]>(Array(6).fill(null))

  function handleKey(e: React.KeyboardEvent<HTMLInputElement>, i: number) {
    if (e.key === 'Backspace' && !value[i] && i > 0) {
      refs.current[i - 1]?.focus()
    }
  }

  function handleChange(e: React.ChangeEvent<HTMLInputElement>, i: number) {
    const char = e.target.value.replace(/\D/g, '').slice(-1)
    const next = [...value]
    next[i] = char
    onChange(next)
    if (char && i < 5) refs.current[i + 1]?.focus()
  }

  function handlePaste(e: React.ClipboardEvent) {
    e.preventDefault()
    const digits = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6)
    const next = [...value]
    digits.split('').forEach((d, i) => { next[i] = d })
    onChange(next)
    const focus = Math.min(digits.length, 5)
    refs.current[focus]?.focus()
  }

  return (
    <div className="flex gap-2 justify-center" onPaste={handlePaste}>
      {value.map((v, i) => (
        <input
          key={i}
          ref={el => { refs.current[i] = el }}
          type="text"
          inputMode="numeric"
          maxLength={1}
          value={v}
          onChange={e => handleChange(e, i)}
          onKeyDown={e => handleKey(e, i)}
          className="w-11 h-12 rounded-lg border border-border bg-background text-center text-lg font-mono font-semibold
                     focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-colors"
        />
      ))}
    </div>
  )
}

// ── Password change section ───────────────────────────────────────────────────
function PasswordSection() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [show, setShow] = useState({ current: false, newPwd: false, confirm: false })
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (form.newPassword !== form.confirmPassword) {
      toast.error('Les mots de passe ne correspondent pas.')
      return
    }
    if (form.newPassword.length < 8) {
      toast.error('Le nouveau mot de passe doit contenir au moins 8 caractères.')
      return
    }
    setLoading(true)
    try {
      await api.post('/api/v1/auth/change-password', {
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      })
      toast.success('Mot de passe modifié. Reconnectez-vous.')
      removeToken()
      navigate('/login', { replace: true })
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      toast.error(msg ?? 'Erreur lors du changement de mot de passe.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex size-9 items-center justify-center rounded-full bg-primary/10">
            <Lock size={16} className="text-primary" />
          </div>
          <div>
            <CardTitle className="text-base">Mot de passe</CardTitle>
            <CardDescription>Choisissez un mot de passe fort et unique.</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Current password */}
          <div className="space-y-1.5">
            <Label htmlFor="currentPwd">Mot de passe actuel</Label>
            <div className="relative">
              <Input
                id="currentPwd"
                type={show.current ? 'text' : 'password'}
                value={form.currentPassword}
                onChange={e => setForm(f => ({ ...f, currentPassword: e.target.value }))}
                required
                className="pr-10"
              />
              <button type="button" onClick={() => setShow(s => ({ ...s, current: !s.current }))}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground">
                {show.current ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>

          {/* New password */}
          <div className="space-y-1.5">
            <Label htmlFor="newPwd">Nouveau mot de passe</Label>
            <div className="relative">
              <Input
                id="newPwd"
                type={show.newPwd ? 'text' : 'password'}
                value={form.newPassword}
                onChange={e => setForm(f => ({ ...f, newPassword: e.target.value }))}
                required
                minLength={8}
                className="pr-10"
              />
              <button type="button" onClick={() => setShow(s => ({ ...s, newPwd: !s.newPwd }))}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground">
                {show.newPwd ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>

          {/* Confirm */}
          <div className="space-y-1.5">
            <Label htmlFor="confirmPwd">Confirmer le nouveau mot de passe</Label>
            <div className="relative">
              <Input
                id="confirmPwd"
                type={show.confirm ? 'text' : 'password'}
                value={form.confirmPassword}
                onChange={e => setForm(f => ({ ...f, confirmPassword: e.target.value }))}
                required
                className="pr-10"
              />
              <button type="button" onClick={() => setShow(s => ({ ...s, confirm: !s.confirm }))}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground">
                {show.confirm ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>

          <Button type="submit" disabled={loading} className="w-full sm:w-auto">
            {loading ? 'Modification…' : 'Changer le mot de passe'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

// ── Email change section (3-step wizard) ─────────────────────────────────────
type EmailStep = 'form' | 'otp' | 'done'

function EmailSection() {
  const [step, setStep] = useState<EmailStep>('form')
  const [form, setForm] = useState({ newEmail: '', currentPassword: '' })
  const [otp, setOtp]   = useState<string[]>(Array(6).fill(''))
  const [loading, setLoading] = useState(false)
  const [show, setShow] = useState(false)

  // ── Step 1: request ───────────────────────────────────────────────────────
  async function handleRequest(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    try {
      await api.post('/api/v1/auth/request-email-change', {
        newEmail: form.newEmail,
        currentPassword: form.currentPassword,
      })
      toast.success('Code envoyé à votre adresse email actuelle.')
      setStep('otp')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      toast.error(msg ?? 'Erreur lors de la demande.')
    } finally {
      setLoading(false)
    }
  }

  // ── Step 2: OTP confirmation ──────────────────────────────────────────────
  async function handleOtp(e: React.FormEvent) {
    e.preventDefault()
    const code = otp.join('')
    if (code.length < 6) { toast.error('Entrez les 6 chiffres du code.'); return }
    setLoading(true)
    try {
      await api.post('/api/v1/auth/confirm-email-change-otp', { code })
      toast.success('Code valide. Vérifiez votre nouvelle boîte mail.')
      setStep('done')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      toast.error(msg ?? 'Code invalide ou expiré.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex size-9 items-center justify-center rounded-full bg-primary/10">
            <Mail size={16} className="text-primary" />
          </div>
          <div>
            <CardTitle className="text-base">Adresse email</CardTitle>
            <CardDescription>
              Le changement est sécurisé par vérification en 3 étapes.
            </CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent>

        {/* ── Step indicator ── */}
        <div className="mb-6 flex items-center gap-2">
          {(['Demande', 'Confirmation', 'Vérification'] as const).map((label, idx) => {
            const stepIdx = step === 'form' ? 0 : step === 'otp' ? 1 : 2
            const done    = idx < stepIdx
            const active  = idx === stepIdx
            return (
              <div key={label} className="flex items-center gap-2">
                {idx > 0 && <div className="h-px w-6 bg-border" />}
                <div className="flex items-center gap-1.5">
                  <div className={`flex size-6 items-center justify-center rounded-full text-xs font-semibold
                    ${done   ? 'bg-primary text-primary-foreground' :
                      active ? 'bg-primary/15 text-primary border border-primary' :
                               'bg-muted text-muted-foreground'}`}>
                    {done ? '✓' : idx + 1}
                  </div>
                  <span className={`text-xs ${active ? 'text-foreground font-medium' : 'text-muted-foreground'}`}>
                    {label}
                  </span>
                </div>
              </div>
            )
          })}
        </div>

        {/* ── Form (step 1) ── */}
        {step === 'form' && (
          <form onSubmit={handleRequest} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="newEmail">Nouvelle adresse email</Label>
              <Input
                id="newEmail"
                type="email"
                value={form.newEmail}
                onChange={e => setForm(f => ({ ...f, newEmail: e.target.value }))}
                placeholder="nouvelle@exemple.com"
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="pwdForEmail">Mot de passe actuel</Label>
              <div className="relative">
                <Input
                  id="pwdForEmail"
                  type={show ? 'text' : 'password'}
                  value={form.currentPassword}
                  onChange={e => setForm(f => ({ ...f, currentPassword: e.target.value }))}
                  required
                  className="pr-10"
                />
                <button type="button" onClick={() => setShow(s => !s)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground">
                  {show ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>
            <p className="text-xs text-muted-foreground">
              Un code de vérification sera envoyé à votre adresse email actuelle.
            </p>
            <Button type="submit" disabled={loading} className="w-full sm:w-auto">
              {loading ? 'Envoi…' : 'Envoyer le code'}
            </Button>
          </form>
        )}

        {/* ── OTP (step 2) ── */}
        {step === 'otp' && (
          <form onSubmit={handleOtp} className="space-y-6">
            <div className="rounded-lg bg-muted/40 px-4 py-3 flex items-start gap-3">
              <ShieldCheck size={16} className="mt-0.5 shrink-0 text-primary" />
              <p className="text-sm text-muted-foreground">
                Un code à 6 chiffres a été envoyé à votre adresse actuelle.
                Entrez-le ci-dessous pour prouver que vous en avez le contrôle.
              </p>
            </div>
            <OtpInput value={otp} onChange={setOtp} />
            <div className="flex gap-3">
              <Button type="button" variant="outline" size="sm"
                      onClick={() => { setStep('form'); setOtp(Array(6).fill('')) }}>
                <ArrowLeft size={14} className="mr-1" /> Retour
              </Button>
              <Button type="submit" disabled={loading || otp.join('').length < 6}>
                {loading ? 'Vérification…' : 'Valider le code'}
              </Button>
            </div>
          </form>
        )}

        {/* ── Done (step 3) ── */}
        {step === 'done' && (
          <div className="space-y-4">
            <div className="rounded-lg border border-green-200 bg-green-50 p-4 flex items-start gap-3">
              <CheckCircle2 size={18} className="mt-0.5 shrink-0 text-green-600" />
              <div>
                <p className="text-sm font-medium text-green-800">
                  Un lien de confirmation a été envoyé à <strong>{form.newEmail}</strong>.
                </p>
                <p className="mt-1 text-sm text-green-700">
                  Cliquez sur ce lien depuis votre nouvelle boîte mail pour finaliser
                  le changement. Il expire dans <strong>1 heure</strong>.
                </p>
              </div>
            </div>
            <Button variant="outline" size="sm"
                    onClick={() => { setStep('form'); setForm({ newEmail: '', currentPassword: '' }); setOtp(Array(6).fill('')) }}>
              Recommencer avec une autre adresse
            </Button>
          </div>
        )}

      </CardContent>
    </Card>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────
export default function SettingsPage() {
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Paramètres du compte</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Gérez la sécurité de votre compte Solaris Bank.
        </p>
      </div>
      <Separator />
      <PasswordSection />
      <EmailSection />
    </div>
  )
}
