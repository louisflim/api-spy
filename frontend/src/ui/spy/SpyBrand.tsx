interface SpyBrandProps {
  eyebrow: string
  title?: string
  subtitle: string
  delayMs?: number
  compact?: boolean
}

/** Mount-once logo block — stable across form re-renders. */
export function SpyBrand({
  eyebrow,
  title = 'API Spy',
  subtitle,
  delayMs = 0,
  compact = false,
}: SpyBrandProps) {
  return (
    <header
      className={`spy-brand ${compact ? 'spy-brand--compact' : ''}`.trim()}
      style={{ animationDelay: `${delayMs}ms` }}
    >
      <p className="spy-brand__eyebrow">{eyebrow}</p>
      <h1 className="spy-brand__title">
        <img
          className="spy-brand__diamond"
          src="/assets/brand/diamond-mark.svg"
          alt=""
          width={28}
          height={28}
          aria-hidden="true"
        />
        <span>{title}</span>
      </h1>
      <h2 className="spy-brand__subtitle">
        <span className="spy-brand__ticks" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
        {subtitle}
      </h2>
    </header>
  )
}
