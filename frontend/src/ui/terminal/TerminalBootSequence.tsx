import { useEffect, useState } from 'react'

interface TerminalBootSequenceProps {
  onComplete: () => void
  /** Total boot duration in ms (default ~1.15s). */
  durationMs?: number
}

const BOOT_LINES = [
  'Power rail check… OK',
  'Loading firmware image…',
  'Initializing terminal…',
  'Negotiating secure uplink…',
  'Secure uplink established.',
]

/**
 * Short CRT-style power-on sequence. Respects prefers-reduced-motion.
 */
export function TerminalBootSequence({ onComplete, durationMs = 1150 }: TerminalBootSequenceProps) {
  const [lineIndex, setLineIndex] = useState(0)
  const [phase, setPhase] = useState<'off' | 'flash' | 'reveal' | 'ready'>('off')

  useEffect(() => {
    const reduced =
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches

    if (reduced) {
      setPhase('ready')
      setLineIndex(BOOT_LINES.length - 1)
      const t = window.setTimeout(onComplete, 80)
      return () => window.clearTimeout(t)
    }

    const timers: number[] = []
    timers.push(window.setTimeout(() => setPhase('flash'), 90))
    timers.push(window.setTimeout(() => setPhase('reveal'), 280))
    timers.push(window.setTimeout(() => setPhase('ready'), Math.max(durationMs - 180, 700)))
    timers.push(window.setTimeout(onComplete, durationMs))

    const lineStep = Math.floor((durationMs - 320) / BOOT_LINES.length)
    BOOT_LINES.forEach((_, index) => {
      timers.push(
        window.setTimeout(() => setLineIndex(index), 300 + index * Math.max(lineStep, 120)),
      )
    })

    return () => {
      for (const id of timers) window.clearTimeout(id)
    }
  }, [durationMs, onComplete])

  return (
    <div
      className={`term-boot term-boot--${phase}`}
      role="status"
      aria-live="polite"
      aria-label="Terminal boot sequence"
    >
      <div className="term-boot__flash" aria-hidden="true" />
      <div className="term-boot__reveal" aria-hidden="true" />
      <div className="term-boot__static" aria-hidden="true" />
      <div className="term-boot__copy">
        <p className="term-boot__brand">API SPY · FIELD TERMINAL</p>
        <ul className="term-boot__lines">
          {BOOT_LINES.slice(0, lineIndex + 1).map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
        {phase === 'ready' ? <p className="term-boot__ready">Terminal ready</p> : null}
      </div>
    </div>
  )
}
