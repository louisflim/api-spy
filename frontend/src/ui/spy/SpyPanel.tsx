import type { ReactNode } from 'react'

interface SpyPanelProps {
  children: ReactNode
  variant?: 'console' | 'dossier' | 'crt' | 'command' | 'ops'
  className?: string
  delayMs?: number
  stamp?: string
}

/**
 * Interactive HUD panel shell over illustrated scenery.
 * Entrance animation is CSS-only on mount.
 */
export function SpyPanel({
  children,
  variant = 'console',
  className = '',
  delayMs = 120,
  stamp,
}: SpyPanelProps) {
  return (
    <section
      className={`spy-panel spy-panel--${variant} ${className}`.trim()}
      style={{ animationDelay: `${delayMs}ms` }}
    >
      <div className="spy-panel__corners" aria-hidden="true">
        <span />
        <span />
        <span />
        <span />
      </div>
      {stamp ? (
        <div className="spy-panel__stamp" aria-hidden="true">
          {stamp}
        </div>
      ) : null}
      <div className="spy-panel__body">{children}</div>
    </section>
  )
}
