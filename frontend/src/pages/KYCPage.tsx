import { useRef, useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import axios from 'axios'
import api from '@/lib/api'
import Logo from '@/components/Logo'
import { Button } from '@/components/ui/button'
import {
  Camera, IdCard, Upload, Loader2, ShieldCheck, CheckCircle2, ArrowRight, FileText,
} from 'lucide-react'
import { cn } from '@/lib/utils'

// ── Accepted file types ───────────────────────────────────────────────────────

const ACCEPTED_MIME = new Set([
  'image/jpeg',
  'image/jpg',
  'image/png',
  'image/webp',
  'image/heic',
  'image/heif',
  'image/gif',
  'image/bmp',
  'image/tiff',
  'application/pdf',
])

// Fallback check by extension when the browser doesn't report a MIME type (e.g. HEIC on some devices)
const ACCEPTED_EXT = /\.(jpe?g|png|webp|heic|heif|gif|bmp|tiff?|pdf)$/i

function isAccepted(file: File): boolean {
  if (file.type && ACCEPTED_MIME.has(file.type)) return true
  return ACCEPTED_EXT.test(file.name)
}

// ── File helpers ──────────────────────────────────────────────────────────────

interface FileData {
  base64:      string
  contentType: string
  filename:    string
  isPdf:       boolean
  previewUrl:  string   // data-URL for images, empty for PDF
}

function readFileAsBase64(file: File): Promise<FileData> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const dataUrl = reader.result as string
      const [prefix, base64] = dataUrl.split(',')
      // Normalise: some browsers report "image/jpg" instead of "image/jpeg"
      let contentType = prefix.replace('data:', '').replace(';base64', '')
      if (contentType === 'image/jpg') contentType = 'image/jpeg'
      // Fallback for HEIC files where browser may report empty type
      if (!contentType || contentType === 'application/octet-stream') {
        if (/\.heic$/i.test(file.name)) contentType = 'image/heic'
        else if (/\.heif$/i.test(file.name)) contentType = 'image/heif'
        else if (/\.pdf$/i.test(file.name)) contentType = 'application/pdf'
      }
      const isPdf = contentType === 'application/pdf'
      resolve({
        base64,
        contentType,
        filename:   file.name,
        isPdf,
        previewUrl: isPdf ? '' : dataUrl,
      })
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

// ── Upload zone ───────────────────────────────────────────────────────────────

const ACCEPT_ATTR = 'image/jpeg,image/png,image/webp,image/heic,image/heif,image/gif,image/bmp,image/tiff,application/pdf'

function UploadZone({
  label,
  hint,
  icon: Icon,
  file,
  onChange,
}: {
  label:    string
  hint:     string
  icon:     typeof Camera
  file:     FileData | null
  onChange: (f: FileData) => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)

  async function handleFile(raw: File | undefined) {
    if (!raw) return
    if (!isAccepted(raw)) {
      toast.error('Format non supporté. Utilisez JPG, PNG, PDF, HEIC, WebP…')
      return
    }
    if (raw.size > 20 * 1024 * 1024) {
      toast.error('Fichier trop volumineux (max 20 Mo)')
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
        accept={ACCEPT_ATTR}
        className="sr-only"
        onChange={e => handleFile(e.target.files?.[0])}
      />

      {file ? (
        <div className="space-y-3">
          <div className="relative mx-auto w-fit">
            {file.isPdf ? (
              <div className="mx-auto flex h-32 w-32 flex-col items-center justify-center gap-2 rounded-xl border bg-muted">
                <FileText size={36} className="text-muted-foreground" />
                <p className="max-w-[100px] truncate px-1 text-[10px] text-muted-foreground">
                  {file.filename}
                </p>
              </div>
            ) : (
              <img
                src={file.previewUrl}
                alt={label}
                className="mx-auto h-32 w-32 rounded-xl object-cover shadow"
              />
            )}
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
            Ajouter un fichier
          </span>
          <p className="text-[10px] text-muted-foreground">JPG · PNG · PDF · HEIC · WebP</p>
        </div>
      )}
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function KYCPage() {
  const navigate     = useNavigate()
  const [selfie,     setSelfie]     = useState<FileData | null>(null)
  const [idCard,     setIdCard]     = useState<FileData | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // If user already has KYC, skip straight to dashboard
  const { data: kycStatus, isLoading: checkingKyc } = useQuery<{ submitted: boolean }>({
    queryKey: ['kyc-status'],
    queryFn:  () => api.get<{ submitted: boolean }>('/api/v1/accounts/kyc/status').then(r => r.data),
    retry: false,
  })

  useEffect(() => {
    if (kycStatus?.submitted) navigate('/dashboard', { replace: true })
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
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status
        const msg    = err.response?.data?.error as string | undefined
        if (status === 413) {
          toast.error('Fichier trop volumineux. Compressez-le et réessayez.')
        } else if (status === 400 && msg) {
          toast.error(msg)
        } else if (status === 401 || status === 403) {
          toast.error('Session expirée. Reconnectez-vous.')
        } else if (!err.response) {
          toast.error('Serveur inaccessible. Vérifiez votre connexion.')
        } else {
          toast.error(`Erreur ${status ?? 'inconnue'}. Veuillez réessayer.`)
        }
      } else {
        toast.error('Impossible d\'envoyer vos documents.')
      }
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
            hint="Selfie ou photo récente avec votre visage visible"
            icon={Camera}
            file={selfie}
            onChange={setSelfie}
          />
          <UploadZone
            label="Pièce d'identité"
            hint="CNI, passeport ou titre de séjour (recto)"
            icon={IdCard}
            file={idCard}
            onChange={setIdCard}
          />
        </div>

        {/* Security notice */}
        <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
          <ShieldCheck size={15} className="mt-0.5 shrink-0 text-amber-600" />
          <p className="text-xs leading-relaxed text-amber-800">
            <strong>Vos données sont sécurisées.</strong> Ces documents sont accessibles
            uniquement à nos administrateurs pour vérification.
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
