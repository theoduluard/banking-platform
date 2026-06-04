import Logo from '@/components/Logo'

interface AuthBrandPanelProps {
  /** Short headline displayed below the logo (optional). */
  title?: string
  /** Subtitle / tagline (optional). */
  subtitle?: string
}

/**
 * Left brand panel shared across all split-layout auth pages.
 * Uses the same landscape photo as the login page.
 */
export default function AuthBrandPanel({ title, subtitle }: AuthBrandPanelProps) {
  return (
    <div
      className="relative hidden w-2/5 flex-col justify-between overflow-hidden p-10 lg:flex"
      style={{
        backgroundImage:
          'url(https://images.unsplash.com/photo-1470770841072-f978cf4d019e?q=80&w=1000&auto=format&fit=crop)',
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }}
    >
      {/* Dark overlay */}
      <div className="pointer-events-none absolute inset-0 bg-black/55" />

      {/* Top spacer — keeps content vertically centered between logo area and copyright */}
      <div className="relative z-10">
        <Logo size={38} className="[&_span]:text-white [&_span:last-child]:text-white/60" />
      </div>

      {/* Centred content */}
      <div className="relative z-10 text-center">
        {title && (
          <h2 className="text-2xl font-semibold text-white leading-snug">{title}</h2>
        )}
        {subtitle && (
          <p className="mt-2 text-sm text-white/55 leading-relaxed">{subtitle}</p>
        )}
      </div>

      {/* Copyright — always flush with the bottom edge */}
      <p className="relative z-10 text-xs text-white/30">
        © {new Date().getFullYear()} Solaris Bank. Tous droits réservés.
      </p>
    </div>
  )
}
