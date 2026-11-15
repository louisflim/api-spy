interface MissionCompleteOverlayProps {
  missionId: string
  title?: string
  /** True when finishing an authorized replay run (career clear already saved). */
  replayClear?: boolean
  onContinue: () => void
  /** When set, offers a next playable operation on the same track. */
  nextMissionTitle?: string | null
  onContinueNext?: () => void
}

export function MissionCompleteOverlay({
  missionId,
  title,
  replayClear = false,
  onContinue,
  nextMissionTitle = null,
  onContinueNext,
}: MissionCompleteOverlayProps) {
  return (
    <div className="editor-overlay" role="dialog" aria-modal="true" aria-label="Mission complete">
      <div className="editor-panel">
        <div className="editor-header">
          <h2>{replayClear ? 'Replay Cleared' : 'Mission Complete'}</h2>
        </div>
        {replayClear ? (
          <>
            <p>
              You finished a replay of <strong>{title ?? missionId}</strong>. Career completion,
              first-attempt research records, and Terminal Verification Progress are unchanged.
            </p>
            <p className="hint">
              No duplicate first-clear rewards were granted. Return to the Operations Board anytime to
              replay again.
            </p>
          </>
        ) : (
          <>
            <p>
              You reached the exit and cleared <strong>{title ?? missionId}</strong>. Progress is saved
              to your authenticated agent record.
            </p>
            <p className="hint">
              No stars, XP, or coins are awarded. You can replay this operation later without losing
              permanent progress.
            </p>
          </>
        )}
        <div className="ops-launch-row" style={{ marginTop: '0.75rem' }}>
          {onContinueNext && nextMissionTitle ? (
            <button type="button" className="ops-launch" onClick={onContinueNext}>
              Continue to {nextMissionTitle}
            </button>
          ) : null}
          <button type="button" onClick={onContinue}>
            Return to Operations Board
          </button>
        </div>
      </div>
    </div>
  )
}
