import { moduleLabel, normalizeRank, RANK_LABELS } from '../../content/moduleMeta'

interface FieldHudProps {
  missionTitle: string
  module: number
  level: number
  rank: string
  hearts?: number
  maxHearts?: number
  pauseOpen: boolean
  onTogglePause: () => void
  onReturnToOps: () => void
}

export function FieldHud({
  missionTitle,
  module,
  level,
  rank,
  hearts = 3,
  maxHearts = 3,
  pauseOpen,
  onTogglePause,
  onReturnToOps,
}: FieldHudProps) {
  const rankLabel = RANK_LABELS[normalizeRank(rank)]
  const filled = Math.max(0, Math.min(maxHearts, hearts))

  return (
    <header className="field-hud" role="banner">
      <div className="field-hud__brand">
        <img
          className="field-hud__mark"
          src="/assets/brand/diamond-mark.svg"
          alt=""
          width={22}
          height={22}
          aria-hidden="true"
        />
        <div className="field-hud__titles">
          <span className="field-hud__product">API Spy</span>
          <span className="field-hud__mission" title={missionTitle}>
            {missionTitle}
          </span>
        </div>
      </div>

      <div className="field-hud__meta" aria-label="Mission location">
        <span>
          {moduleLabel(module)} · L{level}
        </span>
        <span className="field-hud__sep" aria-hidden="true">
          ·
        </span>
        <span>{rankLabel}</span>
      </div>

      <div className="field-hud__hearts" aria-label={`Integrity ${filled} of ${maxHearts}`}>
        {Array.from({ length: maxHearts }, (_, i) => (
          <span
            key={i}
            className={`field-hud__heart ${i < filled ? 'is-filled' : ''}`.trim()}
            aria-hidden="true"
          >
            ♥
          </span>
        ))}
      </div>

      <div className="field-hud__actions">
        <button
          type="button"
          className={`field-hud__icon-btn ${pauseOpen ? 'is-active' : ''}`.trim()}
          onClick={onTogglePause}
          aria-expanded={pauseOpen}
          aria-controls="field-pause-panel"
          title={pauseOpen ? 'Resume (Esc)' : 'Pause (Esc)'}
        >
          <span className="field-hud__gear" aria-hidden="true" />
          <span className="visually-hidden">{pauseOpen ? 'Close pause' : 'Pause'}</span>
        </button>
        <button type="button" className="field-hud__nav-btn" onClick={onReturnToOps}>
          Operations Board
        </button>
      </div>
    </header>
  )
}
