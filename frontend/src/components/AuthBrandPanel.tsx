import { ShieldCheck, TrendingUp, Zap } from 'lucide-react'
import Logo from '@/components/Logo'

interface AuthBrandPanelProps {
  /** Main headline (falls back to a generic sentence if omitted). */
  title?: string
  /** Tagline / subtitle. */
  subtitle?: string
}

/** Brand gradient shared by all auth surfaces. */
export const BRAND_GRADIENT =
  'linear-gradient(145deg, #0c1624 0%, #1a3a8a 55%, #1e1b4b 100%)'

const features = [
  {
    icon: ShieldCheck,
    label: 'Sécurité bancaire',
    desc:  'Chiffrement de bout en bout, authentification forte',
  },
  {
    icon: TrendingUp,
    label: 'Virements rapides',
    desc:  'Transactions confirmées en quelques secondes',
  },
  {
    icon: Zap,
    label: 'Accès instantané',
    desc:  'Toutes vos finances en un seul endroit',
  },
]

/**
 * Gradient banner visible ONLY on mobile (hidden on lg+).
 * Drop it at the very top of the auth form panel so mobile
 * users get color + branding even without the left panel.
 */
export function AuthMobileHeader() {
  return (
    <div className="lg:hidden w-full" style={{ background: BRAND_GRADIENT }}>
      <div className="flex flex-col items-center gap-2 px-6 pb-8 pt-10">
        <Logo size={34} className="[&_span]:text-white [&_span:last-child]:text-white/55" />
        <p className="text-xs text-white/45 text-center">
          Votre banque en ligne, simple et sécurisée
        </p>
      </div>
    </div>
  )
}

/**
 * Left brand panel visible ONLY on desktop (lg+).
 * Uses a rich gradient with decorative blobs + a dot grid.
 */
export default function AuthBrandPanel({ title, subtitle }: AuthBrandPanelProps) {
  return (
    <div
      className="relative hidden w-5/12 flex-col justify-between overflow-hidden lg:flex"
      style={{
        backgroundImage:
          'url(https://images.unsplash.com/photo-1470770841072-f978cf4d019e?q=80&w=1000&auto=format&fit=crop)',
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }}
    >
      {/* Dark overlay for text readability */}
      <div className="pointer-events-none absolute inset-0 bg-black/58" />

      {/* Logo */}
      <div className="relative z-10 p-10">
        <Logo size={36} className="[&_span]:text-white [&_span:last-child]:text-white/55" />
      </div>

      {/* Headline + features */}
      <div className="relative z-10 space-y-8 px-10">
        <div>
          <h1 className="text-3xl font-bold leading-snug text-white">
            {title ?? 'Gérez votre argent\nen toute confiance.'}
          </h1>
          {subtitle && (
            <p className="mt-3 text-sm leading-relaxed text-white/55">{subtitle}</p>
          )}
        </div>

        <ul className="space-y-5">
          {features.map(({ icon: Icon, label, desc }) => (
            <li key={label} className="flex items-start gap-3">
              <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-white/10">
                <Icon size={16} className="text-amber-300" />
              </div>
              <div>
                <p className="text-sm font-medium text-white">{label}</p>
                <p className="text-xs leading-relaxed text-white/50">{desc}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>

      {/* Copyright */}
      <p className="relative z-10 px-10 pb-10 text-xs text-white/25">
        © {new Date().getFullYear()} Solaris Bank. Tous droits réservés.
      </p>
    </div>
  )
}
