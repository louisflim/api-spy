import { useEffect, useState } from 'react'

interface FieldPauseMenuProps {
  open: boolean
  onResume: () => void
  /** Confirm then restart the current room (server classifies PLAYER / API abandonment). */
  onResetLevel: () => void
  onCombatRestart: () => void
  /** Combat restart is server-gated until combat deaths authorize it. */
  combatRestartAvailable?: boolean
  onReturnToOps: () => void
  /** Disable confirm while a reset request is in flight. */
  resetBusy?: boolean
  /** Current mandatory API device count (excludes final lock in the displayed number). */
  currentDeviceCount?: number
  /** Device count after an escalating restart. */
  nextDeviceCount?: number
  /** Server-authoritative: restart will increase required API devices. */
  wouldEscalate?: boolean
  finalLockRequired?: boolean
}

export function FieldPauseMenu({
  open,
  onResume,
  onResetLevel,
  onCombatRestart,
  combatRestartAvailable = false,
  onReturnToOps,
  resetBusy = false,
  currentDeviceCount = 1,
  nextDeviceCount = 1,
  wouldEscalate = false,
  finalLockRequired = false,
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

  const escalateBlurb = wouldEscalate
    ? `This restart will increase required API devices from ${currentDeviceCount} to ${nextDeviceCount}${
        nextDeviceCount > 1 || finalLockRequired ? ' (plus final lock on retry tiers)' : ''
      }. Career progress and research records stay as they are.`
    : `This restart will keep required API devices at ${currentDeviceCount}${
        finalLockRequired ? ' plus the final lock' : ''
      }. No API work is unresolved in this room, so the difficulty tier will not increase.`

  return (
    <div className="field-pause" id="field-pause-panel" role="dialog" aria-modal="true" aria-label="Pause">
      <div className="field-pause__panel">
        {confirmReset ? (
          <>
            <h2 className="field-pause__title">Confirm restart</h2>
            <p className="field-pause__blurb">
              Restart this room from the entrance? Device unlocks, exit status, and combat state for
              this run will clear.
            </p>
            <p className="field-pause__blurb" data-escalate={wouldEscalate ? 'true' : 'false'}>
              {escalateBlurb}
            </p>
            <div className="field-pause__actions">
              <button
                type="button"
                className="field-pause__primary"
                disabled={resetBusy}
                onClick={onResetLevel}
              >
                {resetBusy ? 'Restarting…' : wouldEscalate ? 'Confirm restart (escalate)' : 'Confirm restart'}
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
              Press Esc to resume. Restart Mission clears this room attempt; the server decides whether
              required API devices increase based on unresolved API activity.
            </p>
            <div className="field-pause__actions">
              <button type="button" className="field-pause__primary" onClick={onResume}>
                Resume
              </button>
              <button type="button" className="ghost" onClick={() => setConfirmReset(true)}>
                Restart Mission
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
