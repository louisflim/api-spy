import { useEffect, useMemo, useState } from 'react'
import { fetchAllMissionProgress } from '../api/client'
import {
  moduleHeading,
  moduleLabel,
  normalizeRank,
  RANK_LABELS,
  RANK_ORDER,
  type RankKey,
} from '../content/moduleMeta'
import { isCareerUnlocked, priorRecruitMissionId } from '../learning/careerProgression'
import { conceptLabel } from '../learning/prerequisites'
import { hasPracticeDrill } from '../content/practiceDrills'
import { Module0Academy } from '../screens/Module0Academy'
import {
  isMissionCompleted,
  isMissionInProgress,
  MISSION_PROGRESS_EVENT,
  type MissionProgressUpdatedDetail,
} from '../storage/attemptStorage'
import type { CatalogResponse, MissionProgressState, MissionSummary } from '../types/api'

interface MissionHubProps {
  catalog: CatalogResponse | null
  onPlay: (mission: MissionSummary) => void
  /** Voluntary ungraded practice using separate drill contracts. */
  onPractice?: (mission: MissionSummary) => void
  agentName?: string
  playerId?: string
  /** Bumped when returning to the board so mission states re-read storage. */
  progressRevision?: number
  orientationCompleted?: boolean | null
  /** Prefill MODULE dropdown (0 = Training Academy). */
  initialModule?: number
  /** Prefill difficulty track when returning from a briefing/mission. */
  initialRank?: RankKey
  onOrientationCompleted?: () => void
  /** Return to the mission briefing that opened the Training Academy. */
  onReturnToBriefing?: () => void
}

type MissionUiState = 'available' | 'locked' | 'in_progress' | 'completed'

const STATE_LABEL: Record<MissionUiState, string> = {
  available: 'Available',
  locked: 'Locked',
  in_progress: 'In Progress',
  completed: 'Completed',
}

function resolveMissionState(
  mission: MissionSummary | null,
  playerId: string | undefined,
  serverByMission: Map<string, MissionProgressState>,
): MissionUiState {
  if (!mission || !mission.playable) {
    return 'locked'
  }
  if (!playerId) {
    return 'available'
  }
  const server = serverByMission.get(mission.missionId)
  if (server?.completed || server?.status === 'COMPLETED') {
    return 'completed'
  }
  if (isMissionCompleted(playerId, mission.missionId)) {
    return 'completed'
  }
  if (!isCareerUnlocked(mission, serverByMission)) {
    return 'locked'
  }
  if (
    server &&
    (server.unlockedDeviceIds.length > 0 || server.apiResetCount > 0 || server.status === 'IN_PROGRESS')
  ) {
    return 'in_progress'
  }
  if (isMissionInProgress(playerId, mission.missionId)) {
    return 'in_progress'
  }
  return 'available'
}

