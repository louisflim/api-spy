import type { ReactNode } from 'react'

interface TerminalScreenFrameProps {
  title?: string
  subtitle?: string
  statusLabel: string
  powered: boolean
  onClose: () => void
  children: ReactNode
}

/** Spy-tech monitor bezel around the API terminal contents. */
export function TerminalScreenFrame({
  title = 'API Terminal',
  subtitle = 'Secure Access Console',
  statusLabel,
  powered,
  onClose,
  children,
}: TerminalScreenFrameProps) {
  return (
    <div className="term-monitor" data-powered={powered ? 'on' : 'booting'}>
      <div className="term-monitor__chassis" aria-hidden="true">
        <div className="term-monitor__vent" />
        <div className="term-monitor__vent term-monitor__vent--right" />
      </div>

      <header className="term-monitor__chrome">
        <div className="term-monitor__identity">
          <span className={`term-monitor__power ${powered ? 'is-on' : ''}`} aria-hidden="true" />
          <div>
            <p className="term-monitor__eyebrow">{subtitle}</p>
            <h2 className="term-monitor__title">{title}</h2>
          </div>
        </div>
        <div className="term-monitor__chrome-actions">
          <span className="term-monitor__status" data-live={powered ? 'true' : 'false'}>
            {statusLabel}
          </span>
          <button type="button" className="term-monitor__close" onClick={onClose}>
            Close
          </button>
        </div>
      </header>

      <div className="term-monitor__bezel">
        <div className="term-monitor__glass">
          <div className="term-monitor__scanlines" aria-hidden="true" />
          <div className="term-monitor__glow" aria-hidden="true" />
          <div className="term-monitor__viewport">{children}</div>
        </div>
      </div>

      <footer className="term-monitor__footer" aria-hidden="true">
        <span>UPLINK // LOCAL SIM</span>
        <span>ENC-AES · NODE-7</span>
      </footer>
    </div>
  )
}
