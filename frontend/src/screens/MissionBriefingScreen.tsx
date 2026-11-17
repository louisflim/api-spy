import { useEffect, useMemo, useState } from 'react'
import {
  fetchLessons,
  fetchModule0Progress,
  fetchMyConcepts,
  type ConceptProgressRow,
} from '../api/client'
import {
  conceptLabel,
  lessonTitle,
  missingRequiredLessons,
} from '../learning/prerequisites'
import { hasPracticeDrill } from '../content/practiceDrills'
import { moduleLabel, normalizeRank, RANK_LABELS } from '../content/moduleMeta'
import { publicErrorMessage } from '../security/publicErrors'
import type { Lesson, MissionSummary } from '../types/api'

interface MissionBriefingScreenProps {
  mission: MissionSummary
  onBack: () => void
  onBeginMission: () => void
  /** Open existing mission-specific level training (/missions/:id/training). */
  onOpenLevelTraining: () => void
  /** Optional ungraded practice for this mission when a drill is authored. */
  onPractice?: (mission: MissionSummary) => void
}

type LoadState = 'loading' | 'ready' | 'error'

/**
 * Spy-themed mission dossier between Operations Board launch and Phaser gameplay.
 * Does not alter learning rules — only surfaces Module 0 + required-lesson gates.
 */
export function MissionBriefingScreen({
  mission,
  onBack,
  onBeginMission,
  onOpenLevelTraining,
  onPractice,
}: MissionBriefingScreenProps) {
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [error, setError] = useState<string | null>(null)
  const [orientationCompleted, setOrientationCompleted] = useState(false)
  const [lessons, setLessons] = useState<Lesson[]>([])
  const [concepts, setConcepts] = useState<ConceptProgressRow[]>([])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoadState('loading')
      setError(null)
      try {
        const [progress, lessonData, conceptRows] = await Promise.all([
          fetchModule0Progress(),
          fetchLessons(mission.module === 0 ? 1 : mission.module),
          fetchMyConcepts().catch(() => [] as ConceptProgressRow[]),
        ])
        if (cancelled) return
        setOrientationCompleted(progress.orientationCompleted)
        setLessons(lessonData)
        setConcepts(conceptRows)
        setLoadState('ready')
      } catch (err) {
        if (cancelled) return
        setError(publicErrorMessage(err, 'Unable to load mission briefing.'))
        setLoadState('error')
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [mission.missionId, mission.module])

  const missingLessonIds = useMemo(
    () => missingRequiredLessons(mission, lessons, concepts),
    [mission, lessons, concepts],
  )

  const missingLessonTitles = useMemo(
    () => missingLessonIds.map((id) => lessonTitle(id, lessons)),
    [missingLessonIds, lessons],
  )

  const objectives = useMemo(() => {
    const items = [
      'Reach the field terminal in the escape room',
      'Send the documented API request from the terminal',
      'Unlock the exit door to complete the operation',
    ]
    if (mission.scenarioFocus?.trim()) {
      return [mission.scenarioFocus.trim(), ...items]
    }
    return items
  }, [mission.scenarioFocus])

  const rankLabel = RANK_LABELS[normalizeRank(mission.rank)]
  const ready =
    loadState === 'ready' && orientationCompleted && missingLessonIds.length === 0

  return (
    <div className="briefing" role="main" aria-label="Mission briefing">
      <header className="briefing__header">
        <div>
          <p className="dash-kicker">Mission Briefing</p>
          <h1 className="dash-heading">{mission.title}</h1>
          <p className="dash-note">
            {moduleLabel(mission.module)} · Level {mission.level} · {rankLabel} track
          </p>
        </div>
        <button type="button" className="dash-btn" onClick={onBack}>
          Return to Operations Board
        </button>
      </header>

      <div className="briefing__grid">
        <section className="briefing__panel" aria-labelledby="briefing-overview">
          <h2 id="briefing-overview">Operation Overview</h2>
          <p className="ops-brief">
            {mission.scenarioFocus?.trim() ||
              'Infiltrate the practice room, use the API terminal, and unlock the exit door.'}
          </p>

          <div className="ops-meta-grid">
            <div className="ops-meta-card">
              <span>Difficulty</span>
              <strong>{rankLabel}</strong>
            </div>
            <div className="ops-meta-card">
              <span>Module</span>
              <strong>{moduleLabel(mission.module)}</strong>
            </div>
            <div className="ops-meta-card">
              <span>Level</span>
              <strong>{mission.level} of 7</strong>
            </div>
            <div className="ops-meta-card">
              <span>Status</span>
              <strong>{mission.playable ? 'Ready to brief' : 'Unavailable'}</strong>
            </div>
          </div>

          <div className="ops-stat-block">
            <h3>Objectives</h3>
            <ul className="briefing__objectives">
              {objectives.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>

          {mission.conceptTags.length > 0 ? (
            <div className="ops-stat-block">
              <h3>Required API Skills</h3>
              <div className="ops-tag-row" aria-label="Required skills">
                {mission.conceptTags.map((tag) => (
                  <span key={tag} className="ops-tag">
                    {conceptLabel(tag)}
                  </span>
                ))}
              </div>
            </div>
          ) : null}
        </section>

        <section className="briefing__panel" aria-labelledby="briefing-clearance">
          <h2 id="briefing-clearance">Clearance Check</h2>

          {loadState === 'loading' ? (
            <p className="ops-side-note">Verifying orientation and training clearance…</p>
          ) : null}

          {loadState === 'error' ? (
            <p className="dash-note dash-note--warn">{error}</p>
          ) : null}

          {loadState === 'ready' ? (
            <>
              <ul className="briefing__checklist">
                <li className={orientationCompleted ? 'is-done' : 'is-missing'}>
                  <strong>Agent Training Academy (Module 0)</strong>
                  <span>
                    {orientationCompleted
                      ? 'Orientation complete'
                      : 'Orientation incomplete — finish Module 0 first'}
                  </span>
                </li>
                <li className={missingLessonIds.length === 0 ? 'is-done' : 'is-missing'}>
                  <strong>Mission training lessons</strong>
                  <span>
                    {missingLessonIds.length === 0
                      ? 'Required mission lessons reviewed'
                      : `Open each required lesson in Training: ${missingLessonTitles.join(', ')}`}
                  </span>
                </li>
              </ul>

              {ready ? (
                <p className="ops-side-note">
                  Clearance confirmed. Begin the mission to enter the field room.
                </p>
              ) : (
                <p className="ops-side-note">
                  Complete the missing training below, then return here to begin the mission.
                </p>
              )}
            </>
          ) : null}

          <div className="ops-launch-row briefing__actions">
            <button type="button" className="ops-practice" onClick={onBack}>
              Back
            </button>

            {onPractice && hasPracticeDrill(mission.missionId) ? (
              <button type="button" className="ops-practice" onClick={() => onPractice(mission)}>
                Practice Mode
              </button>
            ) : null}

            <button type="button" className="ops-practice" onClick={onOpenLevelTraining}>
              Open Level Training
            </button>

            <button
              type="button"
              className="ops-launch"
              disabled={!ready || !mission.playable}
              onClick={onBeginMission}
            >
              <span aria-hidden="true">{'<<'}</span>
              Begin Mission
              <span aria-hidden="true">{'>>'}</span>
            </button>
          </div>
        </section>
      </div>
    </div>
  )
}