export function MissionHub({
  catalog,
  onPlay,
  onPractice,
  agentName,
  playerId,
  progressRevision = 0,
  orientationCompleted = null,
  initialModule,
  initialRank,
  onOrientationCompleted,
  onReturnToBriefing,
}: MissionHubProps) {
  const missions = catalog?.missions ?? []

  const modules = useMemo(() => {
    const set = new Set<number>([0])
    for (const mission of missions) set.add(mission.module)
    return [...set].sort((a, b) => a - b)
  }, [missions])

  const [selectedModule, setSelectedModule] = useState(() =>
    initialModule !== undefined ? initialModule : 1,
  )
  const [selectedRank, setSelectedRank] = useState<RankKey>(() => initialRank ?? 'RECRUIT')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  /** Bump when progress may have changed so localStorage-backed states refresh. */
  const [progressTick, setProgressTick] = useState(0)
  const [serverProgress, setServerProgress] = useState<MissionProgressState[]>([])

  useEffect(() => {
    if (!playerId) {
      setServerProgress([])
      return
    }
    void fetchAllMissionProgress()
      .then(setServerProgress)
      .catch(() => setServerProgress([]))
  }, [playerId, progressRevision, progressTick])

  const serverByMission = useMemo(() => {
    const map = new Map<string, MissionProgressState>()
    for (const row of serverProgress) {
      map.set(row.missionId, row)
    }
    return map
  }, [serverProgress])

  useEffect(() => {
    if (initialModule !== undefined) {
      setSelectedModule(initialModule)
    }
  }, [initialModule])

  useEffect(() => {
    if (initialRank) {
      setSelectedRank(initialRank)
    }
  }, [initialRank])

  useEffect(() => {
    setProgressTick((value) => value + 1)
  }, [playerId, catalog, selectedModule, selectedRank, progressRevision])

  useEffect(() => {
    function onProgress(event: Event) {
      const detail = (event as CustomEvent<MissionProgressUpdatedDetail>).detail
      if (playerId && detail?.participantCode && detail.participantCode !== playerId) {
        return
      }
      setProgressTick((value) => value + 1)
    }
    window.addEventListener(MISSION_PROGRESS_EVENT, onProgress)
    return () => window.removeEventListener(MISSION_PROGRESS_EVENT, onProgress)
  }, [playerId])

  useEffect(() => {
    // While catalog is still loading, modules is only [0] — do not clobber a preferred module.
    if (!catalog || modules.length <= 1) return
    if (!modules.includes(selectedModule)) {
      setSelectedModule(modules.find((module) => module > 0) ?? modules[0])
    }
  }, [catalog, modules, selectedModule])

  const rankMissions = useMemo(() => {
    return missions
      .filter(
        (mission) =>
          mission.module === selectedModule && normalizeRank(mission.rank) === selectedRank,
      )
      .sort((a, b) => a.level - b.level || a.missionId.localeCompare(b.missionId))
  }, [missions, selectedModule, selectedRank])

  const pathNodes = useMemo(() => {
    const byLevel = new Map(rankMissions.map((mission) => [mission.level, mission]))
    return Array.from({ length: 7 }, (_, index) => {
      const level = index + 1
      return { level, mission: byLevel.get(level) ?? null }
    })
  }, [rankMissions])

  const nodeStates = useMemo(() => {
    void progressTick
    return pathNodes.map(({ mission }) => resolveMissionState(mission, playerId, serverByMission))
  }, [pathNodes, playerId, progressTick, serverByMission])

  useEffect(() => {
    const preferred =
      rankMissions.find((mission) => mission.playable)?.missionId ??
      rankMissions[0]?.missionId ??
      null
    const stillValid = rankMissions.some((mission) => mission.missionId === selectedId)
    if (!stillValid) {
      setSelectedId(preferred)
    }
  }, [rankMissions, selectedId])

  const selected = rankMissions.find((mission) => mission.missionId === selectedId) ?? null
  const selectedState = selected ? resolveMissionState(selected, playerId, serverByMission) : null

  const availableCount = rankMissions.filter((mission) => mission.playable).length
  const completedCount = useMemo(() => {
    void progressTick
    if (!playerId) return 0
    return rankMissions.filter(
      (mission) => resolveMissionState(mission, playerId, serverByMission) === 'completed',
    ).length
  }, [rankMissions, playerId, progressTick, serverByMission])

  const showingAcademy = selectedModule === 0

  if (!catalog) {
    return <p className="ops-empty">Loading operations board…</p>
  }

  return (
    <div className="ops-board" role="region" aria-label="Operations board">
      <header className="ops-board__header">
        <div>
          <h2>{showingAcademy ? 'Agent Training Academy' : 'Operations Board'}</h2>
          <p>
            {showingAcademy
              ? 'Module 0 orientation · ungraded interactive briefing'
              : moduleHeading(selectedModule)}
          </p>
          {orientationCompleted === false ? (
            <p className="ops-side-note">
              Agent Training Academy incomplete — select Module 0 to finish orientation.
            </p>
          ) : orientationCompleted ? (
            <p className="ops-side-note">Orientation status: Complete (revisit anytime via Module 0).</p>
          ) : null}
        </div>
        <div className="ops-agent-chip">
          <strong>{showingAcademy ? 'Orientation' : `${RANK_LABELS[selectedRank]} track`}</strong>
          <span>Agent {agentName ?? 'Recruit'}</span>
        </div>
      </header>

      {showingAcademy ? (
        <Module0Academy
          moduleSelect={
            <label>
              <span className="ops-field-label">Module</span>
              <select
                className="ops-module-select"
                value={selectedModule}
                onChange={(event) => {
                  setSelectedModule(Number(event.target.value))
                  setSelectedId(null)
                }}
              >
                {modules.map((module) => (
                  <option key={module} value={module}>
                    {moduleLabel(module)}
                  </option>
                ))}
              </select>
            </label>
          }
          onFinished={() => {
            onOrientationCompleted?.()
            if (!onReturnToBriefing) {
              setSelectedModule(1)
            }
          }}
          onReturnToBriefing={onReturnToBriefing}
        />
      ) : (
        <div className="ops-board__layout">
          <aside className="ops-side" aria-label="Module briefing">
            <h3>Module Briefing</h3>
            <p>{moduleHeading(selectedModule)}</p>

            <label>
              <span className="ops-field-label">Module</span>
              <select
                className="ops-module-select"
                value={selectedModule}
                onChange={(event) => {
                  setSelectedModule(Number(event.target.value))
                  setSelectedId(null)
                }}
              >
                {modules.map((module) => (
                  <option key={module} value={module}>
                    {moduleLabel(module)}
                  </option>
                ))}
              </select>
            </label>

            <label>
              <span className="ops-field-label">Difficulty</span>
              <select
                className="ops-rank-select"
                value={selectedRank}
                onChange={(event) => {
                  setSelectedRank(event.target.value as RankKey)
                  setSelectedId(null)
                }}
              >
                {RANK_ORDER.map((rank) => (
                  <option key={rank} value={rank}>
                    {RANK_LABELS[rank]}
                  </option>
                ))}
              </select>
            </label>

            <div className="ops-stat-block">
              <h3>Available Missions</h3>
              <p className="ops-side-note">
                {availableCount} of 7 missions on this {RANK_LABELS[selectedRank]} track are available
                to play. Levels without a live dossier stay locked.
              </p>
            </div>

            <div className="ops-stat-block">
              <h3>Your Progress</h3>
              <p className="ops-side-note">
                {playerId
                  ? `${completedCount} completed on this track (server + local progress).`
                  : 'Sign in to track completed missions.'}
              </p>
            </div>
          </aside>

          <section className="ops-path-shell" aria-label="Mission path">
            <ol className="ops-path">
              {pathNodes.map(({ level, mission }, index) => {
                const state = nodeStates[index]
                const isSelected = Boolean(mission && selected?.missionId === mission.missionId)
                const title = mission?.title ?? `Level ${level} — Not implemented yet`
                const statusText =
                  state === 'locked' && (!mission || !mission.playable)
                    ? level >= 4
                      ? 'Locked · Not implemented'
                      : 'Locked'
                    : STATE_LABEL[state]

                const className = [
                  'ops-node',
                  `ops-node--${state}`,
                  isSelected ? 'is-selected' : '',
                ]
                  .filter(Boolean)
                  .join(' ')

                const body = (
                  <>
                    <span className="ops-node__rail" aria-hidden="true">
                      <span className="ops-node__hex">{level}</span>
                      {index < pathNodes.length - 1 ? <span className="ops-node__connector" /> : null}
                    </span>
                    <span className="ops-node__meta">
                      <span className="ops-node__label">{title}</span>
                      <span className="ops-node__status">{statusText}</span>
                    </span>
                  </>
                )

                if (!mission) {
                  return (
                    <li key={`level-${level}`} className={className}>
                      <div className="ops-node__hit" aria-label={`${title} (${statusText})`}>
                        {body}
                      </div>
                    </li>
                  )
                }

                return (
                  <li key={mission.missionId} className={className}>
                    <button
                      type="button"
                      className="ops-node__hit"
                      onClick={() => setSelectedId(mission.missionId)}
                      aria-pressed={isSelected}
                      aria-label={`${title}, ${statusText}`}
                    >
                      {body}
                    </button>
                  </li>
                )
              })}
            </ol>
          </section>

          <aside className="ops-side ops-side--detail" aria-label="Mission intel">
            <h3>Operation Detail</h3>
            {selected && selectedState ? (
              <>
                <p className="ops-detail-title">
                  Level {selected.level} — {selected.title}
                </p>
                <p className="ops-brief">
                  {selected.scenarioFocus ||
                    (selected.playable
                      ? 'Open this operation to practice the documented API request in the practice room.'
                      : 'This level is not implemented yet. Choose an available mission on this track.')}
                </p>

                <div className="ops-meta-grid">
                  <div className="ops-meta-card">
                    <span>Difficulty</span>
                    <strong>{RANK_LABELS[normalizeRank(selected.rank)]}</strong>
                  </div>
                  <div className="ops-meta-card">
                    <span>Level</span>
                    <strong>{selected.level} of 7</strong>
                  </div>
                  <div className="ops-meta-card">
                    <span>Status</span>
                    <strong>{STATE_LABEL[selectedState]}</strong>
                  </div>
                  <div className="ops-meta-card">
                    <span>Operation</span>
                    <strong>
                      {moduleLabel(selected.module)} · L{selected.level}
                    </strong>
                  </div>
                </div>

                {selected.conceptTags.length > 0 ? (
                  <div className="ops-tag-row" aria-label="Skills practiced">
                    {selected.conceptTags.map((tag) => (
                      <span key={tag} className="ops-tag">
                        {conceptLabel(tag)}
                      </span>
                    ))}
                  </div>
                ) : null}

                <div className="ops-stat-block">
                  <h3>Mission Brief</h3>
                  <p className="ops-side-note">
                    {!selected.playable
                      ? 'This dossier is planned for a later build. Levels without authored contracts stay locked.'
                      : selectedState === 'locked' && priorRecruitMissionId(selected)
                        ? `Complete ${priorRecruitMissionId(selected)} before launching this Recruit operation.`
                        : 'Use the API terminal in the practice room to send the request described in the mission docs. Unlock the door to finish the room.'}
                  </p>
                </div>

                <div className="ops-launch-row">
                  <button
                    type="button"
                    className="ops-launch"
                    disabled={!selected.playable || selectedState === 'locked'}
                    onClick={() => onPlay(selected)}
                  >
                    <span aria-hidden="true">{'<<'}</span>
                    {!selected.playable || selectedState === 'locked'
                      ? 'Operation Locked'
                      : selectedState === 'completed'
                        ? 'Replay Operation'
                        : 'Launch Operation'}
                    <span aria-hidden="true">{'>>'}</span>
                  </button>
                  {onPractice && hasPracticeDrill(selected.missionId) ? (
                    <button
                      type="button"
                      className="ops-practice"
                      disabled={!selected.playable || selectedState === 'locked'}
                      onClick={() => onPractice(selected)}
                    >
                      Practice
                    </button>
                  ) : null}
                </div>
                <p className="ops-side-note">
                  {hasPracticeDrill(selected.missionId)
                    ? 'Practice uses separate training drills. It does not unlock mission doors or count as a graded first attempt.'
                    : 'No authored practice drill for this operation yet. Launch the graded mission when ready.'}
                </p>
              </>
            ) : (
              <p>Select a mission node to review the briefing.</p>
            )}
          </aside>
        </div>
      )}
    </div>
  )
}
