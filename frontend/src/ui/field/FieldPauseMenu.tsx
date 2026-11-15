import { useEffect, useState } from 'react'

interface FieldPauseMenuProps {
  open: boolean
  onResume: () => void
  /** Confirm then restart the current room (server PLAYER reset). */
  onResetLevel: () => void
  onCombatRestart: () => void
  /** Combat restart is server-gated until combat deaths authorize it. */
  combatRestartAvailable?: boolean
  onReturnToOps: () => void
  /** Disable confirm while a reset request is in flight. */
  resetBusy?: boolean
}

export function FieldPauseMenu({
  open,
  onResume,
  onResetLevel,
  onCombatRestart,
  combatRestartAvailable = false,
  onReturnToOps,
  resetBusy = false,
}: FieldPauseMenuProps) {
  const [confirmReset, setConfirmReset] = useState(false)

  useEffect(() => {
    if (!open) setConfirmReset(false)
  }, [open])

  useEffect(() => {
    if (!open || !confirmReset) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      event.stopPropagation()
      setConfirmReset(false)
    }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [open, confirmReset])

  if (!open) return null

  return (
    <div className="field-pause" id="field-pause-panel" role="dialog" aria-modal="true" aria-label="Pause">
      <div className="field-pause__panel">
        {confirmReset ? (
          <>
            <h2 className="field-pause__title">Confirm reset</h2>
            <p className="field-pause__blurb">
              Restart this room from the entrance? Device unlocks, exit status, and combat state for
              this run will clear. Career progress, escalation tier, and research records stay as they
              are.
            </p>
            <div className="field-pause__actions">
              <button
                type="button"
                className="field-pause__primary"
                disabled={resetBusy}
                onClick={onResetLevel}
              >
                {resetBusy ? 'Resetting…' : 'Confirm Reset'}
              </button>
              <button
                type="button"
                className="ghost"
                disabled={resetBusy}
                onClick={() => setConfirmReset(false)}
              >
                Cancel
              </button>
            </div>
          </>
        ) : (
          <>
            <h2 className="field-pause__title">Operation paused</h2>
            <p className="field-pause__blurb">
              Press Esc to resume. Reset level restarts this room without changing your API difficulty
              tier.
            </p>
            <div className="field-pause__actions">
              <button type="button" className="field-pause__primary" onClick={onResume}>
                Resume
              </button>
              <button type="button" className="ghost" onClick={() => setConfirmReset(true)}>
                Reset level
              </button>
              {combatRestartAvailable ? (
                <button type="button" className="ghost" onClick={onCombatRestart}>
                  Combat restart
                </button>
              ) : null}
              <button type="button" className="ghost" onClick={onReturnToOps}>
                Return to Operations Board
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
