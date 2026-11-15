interface ControlGuideProps {
  message: string
  tone?: 'brief' | 'success' | 'warn'
}

/** Authored CONTROL assistant — no generative AI. */
export function ControlGuide({ message, tone = 'brief' }: ControlGuideProps) {
  return (
    <aside className={`control-guide control-guide--${tone}`} aria-live="polite">
      <div className="control-guide__badge" aria-hidden="true">
        CTRL
      </div>
      <div className="control-guide__body">
        <p className="control-guide__name">CONTROL</p>
        <p className="control-guide__message">{message}</p>
      </div>
    </aside>
  )
}
