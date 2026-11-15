import { useEffect, useMemo, useState } from 'react'
import { fetchAllMissionProgress, fetchCatalog } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { normalizeRank, RANK_LABELS, type RankKey } from '../content/moduleMeta'
import {
  isMissionCompleted,
  isMissionInProgress,
  MISSION_PROGRESS_EVENT,
  type MissionProgressUpdatedDetail,
} from '../storage/attemptStorage'
import type { CatalogResponse, MissionProgressState } from '../types/api'
import { AgentProfilePanel } from '../ui/dashboard/AgentProfilePanel'
import { AgentShowcase } from '../ui/dashboard/AgentShowcase'
import { OperationsOverview } from '../ui/dashboard/OperationsOverview'
import type { MenuDestination } from './menuTypes'

export type { MenuDestination } from './menuTypes'

interface MainMenuProps {
  onNavigate: (destination: MenuDestination) => void
}

/** Dashboard page body — chrome comes from AuthenticatedLayout. */
export function MainMenu({ onNavigate }: MainMenuProps) {
  const { profile } = useAuth()
  const [catalog, setCatalog] = useState<CatalogResponse | null>(null)
  const [catalogError, setCatalogError] = useState<string | null>(null)
  const [progressTick, setProgressTick] = useState(0)
  const [difficultyTrack, setDifficultyTrack] = useState<RankKey>('RECRUIT')
  const [serverProgress, setServerProgress] = useState<MissionProgressState[]>([])

  const playerId = profile?.userId
  const agentName = profile?.username ?? 'Recruit'

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const data = await fetchCatalog()
        if (!cancelled) {
          setCatalog(data)
          setCatalogError(null)
        }
      } catch {
        if (!cancelled) {
          setCatalog(null)
          setCatalogError('Mission catalog unavailable. Start the Java backend to load operations.')
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

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
    if (!playerId) {
      setServerProgress([])
      return
    }
    void fetchAllMissionProgress()
      .then(setServerProgress)
      .catch(() => setServerProgress([]))
  }, [playerId, progressTick])

  const serverByMission = useMemo(() => {
    const map = new Map<string, MissionProgressState>()
    for (const row of serverProgress) {
      map.set(row.missionId, row)
    }
    return map
  }, [serverProgress])

  const missionStats = useMemo(() => {
    void progressTick
    if (!catalog) {
      return { completed: null, inProgress: null, available: null, locked: null }
    }
    const scoped = catalog.missions.filter(
      (mission) => normalizeRank(mission.rank) === difficultyTrack,
    )
    let completed = 0
    let inProgress = 0
    let available = 0
    let locked = 0
    for (const mission of scoped) {
      if (!mission.playable) {
        locked += 1
        continue
      }
      if (!playerId) {
        available += 1
        continue
      }
      const server = serverByMission.get(mission.missionId)
      if (server?.completed || server?.status === 'COMPLETED' || isMissionCompleted(playerId, mission.missionId)) {
        completed += 1
      } else if (
        (server &&
          (server.unlockedDeviceIds.length > 0 ||
            server.apiResetCount > 0 ||
            server.status === 'IN_PROGRESS')) ||
        isMissionInProgress(playerId, mission.missionId)
      ) {
        inProgress += 1
      } else {
        available += 1
      }
    }
    return { completed, inProgress, available, locked }
  }, [catalog, playerId, progressTick, serverByMission, difficultyTrack])

  return (
    <div className="agent-dash" role="main" aria-label="Agent dashboard">
      <div className="m0-dash-banner">
        <div>
          <p className="dash-kicker">Module 0</p>
          <h2 className="dash-heading">Agent Training Academy</h2>
          <p className="dash-note">
            Ungraded orientation with CONTROL. Revisit anytime — outside the 3×7×3 mission catalog.
          </p>
        </div>
        <button type="button" className="dash-btn dash-btn--primary" onClick={() => onNavigate('module-0')}>
          Open Module 0
        </button>
      </div>
      <div className="agent-dash__grid">
        <AgentProfilePanel
          username={agentName}
          difficultyTrack={RANK_LABELS[difficultyTrack]}
          missionsCompleted={missionStats.completed}
          missionsInProgress={missionStats.inProgress}
          missionsAvailable={missionStats.available}
          missionsLocked={missionStats.locked}
          delayMs={90}
        />

        <AgentShowcase delayMs={150} />

        <OperationsOverview
          catalog={catalog}
          catalogError={catalogError}
          playerId={playerId}
          progressTick={progressTick}
          serverByMission={serverByMission}
          difficultyTrack={difficultyTrack}
          onDifficultyTrackChange={setDifficultyTrack}
          delayMs={210}
          onContinueMission={() => onNavigate('continue')}
          onOpenBoard={() => onNavigate('select-mission')}
        />
      </div>
    </div>
  )
}
