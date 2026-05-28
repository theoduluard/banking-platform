interface LogoProps {
  size?: number
  className?: string
  variant?: 'full' | 'icon'
}

export default function Logo({ size = 36, className = '', variant = 'full' }: LogoProps) {
  const icon = (
    <svg
      width={size}
      height={size}
      viewBox="0 0 40 40"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={variant === 'icon' ? className : ''}
      aria-hidden="true"
    >
      {/* Rounded square background — primary navy */}
      <rect width="40" height="40" rx="10" fill="oklch(0.30 0.155 263)" />

      {/* Outer subtle orbit ring */}
      <circle cx="20" cy="20" r="13" stroke="white" strokeOpacity="0.15" strokeWidth="1" />

      {/* 8 rays — warm gold */}
      {[0, 45, 90, 135, 180, 225, 270, 315].map((deg) => {
        const r = Math.PI / 180
        const inner = 8.5
        const outer = 13.5
        return (
          <line
            key={deg}
            x1={20 + inner * Math.cos(deg * r)}
            y1={20 + inner * Math.sin(deg * r)}
            x2={20 + outer * Math.cos(deg * r)}
            y2={20 + outer * Math.sin(deg * r)}
            stroke="oklch(0.78 0.145 82)"
            strokeWidth={deg % 90 === 0 ? 2 : 1.2}
            strokeLinecap="round"
          />
        )
      })}

      {/* Center sun — gold disc */}
      <circle cx="20" cy="20" r="6" fill="oklch(0.78 0.145 82)" />
      {/* Inner white highlight */}
      <circle cx="20" cy="20" r="2.8" fill="white" fillOpacity="0.9" />
    </svg>
  )

  if (variant === 'icon') return icon

  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      {icon}
      <div className="flex flex-col leading-none">
        <span className="text-base font-semibold tracking-tight text-foreground">Solaris</span>
        <span className="text-[10px] font-medium uppercase tracking-[0.15em] text-muted-foreground">Bank</span>
      </div>
    </div>
  )
}
