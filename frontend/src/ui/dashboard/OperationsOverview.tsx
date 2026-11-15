import { useMemo, useState } from 'react'
import {
  MODULE_TITLES,
  normalizeRank,
  RANK_LABELS,
  RANK_ORDER,
  type RankKey,
} from '../../content/moduleMeta'
import {
  isMissionCompleted,
  isMissionInProgress,
} from '../../storage/attemptStorage'
import type { CatalogResponse, MissionProgressState, MissionSummary } from '../../types/api'
import { SpyPanel } from '../spy/SpyPanel'

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
  if (!mission || !mission.playable) return 'locked'
  if (!playerId) return 'available'
  const server = serverByMission.get(mission.missionId)
  if (server?.completed || server?.status === 'COMPLETED') return 'completed'
  if (isMissionCompleted(playerId, mission.missionId)) return 'completed'
  if (
    server &&
    (server.unlockedDeviceIds.length > 0 || server.apiResetCount > 0 || server.status === 'IN_PROGRESS')
  ) {
    return 'in_progress'
  }
  if (isMissionInProgress(playerId, mission.missionId)) return 'in_progress'
  return 'available'
}

interface OperationsOverviewProps {
  catalog: CatalogResponse | null
  catalogError: string | null
  playerId: string | undefined
  progressTick: number
  serverByMission?: Map<string, MissionProgressState>
  difficultyTrack: RankKey
  onDifficultyTrackChange: (rank: RankKey) => void
  exiting?: boolean
  delayMs?: number
  onContinueMission: () => void
  onOpenBoard: () => void
}

export function OperationsOverview({
  catalog,
  catalogError,
  playerId,
  progressTick,
  serverByMission = new Map(),
  difficultyTrack,
  onDifficultyTrackChange,
  exiting = false,
  delayMs = 200,
  onContinueMission,
  onOpenBoard,
}: OperationsOverviewProps) {
  const [moduleFocus, setModuleFocus] = useState(1)

  const missions = catalog?.missions ?? []

  const modules = useMemo(() => {
    const fromCatalog = [...new Set(missions.map((m) => m.module))].sort((a, b) => a - b)
    return fromCatalog.length > 0 ? fromCatalog : [1, 2, 3]
  }, [missions])

  const levelNodes = useMemo(() => {
    void progressTick
    const byLevel = new Map(
      missions
        .filter(
          (mission) =>
            mission.module === moduleFocus && normalizeRank(mission.rank) === difficultyTrack,
        )
        .map((mission) => [mission.level, mission] as const),
    )
    return Array.from({ length: 7 }, (_, index) => {
      const level = index + 1
      const mission = byLevel.get(level) ?? null
      return {
        level,
        mission,
        state: resolveMissionState(mission, playerId, serverByMission),
      }
    })
  }, [missions, moduleFocus, difficultyTrack, playerId, progressTick, serverByMission])

  const hasInProgress = useMemo(() => {
    void progressTick
    if (!playerId || !catalog) return false
    return catalog.missions.some((mission) => {
      if (!mission.playable) return false
      const server = serverByMission.get(mission.missionId)
      if (server?.completed || server?.status === 'COMPLETED') return false
      if (
        server &&
        (server.unlockedDeviceIds.length > 0 ||
          server.apiResetCount > 0 ||
          server.status === 'IN_PROGRESS')
      ) {
        return true
      }
      return isMissionInProgress(playerId, mission.missionId)
    })
  }, [catalog, playerId, progressTick, serverByMission])

  return (
    <SpyPanel variant="ops" className="dash-panel dash-panel--ops" delayMs={delayMs} stamp="OPS">
      <div className="dash-ops__header">
        <div>
          <p className="dash-kicker">Operations overview</p>
          <h2 className="dash-heading">Mission network</h2>
        </div>
        <div className="dash-ops__actions">
          <button
            type="button"
            className="dash-btn dash-btn--primary"
            disabled={exiting}
            onClick={onContinueMission}
          >
            {hasInProgress ? 'Continue Mission' : 'Enter Field Briefing'}
          </button>
          <button
            type="button"
            className="dash-btn"
            disabled={exiting}
            onClick={onOpenBoard}
          >
            Open Operations Board
          </button>
        </div>
      </div>

      {catalogError ? (
        <p className="dash-note dash-note--warn">{catalogError}</p>
      ) : null}
      {!catalog && !catalogError ? <p className="dash-note">Loading mission catalog…</p> : null}

      <div className="dash-ops__filters">
        <label>
          <span>Module</span>
          <select
            value={moduleFocus}
            disabled={exiting || !catalog}
            onChange={(event) => setModuleFocus(Number(event.target.value))}
          >
            {modules.map((module) => (
              <option key={module} value={module}>
                Module {String(module).padStart(2, '0')}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Difficulty track</span>
          <select
            value={difficultyTrack}
            disabled={exiting}
            onChange={(event) => onDifficultyTrackChange(event.target.value as RankKey)}
          >
            {RANK_ORDER.map((rank) => (
              <option key={rank} value={rank}>
                {RANK_LABELS[rank]}
              </option>
            ))}
          </select>
        </label>
      </div>

      <p className="dash-ops__module-blurb">
        {MODULE_TITLES[moduleFocus] ?? `Module ${moduleFocus}`}
      </p>

      <div className="dash-module-grid" role="list" aria-label="Modules">
        {[1, 2, 3].map((module) => {
          const active = module === moduleFocus
          const available = catalog
            ? catalog.missions.filter((m) => m.module === module && m.playable).length
            : null
          return (
            <button
              key={module}
              type="button"
              role="listitem"
              className={`dash-module-card ${active ? 'is-active' : ''}`.trim()}
              disabled={exiting}
              onClick={() => setModuleFocus(module)}
            >
              <strong>Module {String(module).padStart(2, '0')}</strong>
              <span>{MODULE_TITLES[module] ?? 'Briefing pending'}</span>
              <em>
                {available === null
                  ? 'Catalog pending'
                  : `${available} available across tracks`}
              </em>
            </button>
          )
        })}
      </div>

      <ol className="dash-level-row" aria-label={`Seven levels · ${RANK_LABELS[difficultyTrack]}`}>
        {levelNodes.map(({ level, mission, state }) => (
          <li key={level} className={`dash-level dash-level--${state}`}>
            <span className="dash-level__num">{level}</span>
            <span className="dash-level__title">
              {mission?.title ?? `Level ${level}`}
            </span>
            <span className="dash-level__state">{STATE_LABEL[state]}</span>
          </li>
        ))}
      </ol>
    </SpyPanel>
  )
}
