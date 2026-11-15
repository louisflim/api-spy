import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  fetchModule0Academy,
  fetchModule0Progress,
  updateModule0Progress,
} from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { moduleHeading } from '../content/moduleMeta'
import type { Module0Academy as AcademyData, Module0Progress, TopicStatus } from '../content/module0Types'
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

/** Module 0 content in the same Operations Board 3-column shell as Modules 01–03. */
export function Module0Academy({ onFinished, moduleSelect }: Module0AcademyProps) {
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
    await persist({ topicId: topic.id, stepId: 'interaction', lastTopicId: topic.id })
    setControlTone('success')
    setControlMessage(topic.interaction.successFeedback)
  }

  async function markTopicComplete() {
    if (!topic) return
    try {
      await persist({
        topicId: topic.id,
        stepId: 'interaction',
        markTopicComplete: true,
        lastTopicId: topic.id,
      })
      setControlTone('success')
      setControlMessage(topic.completionMessage)
    } catch (err) {
      setControlTone('warn')
      setControlMessage(err instanceof Error ? err.message : 'Complete the interaction first.')
    }
  }

  function goTo(index: number) {
    if (!topics[index]) return
    setTopicIndex(index)
    setShowTrainingRoom(false)
    setEditorOpen(false)
    setControlTone('brief')
    setControlMessage(topics[index].controlIntro)
    void persist({ topicId: topics[index].id, lastTopicId: topics[index].id })
  }

  const currentStatus: TopicStatus = topic
    ? topicStatus(topic.id, progress, topic.id)
    : 'not_started'

  function nodeStateClass(status: TopicStatus): string {
    if (status === 'completed') return 'completed'
    if (status === 'in_progress') return 'in_progress'
    return 'available'
  }

  return (
    <>
      <div className="ops-board__layout" role="main" aria-label="Agent Training Academy">
        <aside className="ops-side" aria-label="Module briefing">
          <h3>Module Briefing</h3>
          <p>{moduleHeading(0)}</p>

          {moduleSelect}

          <div className="ops-stat-block">
            <h3>Available Topics</h3>
            <p className="ops-side-note">
              {topics.length > 0
                ? `${topics.length} orientation topics on this track. Ungraded — outside the 3×7×3 catalog.`
                : 'Loading orientation topics…'}
            </p>
          </div>

          <div className="ops-stat-block">
            <h3>Your Progress</h3>
            <p className="ops-side-note">
              {error
                ? error
                : `${completedCount} of ${topics.length || 6} topics complete (${percent}%).`}
            </p>
            <div className="m0-progress" aria-hidden="true">
              <div className="m0-progress__bar">
                <span style={{ width: `${percent}%` }} />
              </div>
            </div>
          </div>
        </aside>

        <section className="ops-path-shell" aria-label="Training topics">
          {!academy ? (
            <p className="ops-side-note">Loading topics…</p>
          ) : (
            <ol className="ops-path">
              {topics.map((item, index) => {
                const status = topicStatus(item.id, progress, topic?.id ?? '')
                const isSelected = index === topicIndex
                const className = [
                  'ops-node',
                  `ops-node--${nodeStateClass(status)}`,
                  isSelected ? 'is-selected' : '',
                ]
                  .filter(Boolean)
                  .join(' ')

                return (
                  <li key={item.id} className={className}>
                    <button
                      type="button"
                      className="ops-node__hit"
                      onClick={() => goTo(index)}
                      aria-pressed={isSelected}
                      aria-label={`${item.title}, ${STATUS_LABEL[status]}`}
                    >
                      <span className="ops-node__rail" aria-hidden="true">
                        <span className="ops-node__hex">{item.order}</span>
                        {index < topics.length - 1 ? <span className="ops-node__connector" /> : null}
                      </span>
                      <span className="ops-node__meta">
                        <span className="ops-node__label">{item.title}</span>
                        <span className="ops-node__status">{STATUS_LABEL[status]}</span>
                      </span>
                    </button>
                  </li>
                )
              })}
            </ol>
          )}
        </section>

        <aside className="ops-side ops-side--detail" aria-label="Topic detail">
          <h3>Operation Detail</h3>
          {!topic ? (
            <p className="ops-side-note">Select a topic node to review the briefing.</p>
          ) : (
            <>
              <p className="ops-detail-title">
                Topic {topic.order} — {topic.title}
              </p>
              <p className="ops-brief">{topic.objective}</p>

              <div className="ops-meta-grid">
                <div className="ops-meta-card">
                  <span>Track</span>
                  <strong>Orientation</strong>
                </div>
                <div className="ops-meta-card">
                  <span>Topic</span>
                  <strong>
                    {topic.order} of {topics.length}
                  </strong>
                </div>
                <div className="ops-meta-card">
                  <span>Status</span>
                  <strong>{STATUS_LABEL[currentStatus]}</strong>
                </div>
                <div className="ops-meta-card">
                  <span>Graded</span>
                  <strong>No</strong>
                </div>
              </div>

              <ControlGuide tone={controlTone} message={controlMessage} />

              <div className="ops-stat-block">
                <h3>Briefing</h3>
                <p className="ops-side-note">{topic.explanation}</p>
              </div>

              <div className="ops-stat-block">
                <h3>Worked Example</h3>
                <p className="ops-side-note">{topic.workedExample.title}</p>
                <p className="ops-side-note">{topic.workedExample.notes}</p>
                <pre className="m0-example-pre">{topic.workedExample.body}</pre>
              </div>

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
                  <p className="ops-side-note">No enemies. Unlimited retries. Mode: TRAINING.</p>
                  <GameErrorBoundary>
                    <GameCanvas
                      missionId={TRAINING_DETAIL.mission.missionId}
                      deviceId={TRAINING_DETAIL.challenge.deviceId}
                      documentation={TRAINING_DETAIL.challenge.documentation}
                      exitReady={terminalDone}
                      combatEnabled={false}
                      onOpenTerminal={() => setEditorOpen(true)}
                      onOpenPractice={() => setEditorOpen(true)}
                      onExitReached={() => undefined}
                    />
                  </GameErrorBoundary>
                  <button type="button" className="dash-btn" onClick={() => setEditorOpen(true)}>
                    Open training terminal
                  </button>
                  <button
                    type="button"
                    className="dash-btn"
                    onClick={() => setShowTrainingRoom(false)}
                  >
                    Back to topic briefing
                  </button>
                </div>
              )}

              <div className="ops-launch-row">
                <button
                  type="button"
                  className="ops-practice"
                  disabled={topicIndex === 0}
                  onClick={() => goTo(topicIndex - 1)}
                >
                  Previous
                </button>
                <button
                  type="button"
                  className="ops-launch"
                  disabled={!interactionDone}
                  onClick={() => void markTopicComplete()}
                >
                  <span aria-hidden="true">{'<<'}</span>
                  Mark Topic Complete
                  <span aria-hidden="true">{'>>'}</span>
                </button>
                <button
                  type="button"
                  className="ops-practice"
                  disabled={topicIndex >= topics.length - 1}
                  onClick={() => goTo(topicIndex + 1)}
                >
                  Next
                </button>
              </div>

              {progress?.orientationCompleted ? (
                <div className="ops-stat-block">
                  <h3>Orientation complete</h3>
                  <p className="ops-side-note">
                    You may revisit Module 0 anytime from the MODULE dropdown.
                  </p>
                  <button type="button" className="ops-launch" onClick={onFinished}>
                    <span aria-hidden="true">{'<<'}</span>
                    Continue to Module 01
                    <span aria-hidden="true">{'>>'}</span>
                  </button>
                </div>
              ) : null}
            </>
          )}
        </aside>
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
