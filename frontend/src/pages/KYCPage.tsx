import { useRef, useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import api from '@/lib/api'
import Logo from '@/components/Logo'
import { Button } from '@/components/ui/button'
import {
  Camera, IdCard, Upload, Loader2, ShieldCheck, CheckCircle2, ArrowRight,
} from 'lucide-react'
import { cn } from '@/lib/utils'

// ── File helpers ──────────────────────────────────────────────────────────────

interface FileData {
  base64: string
  contentType: string
  previewUrl: string
}

function readFileAsBase64(file: File): Promise<FileData> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const dataUrl = reader.result as string
      const [prefix, base64] = dataUrl.split(',')
      const contentType = prefix.replace('data:', '').replace(';base64', '')
      resolve({ base64, contentType, previewUrl: dataUrl })
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

// ── Upload zone ───────────────────────────────────────────────────────────────

function UploadZone({
  label,
  hint,
  icon: Icon,
  capture,
  file,
  onChange,
}: {
  label: string
  hint: string
  icon: typeof Camera
  capture?: 'user' | 'environment'
  file: FileData | null
  onChange: (f: FileData) => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)

  async function handleFile(raw: File | undefined) {
    if (!raw) return
    if (!raw.type.startsWith('image/')) {
      toast.error('Veuillez sélectionner une image (JPG, PNG…)')
      return
    }
    if (raw.size > 10 * 1024 * 1024) {
      toast.error('Image trop volumineuse (max 10 Mo)')
      return
    }
    try {
      onChange(await readFileAsBase64(raw))
    } catch {
      toast.error('Impossible de lire le fichier')
    }
  }

  return (
    <div
      onClick={() => inputRef.current?.click()}
      className={cn(
        'relative cursor-pointer overflow-hidden rounded-2xl border-2 border-dashed p-6 text-center transition-all duration-150',
        file
          ? 'border-primary/50 bg-primary/5'
          : 'border-border hover:border-primary/40 hover:bg-muted/30',
      )}
    >
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        capture={capture}
        className="sr-only"
        onChange={e => handleFile(e.target.files?.[0])}
      />
      {file ? (
        <div className="space-y-3">
          <div className="relative mx-auto w-fit">
            <img
              src={file.previewUrl}
              alt={label}
              className="mx-auto h-32 w-32 rounded-xl object-cover shadow"
            />
            <div className="absolute -right-1.5 -top-1.5 flex size-6 items-center justify-center rounded-full bg-primary shadow">
              <CheckCircle2 size={13} className="text-primary-foreground" />
            </div>
          </div>
          <div>
            <p className="text-sm font-semibold text-primary">{label} ajouté</p>
            <p className="text-xs text-muted-foreground">Cliquez pour changer</p>
          </div>
        </div>
      ) : (
        <div className="space-y-4 py-2">
          <div className="mx-auto flex size-16 items-center justify-center rounded-2xl bg-muted">
            <Icon size={26} className="text-muted-foreground" />
          </div>
          <div>
            <p className="text-sm font-semibold">{label}</p>
            <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{hint}</p>
          </div>
          <span className="inline-flex items-center gap-1.5 text-xs font-medium text-primary">
            <Upload size={12} />
            Ajouter une photo
          </span>
        </div>
      )}
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function KYCPage() {
  const navigate    = useNavigate()
  const [selfie,    setSelfie]    = useState<FileData | null>(null)
  const [idCard,    setIdCard]    = useState<FileData | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // If user already has KYC, skip straight to dashboard
  const { data: kycStatus, isLoading: checkingKyc } = useQuery<{ submitted: boolean }>({
    queryKey: ['kyc-status'],
    queryFn:  () => api.get<{ submitted: boolean }>('/api/v1/accounts/kyc/status').then(r => r.data),
    retry: false,
  })

  useEffect(() => {
    if (kycStatus?.submitted) {
      navigate('/dashboard', { replace: true })
    }
  }, [kycStatus, navigate])

  async function handleSubmit() {
    if (!selfie || !idCard) return
    setSubmitting(true)
    try {
      await api.post('/api/v1/accounts/kyc', {
        selfieBase64:      selfie.base64,
        selfieContentType: selfie.contentType,
        idCardBase64:      idCard.base64,
        idCardContentType: idCard.contentType,
      })
      toast.success('Vérification d\'identité envoyée !')
      navigate('/dashboard', { replace: true })
    } catch {
      toast.error('Impossible d\'envoyer vos documents. Veuillez réessayer.')
    } finally {
      setSubmitting(false)
    }
  }

  if (checkingKyc) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 size={24} className="animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-6 py-12">
      <div className="w-full max-w-md space-y-8">

        {/* Logo */}
        <div className="flex justify-center">
          <Logo size={34} />
        </div>

        {/* Header */}
        <div className="space-y-2 text-center">
          <div className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-primary/10">
            <ShieldCheck size={26} className="text-primary" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">Vérification d'identité</h1>
          <p className="text-sm leading-relaxed text-muted-foreground">
            Avant d'ouvrir votre premier compte, nous devons vérifier votre identité.
            Cela ne prend que quelques secondes.
          </p>
        </div>

        {/* Step indicator */}
        <div className="flex items-center gap-3 rounded-xl border bg-muted/30 px-4 py-3">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-emerald-100">
            <CheckCircle2 size={14} className="text-emerald-600" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-medium">Email vérifié</p>
            <p className="text-[10px] text-muted-foreground">Étape 1 terminée</p>
          </div>
          <div className="h-px w-6 bg-border" />
          <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-[11px] font-bold text-primary-foreground">
            2
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-medium">Identité</p>
            <p className="text-[10px] text-muted-foreground">En cours</p>
          </div>
        </div>

        {/* Upload zones */}
        <div className="grid gap-4 sm:grid-cols-2">
          <UploadZone
            label="Photo de vous"
            hint="Prenez un selfie avec votre visage bien visible"
            icon={Camera}
            capture="user"
            file={selfie}
            onChange={setSelfie}
          />
          <UploadZone
            label="Carte d'identité"
            hint="Photographiez le recto de votre pièce d'identité"
            icon={IdCard}
            capture="environment"
            file={idCard}
            onChange={setIdCard}
          />
        </div>

        {/* Security notice */}
        <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
          <ShieldCheck size={15} className="mt-0.5 shrink-0 text-amber-600" />
          <p className="text-xs leading-relaxed text-amber-800">
            <strong>Vos données sont sécurisées.</strong> Ces documents sont chiffrés et
            accessibles uniquement à nos administrateurs pour vérification.
          </p>
        </div>

        {/* Submit */}
        <Button
          className="h-12 w-full gap-2 text-sm font-medium"
          disabled={!selfie || !idCard || submitting}
          onClick={handleSubmit}
        >
          {submitting ? (
            <><Loader2 size={15} className="animate-spin" /><span>Envoi en cours…</span></>
          ) : (
            <><span>Valider et accéder à mon espace</span><ArrowRight size={15} /></>
          )}
        </Button>

        <p className="text-center text-xs text-muted-foreground">
          Vous pourrez ouvrir votre premier compte dès que vos documents auront été examinés.
        </p>

      </div>
    </div>
  )
}
