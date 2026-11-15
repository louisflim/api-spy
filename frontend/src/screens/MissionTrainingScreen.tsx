import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  fetchLessons,
  fetchMyConcepts,
  introduceLesson,
  type ConceptProgressRow,
} from '../api/client'
import { hasPracticeDrill } from '../content/practiceDrills'
import { moduleLabel, normalizeRank, RANK_LABELS } from '../content/moduleMeta'
import {
  isLessonIntroduced,
  lessonIntroductionKey,
  lessonTitle,
  missingRequiredLessons,
} from '../learning/prerequisites'
import { saveConceptStage } from '../storage/attemptStorage'
import { useAuth } from '../auth/AuthContext'
import type { Lesson, MissionSummary } from '../types/api'

interface MissionTrainingScreenProps {
  mission: MissionSummary
  onReturnToBriefing: () => void
  onOpenOpsBoard: () => void
  onOpenDashboard: () => void
  onPractice?: (mission: MissionSummary) => void
}

type LoadState = 'loading' | 'ready' | 'error'

/**
 * Module 1+ mission-specific lesson prep (ungraded).
 * Distinct from Module 0 Agent Training Academy.
 */
export function MissionTrainingScreen({
  mission,
  onReturnToBriefing,
  onOpenOpsBoard,
  onOpenDashboard,
  onPractice,
}: MissionTrainingScreenProps) {
  const { profile } = useAuth()
  const playerId = profile?.userId ?? ''
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [error, setError] = useState<string | null>(null)
  const [lessons, setLessons] = useState<Lesson[]>([])
  const [concepts, setConcepts] = useState<ConceptProgressRow[]>([])
  const [selectedLessonId, setSelectedLessonId] = useState<string | null>(null)
  const [reviewBusy, setReviewBusy] = useState(false)

  const refreshConcepts = useCallback(async () => {
    try {
      const rows = await fetchMyConcepts()
      setConcepts(rows)
      return rows
    } catch {
      return [] as ConceptProgressRow[]
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoadState('loading')
      setError(null)
      try {
        const [lessonData, conceptRows] = await Promise.all([
          fetchLessons(mission.module === 0 ? 1 : mission.module),
          fetchMyConcepts().catch(() => [] as ConceptProgressRow[]),
        ])
        if (cancelled) return
        setLessons(lessonData)
        setConcepts(conceptRows)
        const missing = missingRequiredLessons(mission, lessonData, conceptRows)
        setSelectedLessonId(
          missing[0] ?? mission.requiredLessonIds[0] ?? lessonData[0]?.id ?? null,
        )
        setLoadState('ready')
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'Unable to load mission training.')
        setLoadState('error')
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [mission])

  const requiredLessons = useMemo(() => {
    const ids = mission.requiredLessonIds ?? []
    return ids
      .map((id) => lessons.find((lesson) => lesson.id === id))
      .filter((lesson): lesson is Lesson => Boolean(lesson))
  }, [mission.requiredLessonIds, lessons])

  const missingIds = useMemo(
    () => missingRequiredLessons(mission, lessons, concepts),
    [mission, lessons, concepts],
  )

  const completedCount = Math.max(0, requiredLessons.length - missingIds.length)
  const allReady = loadState === 'ready' && requiredLessons.length > 0 && missingIds.length === 0
  const selected =
    requiredLessons.find((lesson) => lesson.id === selectedLessonId) ?? requiredLessons[0] ?? null
  const selectedReviewed = selected ? isLessonIntroduced(selected.id, concepts) : false
  const rankLabel = RANK_LABELS[normalizeRank(mission.rank)]

  async function markReviewed(lesson: Lesson) {
    if (!playerId || reviewBusy) return
    setReviewBusy(true)
    try {
      await introduceLesson(lesson.id)
      saveConceptStage(playerId, lessonIntroductionKey(lesson.id), 'INTRODUCED')
      for (const conceptTag of lesson.conceptTags) {
        saveConceptStage(playerId, conceptTag, 'INTRODUCED')
      }
      await refreshConcepts()
    } catch {
      // Content remains readable if introduce fails.
    } finally {
      setReviewBusy(false)
    }
  }

  async function handleSelectLesson(lessonId: string) {
    setSelectedLessonId(lessonId)
    const lesson = lessons.find((item) => item.id === lessonId)
    if (!lesson) return
    // Opening a required lesson records review (existing introduce contract).
    if (!isLessonIntroduced(lessonId, concepts)) {
      await markReviewed(lesson)
    }
  }

  return (
    <div className="mission-training" role="main" aria-label="Mission training">
      <header className="mission-training__header">
        <div>
          <p className="dash-kicker">Mission Training</p>
          <h1 className="dash-heading">Prepare for your next operation</h1>
          <p className="dash-note">
            {moduleLabel(mission.module)} · Level {mission.level} · {rankLabel} track · Ungraded
            prep
          </p>
        </div>
        <div className="mission-training__header-actions">
          <button type="button" className="dash-btn" onClick={onReturnToBriefing}>
            Mission Briefing
          </button>
          <button type="button" className="dash-btn" onClick={onOpenOpsBoard}>
            Operations Board
          </button>
          <button type="button" className="dash-btn" onClick={onOpenDashboard}>
            Dashboard
          </button>
        </div>
      </header>

      {loadState === 'loading' ? (
        <p className="ops-side-note">Loading mission lessons…</p>
      ) : null}
      {loadState === 'error' ? <p className="dash-note dash-note--warn">{error}</p> : null}

      {loadState === 'ready' ? (
        <>
          <section className="mission-training__readiness" aria-label="Operation briefing">
            <div className="mission-training__readiness-main">
              <p className="mission-training__eyebrow">Operation Briefing</p>
              <h2>{mission.title}</h2>
              <p>
                {completedCount}/{requiredLessons.length} required lessons reviewed
                {missingIds.length > 0
                  ? ` · Remaining: ${missingIds.map((id) => lessonTitle(id, lessons)).join(', ')}`
                  : ' · Clearance ready'}
              </p>
            </div>
            <div className="mission-training__readiness-actions">
              <div
                className="mission-training__progress"
                role="progressbar"
                aria-valuenow={completedCount}
                aria-valuemin={0}
                aria-valuemax={requiredLessons.length}
                aria-label="Required lesson progress"
              >
                <span
                  style={{
                    width:
                      requiredLessons.length === 0
                        ? '0%'
                        : `${(completedCount / requiredLessons.length) * 100}%`,
                  }}
                />
              </div>
              <button
                type="button"
                className="dash-btn dash-btn--primary"
                onClick={onReturnToBriefing}
              >
                {allReady ? 'Return to Mission Briefing' : 'Back to Briefing'}
              </button>
            </div>
          </section>

          <div className="mission-training__grid">
            <aside className="mission-training__nav" aria-label="Required lessons">
              <h2>Required Lessons</h2>
              <p className="dash-note">
                {completedCount} of {requiredLessons.length} complete
              </p>
              <ul className="mission-training__nav-list">
                {requiredLessons.map((lesson) => {
                  const done = isLessonIntroduced(lesson.id, concepts)
                  const active = selected?.id === lesson.id
                  return (
                    <li key={lesson.id}>
                      <button
                        type="button"
                        className={`mission-training__nav-btn${active ? ' is-active' : ''}${
                          done ? ' is-done' : ''
                        }`}
                        onClick={() => void handleSelectLesson(lesson.id)}
                      >
                        <span className="mission-training__nav-status" aria-hidden="true">
                          {done ? '✓' : lesson.order}
                        </span>
                        <span className="mission-training__nav-title">{lesson.title}</span>
                        <span className="visually-hidden">
                          {done ? 'Reviewed' : 'Not yet reviewed'}
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            </aside>

            <section className="mission-training__content" aria-live="polite">
              {selected ? (
                <>
                  <header className="mission-training__content-head">
                    <div>
                      <p className="mission-training__eyebrow">Lesson {selected.order}</p>
                      <h2>{selected.title}</h2>
                    </div>
                    <button
                      type="button"
                      className="dash-btn dash-btn--primary"
                      disabled={reviewBusy || selectedReviewed}
                      onClick={() => void markReviewed(selected)}
                    >
                      {selectedReviewed
                        ? 'Reviewed'
                        : reviewBusy
                          ? 'Saving…'
                          : 'Mark as reviewed'}
                    </button>
                  </header>

                  <p className="mission-training__explanation">{selected.explanation}</p>

                  <article className="mission-training__card">
                    <h3>Worked example (training only)</h3>
                    <p className="dash-note">{selected.workedExample.notes}</p>
                    <p className="mission-training__example-title">{selected.workedExample.title}</p>
                    <pre className="mission-training__code">{`${selected.workedExample.method} ${selected.workedExample.path}

Response ${selected.workedExample.responseStatus}
${JSON.stringify(selected.workedExample.responseBody, null, 2)}`}</pre>
                  </article>

                  {selected.practiceScenario?.summary ? (
                    <article className="mission-training__card mission-training__card--muted">
                      <h3>Optional linked drill</h3>
                      <p>{selected.practiceScenario.summary}</p>
                      {onPractice &&
                      selected.practiceScenario.linkedMissionId === mission.missionId &&
                      hasPracticeDrill(mission.missionId) ? (
                        <button
                          type="button"
                          className="dash-btn"
                          onClick={() => onPractice(mission)}
                        >
                          Open practice room
                        </button>
                      ) : (
                        <p className="dash-note">
                          Optional practice stays ungraded and never reveals the live mission answer.
                        </p>
                      )}
                    </article>
                  ) : null}
                </>
              ) : (
                <p className="ops-side-note">No required lessons found for this mission.</p>
              )}
            </section>
          </div>
        </>
      ) : null}
    </div>
  )
}
