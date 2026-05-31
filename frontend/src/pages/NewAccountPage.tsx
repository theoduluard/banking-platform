import { useRef, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getUserIdFromToken } from '@/lib/auth'
import api from '@/lib/api'
import type { Account, AccountType } from '@/types'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ArrowLeft, ArrowRight, CreditCard, PiggyBank, CheckCircle2, Camera, IdCard, Upload, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

// ── Account type step ─────────────────────────────────────────────────────────

const typeSchema = z.object({
  type: z.enum(['CHECKING', 'SAVINGS'], { message: 'Choisissez un type' }),
})
type TypeForm = z.infer<typeof typeSchema>

const accountTypes = [
  {
    value: 'CHECKING' as const,
    label: 'Compte courant',
    description: 'Pour vos dépenses quotidiennes, paiements et virements.',
    icon: CreditCard,
    color: 'primary',
  },
  {
    value: 'SAVINGS' as const,
    label: 'Compte épargne',
    description: "Pour mettre de l'argent de côté et faire fructifier votre épargne.",
    icon: PiggyBank,
    color: 'accent',
  },
]

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

// ── Document upload step ──────────────────────────────────────────────────────

function DocumentUploadStep({
  onBack,
  onConfirm,
  isSubmitting,
}: {
  onBack: () => void
  onConfirm: (selfie: FileData, idCard: FileData) => void
  isSubmitting: boolean
}) {
  const [selfie, setSelfie]   = useState<FileData | null>(null)
  const [idCard, setIdCard]   = useState<FileData | null>(null)
  const selfieRef             = useRef<HTMLInputElement>(null)
  const idCardRef             = useRef<HTMLInputElement>(null)

  async function handleFile(file: File | undefined, setter: (d: FileData) => void) {
    if (!file) return
    if (!file.type.startsWith('image/')) {
      toast.error('Veuillez sélectionner une image (JPG, PNG…)')
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      toast.error('Image trop volumineuse (max 10 Mo)')
      return
    }
    try {
      setter(await readFileAsBase64(file))
    } catch {
      toast.error("Impossible de lire le fichier")
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold tracking-tight">Vérification d'identité</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Pour l'ouverture de votre premier compte, nous devons vérifier votre identité.
          Ces documents seront examinés par notre équipe.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        {/* Selfie */}
        <div
          className={cn(
            'relative cursor-pointer overflow-hidden rounded-xl border-2 border-dashed p-5 text-center transition-colors',
            selfie ? 'border-primary/50 bg-primary/5' : 'border-border hover:border-primary/40 hover:bg-muted/30',
          )}
          onClick={() => selfieRef.current?.click()}
        >
          <input
            ref={selfieRef}
            type="file"
            accept="image/*"
            capture="user"
            className="sr-only"
            onChange={e => handleFile(e.target.files?.[0], setSelfie)}
          />
          {selfie ? (
            <div className="space-y-2">
              <img
                src={selfie.previewUrl}
                alt="Selfie"
                className="mx-auto h-28 w-28 rounded-xl object-cover shadow-sm"
              />
              <p className="text-xs font-medium text-primary">Selfie ajouté ✓</p>
              <p className="text-[10px] text-muted-foreground">Cliquez pour changer</p>
            </div>
          ) : (
            <div className="space-y-3 py-4">
              <div className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-muted">
                <Camera size={24} className="text-muted-foreground" />
              </div>
              <div>
                <p className="text-sm font-semibold">Photo de vous</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Prenez un selfie avec votre visage bien visible
                </p>
              </div>
              <div className="inline-flex items-center gap-1.5 text-xs text-primary">
                <Upload size={12} />
                <span>Ajouter une photo</span>
              </div>
            </div>
          )}
        </div>

        {/* ID card */}
        <div
          className={cn(
            'relative cursor-pointer overflow-hidden rounded-xl border-2 border-dashed p-5 text-center transition-colors',
            idCard ? 'border-primary/50 bg-primary/5' : 'border-border hover:border-primary/40 hover:bg-muted/30',
          )}
          onClick={() => idCardRef.current?.click()}
        >
          <input
            ref={idCardRef}
            type="file"
            accept="image/*"
            capture="environment"
            className="sr-only"
            onChange={e => handleFile(e.target.files?.[0], setIdCard)}
          />
          {idCard ? (
            <div className="space-y-2">
              <img
                src={idCard.previewUrl}
                alt="Carte d'identité"
                className="mx-auto h-28 w-28 rounded-xl object-cover shadow-sm"
              />
              <p className="text-xs font-medium text-primary">Carte d'identité ajoutée ✓</p>
              <p className="text-[10px] text-muted-foreground">Cliquez pour changer</p>
            </div>
          ) : (
            <div className="space-y-3 py-4">
              <div className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-muted">
                <IdCard size={24} className="text-muted-foreground" />
              </div>
              <div>
                <p className="text-sm font-semibold">Carte d'identité</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Photographiez le recto de votre pièce d'identité
                </p>
              </div>
              <div className="inline-flex items-center gap-1.5 text-xs text-primary">
                <Upload size={12} />
                <span>Ajouter une photo</span>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
        <p className="text-xs leading-relaxed text-amber-800">
          <strong>Vos données sont sécurisées.</strong> Ces documents ne sont accessibles qu'aux
          administrateurs de la plateforme et seront supprimés après vérification.
        </p>
      </div>

      <div className="flex gap-3">
        <Button
          type="button"
          variant="outline"
          className="flex-1 gap-1.5"
          onClick={onBack}
          disabled={isSubmitting}
        >
          <ArrowLeft size={14} />
          <span>Retour</span>
        </Button>
        <Button
          type="button"
          className="flex-1 gap-1.5"
          disabled={!selfie || !idCard || isSubmitting}
          onClick={() => selfie && idCard && onConfirm(selfie, idCard)}
        >
          {isSubmitting
            ? <><Loader2 size={14} className="animate-spin" /><span>Envoi…</span></>
            : <><span>Soumettre</span><ArrowRight size={14} /></>
          }
        </Button>
      </div>
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function NewAccountPage() {
  const navigate  = useNavigate()
  const userId    = getUserIdFromToken()
  const [step, setStep]           = useState<'type' | 'documents'>('type')
  const [submitting, setSubmitting] = useState(false)

  // Fetch existing accounts to decide if this is the user's first
  const { data: existingAccounts = [] } = useQuery<Account[]>({
    queryKey: ['accounts', userId],
    queryFn:  () => api.get<Account[]>('/api/v1/accounts', { headers: { 'X-User-Id': userId } }).then(r => r.data),
    enabled: !!userId,
  })

  const isFirstAccount = existingAccounts.length === 0

  const { control, handleSubmit, watch, formState: { errors } } = useForm<TypeForm>({
    resolver: zodResolver(typeSchema),
  })

  const selectedType = watch('type')

  async function onTypeSubmit({ type }: TypeForm) {
    if (isFirstAccount) {
      // Need KYC documents — go to document upload step
      setStep('documents')
    } else {
      // Not the first account — just create directly
      await createAccount(type, null, null)
    }
  }

  async function onDocumentsConfirm(selfie: FileData, idCard: FileData) {
    await createAccount(selectedType as AccountType, selfie, idCard)
  }

  async function createAccount(
    type: AccountType,
    selfie: FileData | null,
    idCard: FileData | null,
  ) {
    setSubmitting(true)
    try {
      const { data: account } = await api.post<Account>('/api/v1/accounts', { type })

      if (selfie && idCard) {
        await api.post(`/api/v1/accounts/${account.id}/documents`, {
          selfieBase64:       selfie.base64,
          selfieContentType:  selfie.contentType,
          idCardBase64:       idCard.base64,
          idCardContentType:  idCard.contentType,
        })
      }

      toast.success(
        isFirstAccount
          ? 'Demande envoyée ! Votre compte sera activé après vérification.'
          : 'Compte créé, en attente de validation.',
      )
      navigate('/dashboard')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
        ?? 'Impossible de créer le compte.'
      toast.error(msg)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <Link
        to="/dashboard"
        className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }), '-ml-2 gap-1.5 text-muted-foreground')}
      >
        <ArrowLeft size={14} />
        <span>Retour</span>
      </Link>

      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Ouvrir un compte</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {step === 'type'
            ? 'Choisissez le type de compte que vous souhaitez ouvrir.'
            : 'Étape 2 sur 2 — Vérification d\'identité'}
        </p>
      </div>

      {/* Step indicator */}
      {isFirstAccount && (
        <div className="flex items-center gap-2">
          <div className={cn(
            'flex size-6 items-center justify-center rounded-full text-[10px] font-bold',
            step === 'type' ? 'bg-primary text-primary-foreground' : 'bg-emerald-500 text-white',
          )}>
            {step === 'type' ? '1' : <CheckCircle2 size={12} />}
          </div>
          <div className="h-px flex-1 bg-border" />
          <div className={cn(
            'flex size-6 items-center justify-center rounded-full text-[10px] font-bold',
            step === 'documents' ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground',
          )}>
            2
          </div>
        </div>
      )}

      {/* Step 1: Type selection */}
      {step === 'type' && (
        <form onSubmit={handleSubmit(onTypeSubmit)} className="space-y-5">
          <Controller
            name="type"
            control={control}
            render={({ field }) => (
              <div className="grid gap-3 sm:grid-cols-2">
                {accountTypes.map(({ value, label, description, icon: Icon, color }) => {
                  const selected = field.value === value
                  return (
                    <button
                      key={value}
                      type="button"
                      onClick={() => field.onChange(value)}
                      className={cn(
                        'relative rounded-xl border-2 p-5 text-left transition-all duration-150',
                        selected
                          ? color === 'primary'
                            ? 'border-primary bg-primary/5 shadow-sm'
                            : 'border-[oklch(0.78_0.145_82)] bg-[oklch(0.78_0.145_82)]/8 shadow-sm'
                          : 'border-border bg-card hover:border-primary/40 hover:bg-muted/30',
                      )}
                    >
                      {selected && (
                        <CheckCircle2
                          size={16}
                          className={`absolute right-3 top-3 ${
                            color === 'primary' ? 'text-primary' : 'text-[oklch(0.55_0.14_82)]'
                          }`}
                        />
                      )}
                      <div className={`mb-3 inline-flex size-10 items-center justify-center rounded-lg ${
                        color === 'primary' ? 'bg-primary/10 text-primary' : 'bg-[oklch(0.78_0.145_82)]/20 text-[oklch(0.50_0.14_82)]'
                      }`}>
                        <Icon size={18} />
                      </div>
                      <p className="text-sm font-semibold">{label}</p>
                      <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{description}</p>
                    </button>
                  )
                })}
              </div>
            )}
          />
          {errors.type && <p className="text-xs text-destructive">{errors.type.message}</p>}

          <Button
            type="submit"
            className="h-11 w-full gap-1.5 text-sm font-medium"
            disabled={submitting || !selectedType}
          >
            {isFirstAccount ? (
              <><span>Suivant</span><ArrowRight size={14} /></>
            ) : submitting ? (
              <><Loader2 size={14} className="animate-spin" /><span>Ouverture…</span></>
            ) : (
              <span>
                {selectedType === 'CHECKING'
                  ? 'Ouvrir le compte courant'
                  : selectedType === 'SAVINGS'
                  ? 'Ouvrir le compte épargne'
                  : 'Ouvrir le compte'}
              </span>
            )}
          </Button>
        </form>
      )}

      {/* Step 2: Document upload (first account only) */}
      {step === 'documents' && (
        <DocumentUploadStep
          onBack={() => setStep('type')}
          onConfirm={onDocumentsConfirm}
          isSubmitting={submitting}
        />
      )}

      {/* Info card for pending approval */}
      {!isFirstAccount && (
        <Card className="border-amber-200 bg-amber-50">
          <CardContent className="px-4 py-3">
            <p className="text-xs leading-relaxed text-amber-800">
              Les nouveaux comptes sont activés après validation par notre équipe (généralement sous 24 h).
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
