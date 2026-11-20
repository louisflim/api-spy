interface MissionCompleteOverlayProps {
  missionId: string
  title?: string
  /** True when finishing an authorized replay run (career clear already saved). */
  replayClear?: boolean
  /** Coins granted by this clear (first clears only); null while loading or unknown. */
  coinsAwarded?: number | null
  onContinue: () => void
  /** When set, offers a next playable operation on the same track. */
  nextMissionTitle?: string | null
  onContinueNext?: () => void
}

export function MissionCompleteOverlay({
  missionId,
  title,
  replayClear = false,
  coinsAwarded = null,
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
              Replays never grant first-clear coins again. Enemies you defeat still give EXP until this
              mission's EXP limit is reached. Return to the Operations Board anytime to replay again.
            </p>
          </>
        ) : (
          <>
            <p>
              You reached the exit and cleared <strong>{title ?? missionId}</strong>. Progress is saved
              to your authenticated agent record.
            </p>
            {coinsAwarded && coinsAwarded > 0 ? (
              <p className="mission-complete__reward">
                <span aria-hidden="true">◈</span> +{coinsAwarded} coins (first-clear Intel Credit)
              </p>
            ) : null}
            <p className="hint">
              Spend coins on new guns and EXP on gun upgrades in your Loadout. Rewards only change combat —
              never your API record. You can replay this operation later without losing permanent progress.
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
