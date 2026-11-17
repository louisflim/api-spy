import { useMemo, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { drillLabel as drillLabelFor, getPracticeDetail } from '../content/practiceDrills'
import { moduleLabel, normalizeRank, RANK_LABELS } from '../content/moduleMeta'
import { conceptLabel } from '../learning/prerequisites'
import type { MissionSummary } from '../types/api'
import { ApiRequestEditor } from '../ui/ApiRequestEditor'
import { GameCanvas } from '../ui/GameCanvas'
import { GameErrorBoundary } from '../ui/GameErrorBoundary'

interface PracticeWorkspaceProps {
  mission: MissionSummary
  onBack: () => void
  backLabel?: string
}

/** Voluntary practice room — no graded first attempts or mission door unlocks. */
export function PracticeWorkspace({
  mission,
  onBack,
  backLabel = 'Return to Operations Board',
}: PracticeWorkspaceProps) {
  const { profile } = useAuth()
  const playerId = profile?.userId ?? ''
  const [editorOpen, setEditorOpen] = useState(false)
  const [cleared, setCleared] = useState(false)
  const [roomKey, setRoomKey] = useState(0)

  const detail = useMemo(() => getPracticeDetail(mission.missionId), [mission.missionId])
  const rankLabel = RANK_LABELS[normalizeRank(mission.rank)]
  const drillLabel = drillLabelFor(mission.missionId) ?? 'Terminal drill'
  const conceptTags = detail?.challenge.conceptTags ?? mission.conceptTags ?? []

  function restartDrill() {
    setEditorOpen(false)
    setCleared(false)
    setRoomKey((value) => value + 1)
  }

  if (!detail) {
    return (
      <div className="practice-sim" role="main" aria-label="Practice Mode">
        <header className="practice-sim__header">
          <div>
            <p className="dash-kicker">Practice Mode</p>
            <h1 className="dash-heading">Drill unavailable</h1>
            <p className="dash-note">
              No practice simulation is authored for {mission.title} yet.
            </p>
          </div>
          <button type="button" className="dash-btn dash-btn--primary" onClick={onBack}>
            {backLabel}
          </button>
        </header>
      </div>
    )
  }

  return (
    <div className="practice-sim" role="main" aria-label="Practice Mode">
      <header className="practice-sim__header">
        <div className="practice-sim__titles">
          <div className="practice-sim__title-row">
            <h1 className="dash-heading">Practice Mode</h1>
            <span className="practice-sim__badge">PRACTICE // UNGRADED</span>
          </div>
          <p className="dash-note practice-sim__subtitle">
            Ungraded simulation for concept review and terminal drills
          </p>
          <p className="dash-note">
            Safe training only — no coins, EXP, RCA, mission completion, or career door unlocks.
          </p>
        </div>
        <div className="practice-sim__header-actions">
          <button type="button" className="dash-btn" onClick={restartDrill}>
            Restart drill
          </button>
          <button type="button" className="dash-btn dash-btn--primary" onClick={onBack}>
            {backLabel}
          </button>
        </div>
      </header>

      <section className="practice-sim__op-strip" aria-label="Current practice drill">
        <div>
          <p className="practice-sim__eyebrow">Practice Drill</p>
          <h2>{mission.title.replace(/^Practice:\s*/i, '')}</h2>
          <p>
            {moduleLabel(mission.module)} · {rankLabel} · Practice Simulation · {drillLabel}
          </p>
        </div>
        <div className="practice-sim__op-status">
          <span className={cleared ? 'is-ok' : 'is-pending'}>
            {cleared ? 'Drill acknowledged' : 'Awaiting terminal success'}
          </span>
        </div>
      </section>

      <div className="practice-sim__body">
        <aside className="practice-sim__panel" aria-labelledby="practice-objectives">
          <h2 id="practice-objectives">Drill objectives</h2>
          <ul className="practice-sim__list">
            <li>Reach the practice terminal in the simulation room</li>
            <li>Build the documented training request (not the live mission path)</li>
            <li>Confirm the response — unlimited retries</li>
          </ul>

          <h3>Concept focus</h3>
          <div className="practice-sim__tags" aria-label="Concept tags">
            {conceptTags.map((tag) => (
              <span key={tag} className="ops-tag">
                {conceptLabel(tag)}
              </span>
            ))}
          </div>

          <h3>Controls</h3>
          <p className="dash-note">WASD / arrows move · E interact · terminal opens the practice editor</p>

          <button type="button" className="dash-btn dash-btn--primary" onClick={() => setEditorOpen(true)}>
            Open practice terminal
          </button>
        </aside>

        <section className="practice-sim__stage" aria-label="Practice simulation room">
          <div className="practice-sim__stage-chrome">
            <h2>Simulation room · top-down view</h2>
            <span>No enemies · training paths only</span>
          </div>
          <div className="practice-sim__canvas-frame">
            <GameErrorBoundary>
              <GameCanvas
                key={roomKey}
                missionId={detail.mission.missionId}
                deviceId={detail.challenge.deviceId}
                documentation={detail.challenge.documentation}
                exitReady={cleared}
                combatEnabled={false}
                onOpenTerminal={() => {
                  setEditorOpen(true)
                }}
                onExitReached={() => undefined}
              />
            </GameErrorBoundary>
          </div>
        </section>

        <aside className="practice-sim__panel practice-sim__panel--status" aria-labelledby="practice-status">
          <h2 id="practice-status">Practice status</h2>
          <dl className="practice-sim__meta">
            <div>
              <dt>Mode</dt>
              <dd>Ungraded</dd>
            </div>
            <div>
              <dt>Drill type</dt>
              <dd>Terminal concept review</dd>
            </div>
            <div>
              <dt>Progress impact</dt>
              <dd>None — excluded from RCA / rewards</dd>
            </div>
            <div>
              <dt>Result</dt>
              <dd>{cleared ? 'Acknowledged locally' : 'Not yet cleared'}</dd>
            </div>
          </dl>
          <p className="dash-note">
            Practice reinforces concepts and TVP prep. It never replaces mandatory mission attempts.
          </p>
          <button type="button" className="dash-btn" onClick={onBack}>
            {backLabel}
          </button>
        </aside>
      </div>

      {editorOpen && playerId ? (
        <ApiRequestEditor
          participantCode={playerId}
          mission={detail}
          mode="PRACTICE"
          onClose={() => setEditorOpen(false)}
          onSuccess={() => setCleared(true)}
          onAttemptSaved={() => undefined}
        />
      ) : null}
    </div>
  )
}
