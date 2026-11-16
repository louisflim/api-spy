import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  fetchModule0Academy,
  fetchModule0Progress,
  updateModule0Progress,
} from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { moduleHeading } from '../content/moduleMeta'
import type { Module0Academy as AcademyData, Module0Progress, TopicStatus } from '../content/module0Types'
import { TopicConceptVisual } from '../ui/module0/TopicConceptVisual'
import { TopicInteraction } from '../ui/module0/TopicInteraction'
import { ApiRequestEditor } from '../ui/ApiRequestEditor'
import { GameCanvas } from '../ui/GameCanvas'
import { GameErrorBoundary } from '../ui/GameErrorBoundary'
import { ControlGuide } from '../ui/spy/ControlGuide'
import type { MissionDetail } from '../types/api'

interface Module0AcademyProps {
  onFinished: () => void
  /** Shared MODULE dropdown from Operations Board. */
  moduleSelect: ReactNode
  /** Return to the mission briefing that opened this academy (read-only review). */
  onReturnToBriefing?: () => void
}

function topicStatus(
  topicId: string,
  progress: Module0Progress | null,
  currentId: string,
): TopicStatus {
  if (progress?.completedTopicIds.includes(topicId)) return 'completed'
  if (progress && (progress.lastTopicId === topicId || currentId === topicId)) {
    const steps = progress.completedSteps[topicId] ?? []
    if (steps.length > 0 || currentId === topicId) return 'in_progress'
  }
  return 'not_started'
}

const STATUS_LABEL: Record<TopicStatus, string> = {
  completed: 'Completed',
  in_progress: 'In Progress',
  not_started: 'Not Started',
}

const TRAINING_DETAIL: MissionDetail = {
  mission: {
    missionId: 'm0-training-room',
    module: 0,
    rank: 'RECRUIT',
    level: 0,
    title: 'Terminal Field Training',
    status: 'TRAINING',
    playable: true,
    primaryChallengeId: 'chal-m0-training-terminal',
    requiredLessonIds: [],
    conceptTags: ['http.method.get', 'http.endpoint'],
    scenarioFocus: 'Module 0 ungraded training terminal',
  },
  challenge: {
    challengeId: 'chal-m0-training-terminal',
    variantId: 'training-locker-v1',
    deviceId: 'training-terminal',
    conceptTags: ['http.method.get', 'http.endpoint'],
    documentation:
      'MODULE 0 FIELD TRAINING — not a graded mission.\n\nRetrieve the academy training locker.\n\nMethod: GET\nPath: /api/training/lockers/academy',
    expectedMethod: 'GET',
    pathTemplate: '/api/training/lockers/academy',
    pathParameters: {},
    queryParameters: {},
  },
  conceptVariants: [],
}

/** Dedicated Module 0 Training Academy — spacious lesson workspace, not a narrow ops detail pane. */
export function Module0Academy({ onFinished, moduleSelect, onReturnToBriefing }: Module0AcademyProps) {
  const { profile } = useAuth()
  const playerId = profile?.userId ?? ''
  const [academy, setAcademy] = useState<AcademyData | null>(null)
  const [progress, setProgress] = useState<Module0Progress | null>(null)
  const [topicIndex, setTopicIndex] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [showTrainingRoom, setShowTrainingRoom] = useState(false)
  const [editorOpen, setEditorOpen] = useState(false)
  const [terminalDone, setTerminalDone] = useState(false)
  const [controlTone, setControlTone] = useState<'brief' | 'success' | 'warn'>('brief')
  const [controlMessage, setControlMessage] = useState('Stand by for briefing.')
  const [sidebarOpen, setSidebarOpen] = useState(false)

  const load = useCallback(async () => {
    const [academyData, progressData] = await Promise.all([
      fetchModule0Academy(),
      fetchModule0Progress(),
    ])
    setAcademy(academyData)
    setProgress(progressData)
    const idx = academyData.topics.findIndex((topic) => topic.id === progressData.lastTopicId)
    setTopicIndex(idx >= 0 ? idx : 0)
    const current = academyData.topics[idx >= 0 ? idx : 0]
    setControlMessage(current?.controlIntro ?? 'Welcome to the Agent Training Academy.')
    if (progressData.completedSteps[current?.id ?? '']?.includes('interaction')) {
      setTerminalDone(true)
    }
  }, [])

  useEffect(() => {
    void load().catch((err) => setError(err instanceof Error ? err.message : 'Failed to load Module 0'))
  }, [load])

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return
      if (event.key === 'ArrowLeft') {
        event.preventDefault()
        setTopicIndex((index) => Math.max(0, index - 1))
      }
      if (event.key === 'ArrowRight') {
        event.preventDefault()
        setTopicIndex((index) => Math.min((academy?.topics.length ?? 1) - 1, index + 1))
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [academy])

  const topics = academy?.topics ?? []
  const topic = topics[topicIndex] ?? null
  const completedCount = progress?.completedTopicIds.length ?? 0
  const percent = topics.length === 0 ? 0 : Math.round((completedCount / topics.length) * 100)

  const interactionDone = useMemo(() => {
    if (!topic || !progress) return false
    return (
      (progress.completedSteps[topic.id] ?? []).includes('interaction') ||
      progress.completedTopicIds.includes(topic.id)
    )
  }, [topic, progress])

  async function persist(partial: {
    topicId: string
    stepId?: string
    markTopicComplete?: boolean
    lastTopicId?: string
  }) {
    const next = await updateModule0Progress(partial)
    setProgress(next)
    if (next.orientationCompleted) {
      setControlTone('success')
      setControlMessage('Orientation complete. Return to the Operations Board when ready.')
    }
    return next
  }

  async function handleInteractionComplete() {
    if (!topic) return
    try {
      await persist({
        topicId: topic.id,
        stepId: 'interaction',
        markTopicComplete: true,
        lastTopicId: topic.id,
      })
      setControlTone('success')
      setControlMessage(topic.completionMessage || topic.interaction.successFeedback)
    } catch (err) {
      setControlTone('warn')
      setControlMessage(err instanceof Error ? err.message : 'Unable to complete this topic.')
    }
  }

  function goTo(index: number) {
    if (!topics[index]) return
    setTopicIndex(index)
    setShowTrainingRoom(false)
    setEditorOpen(false)
    setSidebarOpen(false)
    setControlTone('brief')
    setControlMessage(topics[index].controlIntro)
    setTerminalDone(
      Boolean(progress?.completedSteps[topics[index].id]?.includes('interaction')) ||
        Boolean(progress?.completedTopicIds.includes(topics[index].id)),
    )
    void persist({ topicId: topics[index].id, lastTopicId: topics[index].id })
  }

  const currentStatus: TopicStatus = topic
    ? topicStatus(topic.id, progress, topic.id)
    : 'not_started'

  return (
    <>
      <div className="m0-academy" role="main" aria-label="Agent Training Academy">
        <header className="m0-academy__top">
          <div className="m0-academy__top-copy">
            <p className="m0-academy__eyebrow">Module 0 · Orientation</p>
            <h2>{academy?.title ?? 'Agent Training Academy'}</h2>
            <p>
              {moduleHeading(0)} — ungraded briefing track. Outside RCA, SCR, HLCR, and TVP.
            </p>
          </div>
          <div className="m0-academy__top-tools">
            {moduleSelect}
            <div className="m0-progress" aria-label="Orientation progress">
              <div className="m0-progress__bar">
                <span style={{ width: `${percent}%` }} />
              </div>
              <p>
                {error
                  ? error
                  : topic
                    ? `Topic ${topic.order} of ${topics.length} · ${completedCount} complete (${percent}%)`
                    : 'Loading orientation…'}
              </p>
            </div>
            <button
              type="button"
              className="m0-topics-toggle dash-btn"
              onClick={() => setSidebarOpen((open) => !open)}
              aria-expanded={sidebarOpen}
            >
              {sidebarOpen ? 'Hide topics' : 'Topics'}
            </button>
            {onReturnToBriefing ? (
              <button type="button" className="ops-practice" onClick={onReturnToBriefing}>
                Return to Mission Briefing
              </button>
            ) : null}
          </div>
        </header>

        <div className={`m0-academy__layout ${sidebarOpen ? 'is-sidebar-open' : ''}`.trim()}>
          <aside className="m0-topics" aria-label="Orientation topics">
            <h3>Training topics</h3>
            <p className="m0-visual__lead">
              Six authored briefings. Revisit anytime. Progress survives refresh.
            </p>
            {!academy ? (
              <p className="ops-side-note">Loading topics…</p>
            ) : (
              <ol>
                {topics.map((item, index) => {
                  const status = topicStatus(item.id, progress, topic?.id ?? '')
                  const isSelected = index === topicIndex
                  return (
                    <li key={item.id}>
                      <button
                        type="button"
                        className={`m0-topic-btn m0-topic-btn--${status} ${
                          isSelected ? 'is-active' : ''
                        }`.trim()}
                        onClick={() => goTo(index)}
                        aria-pressed={isSelected}
                        aria-label={`${item.title}, ${STATUS_LABEL[status]}`}
                      >
                        <span className="m0-topic-btn__order">{item.order}</span>
                        <span className="m0-topic-btn__title">{item.title}</span>
                        <span className="m0-topic-btn__status">{STATUS_LABEL[status]}</span>
                      </button>
                    </li>
                  )
                })}
              </ol>
            )}
          </aside>

          <section className="m0-lesson" aria-label="Lesson workspace">
            {!topic ? (
              <p className="ops-side-note">Select a topic to begin orientation.</p>
            ) : (
              <>
                <div className="m0-lesson__hero">
                  <p className="m0-academy__eyebrow">
                    Topic {topic.order} of {topics.length} · {STATUS_LABEL[currentStatus]} · Ungraded
                  </p>
                  <h1>{topic.title}</h1>
                  <p className="m0-lesson__objective">
                    <strong>Objective.</strong> {topic.objective}
                  </p>
                </div>

                <ControlGuide tone={controlTone} message={controlMessage} />

                <article className="m0-lesson__section">
                  <h3>CONTROL briefing</h3>
                  <p>{topic.explanation}</p>
                </article>

                <TopicConceptVisual topic={topic} />

                <article className="m0-lesson__section">
                  <h3>Worked example</h3>
                  <p className="m0-lesson__example-title">{topic.workedExample.title}</p>
                  <p>{topic.workedExample.notes}</p>
                  <pre className="m0-example-pre">{topic.workedExample.body}</pre>
                </article>

                {!showTrainingRoom ? (
                  <TopicInteraction
                    topic={topic}
                    alreadyComplete={interactionDone}
                    onInteractionComplete={() => void handleInteractionComplete()}
                    onLaunchTerminalTraining={() => setShowTrainingRoom(true)}
                    terminalTrainingDone={terminalDone}
                  />
                ) : (
                  <div className="m0-training-room">
                    <h3>Training room</h3>
                    <p className="m0-visual__lead">
                      No enemies. Unlimited retries. Mode: TRAINING. Does not award TVP or rewrite research
                      evidence.
                    </p>
                    <GameErrorBoundary>
                      <GameCanvas
                        missionId={TRAINING_DETAIL.mission.missionId}
                        deviceId={TRAINING_DETAIL.challenge.deviceId}
                        documentation={TRAINING_DETAIL.challenge.documentation}
                        exitReady={terminalDone}
                        combatEnabled={false}
                        onOpenTerminal={() => {
                          setEditorOpen(true)
                        }}
                        onExitReached={() => undefined}
                      />
                    </GameErrorBoundary>
                    <div className="m0-nav-row">
                      <button type="button" className="dash-btn dash-btn--primary" onClick={() => setEditorOpen(true)}>
                        Open training terminal
                      </button>
                      <button type="button" className="dash-btn" onClick={() => setShowTrainingRoom(false)}>
                        Back to topic briefing
                      </button>
                    </div>
                  </div>
                )}

                <div className="m0-nav-row m0-nav-row--footer">
                  <button
                    type="button"
                    className="ops-practice"
                    disabled={topicIndex === 0}
                    onClick={() => goTo(topicIndex - 1)}
                  >
                    Previous topic
                  </button>
                  <button
                    type="button"
                    className="ops-practice"
                    disabled={topicIndex >= topics.length - 1}
                    onClick={() => goTo(topicIndex + 1)}
                  >
                    Next topic
                  </button>
                </div>

                {progress?.orientationCompleted ? (
                  <div className="m0-lesson__section m0-lesson__section--complete">
                    <h3>Orientation complete</h3>
                    <p>
                      {onReturnToBriefing
                        ? 'Review complete. Return to your mission briefing when ready — mission progress is unchanged.'
                        : 'You may revisit Module 0 anytime from the MODULE dropdown. Continue when ready for independent missions.'}
                    </p>
                    <button type="button" className="ops-launch" onClick={onFinished}>
                      <span aria-hidden="true">{'<<'}</span>
                      {onReturnToBriefing ? 'Return to Mission Briefing' : 'Continue to Module 01'}
                      <span aria-hidden="true">{'>>'}</span>
                    </button>
                  </div>
                ) : null}
              </>
            )}
          </section>
        </div>
      </div>

      {editorOpen && playerId && topic ? (
        <ApiRequestEditor
          participantCode={playerId}
          mission={TRAINING_DETAIL}
          mode="TRAINING"
          onClose={() => setEditorOpen(false)}
          onSuccess={() => {
            setTerminalDone(true)
            setControlTone('success')
            setControlMessage(topic.interaction.successFeedback)
          }}
          onAttemptSaved={() => undefined}
        />
      ) : null}
    </>
  )
}
