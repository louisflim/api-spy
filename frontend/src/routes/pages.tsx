import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import {
  fetchAllMissionProgress,
  fetchCatalog,
  fetchLessons,
  fetchModule0Progress,
  fetchMyConcepts,
} from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { hasPracticeDrill } from '../content/practiceDrills'
import { normalizeRank } from '../content/moduleMeta'
import { missingRequiredLessons } from '../learning/prerequisites'
import { ForgotPasswordScreen } from '../screens/ForgotPasswordScreen'
import { GameWorkspace } from '../screens/GameWorkspace'
import { LoadoutScreen } from '../screens/LoadoutScreen'
import { LoginScreen } from '../screens/LoginScreen'
import { MainMenu } from '../screens/MainMenu'
import { MissionBriefingScreen } from '../screens/MissionBriefingScreen'
import { MissionTrainingScreen } from '../screens/MissionTrainingScreen'
import { OperationsBoardScreen } from '../screens/OperationsBoardScreen'
import { PlaceholderScreen } from '../screens/PlaceholderScreen'
import { PracticeWorkspace } from '../screens/PracticeWorkspace'
import { RegisterScreen } from '../screens/RegisterScreen'
import { isMissionCompleted, isMissionInProgress } from '../storage/attemptStorage'
import type { MissionSummary } from '../types/api'
import { useMissionOutlet } from './MissionRoute'
import { parsePracticeFrom, paths, safeInternalPath, type PracticeFrom } from './paths'

function isInProgressMission(
  mission: MissionSummary,
  playerId: string,
  serverByMission: Map<
    string,
    { completed?: boolean; status?: string; unlockedDeviceIds: string[]; apiResetCount: number }
  >,
): boolean {
  if (!mission.playable) return false
  const server = serverByMission.get(mission.missionId)
  if (server?.completed || server?.status === 'COMPLETED') return false
  if (isMissionCompleted(playerId, mission.missionId)) return false
  if (
    server &&
    (server.unlockedDeviceIds.length > 0 ||
      server.apiResetCount > 0 ||
      server.status === 'IN_PROGRESS')
  ) {
    return true
  }
  return isMissionInProgress(playerId, mission.missionId)
}

export function LoginPage() {
  const navigate = useNavigate()
  return (
    <LoginScreen
      onGoRegister={() => navigate(paths.register)}
      onForgotPassword={() => navigate(paths.forgotPassword)}
    />
  )
}

export function RegisterPage() {
  const navigate = useNavigate()
  return <RegisterScreen onGoLogin={() => navigate(paths.login)} />
}

export function ForgotPasswordPage() {
  const navigate = useNavigate()
  return <ForgotPasswordScreen onBack={() => navigate(paths.login)} />
}

export function DashboardPage() {
  const navigate = useNavigate()
  const { profile } = useAuth()

  async function continueToBriefing() {
    if (!profile) {
      navigate(paths.operations)
      return
    }
    try {
      const [catalog, progressRows] = await Promise.all([fetchCatalog(), fetchAllMissionProgress()])
      const serverByMission = new Map(progressRows.map((row) => [row.missionId, row]))
      const inProgress = catalog.missions
        .filter((mission) => isInProgressMission(mission, profile.userId, serverByMission))
        .sort((a, b) => a.module - b.module || a.level - b.level)
      const target =
        inProgress[0] ??
        catalog.missions.find((mission) => mission.playable && mission.module > 0)
      if (target) {
        navigate(paths.missionBriefing(target.missionId))
        return
      }
      navigate(paths.operations)
    } catch {
      navigate(paths.operations)
    }
  }

  return (
    <MainMenu
      onNavigate={(destination) => {
        if (destination === 'continue') {
          void continueToBriefing()
          return
        }
        if (destination === 'dashboard') {
          navigate(paths.dashboard)
          return
        }
        if (destination === 'select-mission') {
          navigate(paths.operations)
          return
        }
        if (destination === 'module-0') {
          navigate(paths.trainingAcademy)
          return
        }
        if (destination === 'profile') {
          navigate(paths.profile)
          return
        }
        if (destination === 'loadout') {
          navigate(paths.loadout)
          return
        }
        navigate(paths.settings)
      }}
    />
  )
}

export function OperationsPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const moduleParam = searchParams.get('module')
  const initialModule = moduleParam != null && moduleParam !== '' ? Number(moduleParam) : undefined

  return (
    <OperationsBoardScreen
      key={`ops-${initialModule ?? 'default'}`}
      initialModule={Number.isFinite(initialModule) ? initialModule : undefined}
      onPlay={(mission) => navigate(paths.missionBriefing(mission.missionId))}
      onPractice={(mission) => navigate(paths.missionPractice(mission.missionId, 'ops'))}
    />
  )
}

export function TrainingAcademyPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const returnTo = safeInternalPath(searchParams.get('returnTo'))
  const leaveAcademy = () => navigate(returnTo ?? paths.operations)

  return (
    <OperationsBoardScreen
      key={`training-academy-${returnTo ?? 'ops'}`}
      initialModule={0}
      onPlay={(mission) => navigate(paths.missionBriefing(mission.missionId))}
      onPractice={(mission) => navigate(paths.missionPractice(mission.missionId, 'ops'))}
      onOrientationCompleted={leaveAcademy}
      onReturnToBriefing={returnTo ? leaveAcademy : undefined}
    />
  )
}

export function ProfilePage() {
  return (
    <PlaceholderScreen
      title="Agent Profile"
      blurb="Profile details and career stats will live here. Not implemented yet."
    />
  )
}

export function LoadoutPage() {
  return <LoadoutScreen />
}

export function SettingsPage() {
  return (
    <PlaceholderScreen
      title="Settings"
      blurb="Audio, accessibility, and account settings will be added later."
    />
  )
}

export function MissionBriefingPage() {
  const { mission } = useMissionOutlet()
  const navigate = useNavigate()

  return (
    <MissionBriefingScreen
      mission={mission}
      onBack={() => navigate(paths.operationsModule(mission.module))}
      onBeginMission={() => navigate(paths.missionField(mission.missionId))}
      onOpenLevelTraining={() => navigate(paths.missionTraining(mission.missionId))}
      onPractice={(m) => navigate(paths.missionPractice(m.missionId, 'briefing'))}
    />
  )
}

export function MissionTrainingPage() {
  const { mission } = useMissionOutlet()
  const navigate = useNavigate()

  return (
    <MissionTrainingScreen
      mission={mission}
      onReturnToBriefing={() => navigate(paths.missionBriefing(mission.missionId))}
      onOpenOpsBoard={() => navigate(paths.operationsModule(mission.module))}
      onOpenDashboard={() => navigate(paths.dashboard)}
      onPractice={(m) => navigate(paths.missionPractice(m.missionId, 'training'))}
    />
  )
}

export function MissionPracticePage() {
  const { mission } = useMissionOutlet()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const from = parsePracticeFrom(searchParams.get('from'))

  if (!hasPracticeDrill(mission.missionId)) {
    return <Navigate to={paths.missionBriefing(mission.missionId)} replace />
  }

  const backLabel =
    from === 'briefing'
      ? 'Return to Mission Briefing'
      : from === 'training'
        ? 'Return to Mission Training'
        : 'Return to Operations Board'

  function leavePractice() {
    if (from === 'briefing') {
      navigate(paths.missionBriefing(mission.missionId))
      return
    }
    if (from === 'training') {
      navigate(paths.missionTraining(mission.missionId))
      return
    }
    navigate(paths.operationsModule(mission.module))
  }

  return (
    <PracticeWorkspace mission={mission} backLabel={backLabel} onBack={leavePractice} />
  )
}

/**
 * Field room gate: orientation + required lessons before Phaser entry.
 * GameWorkspace.ensureActiveRoomRun reuses PRIMARY/REPLAY — no duplicate runs on refresh.
 */
export function MissionFieldPage() {
  const { mission } = useMissionOutlet()
  const navigate = useNavigate()
  const [gate, setGate] = useState<'loading' | 'ready' | 'redirect-orientation' | 'redirect-briefing'>(
    'loading',
  )
  const [nextMission, setNextMission] = useState<MissionSummary | null>(null)

  useEffect(() => {
    let cancelled = false
    async function check() {
      try {
        if (!mission.playable) {
          if (!cancelled) setGate('redirect-briefing')
          return
        }
        const [orientation, lessons, concepts, catalog] = await Promise.all([
          fetchModule0Progress(),
          fetchLessons(mission.module > 0 ? mission.module : 1),
          fetchMyConcepts(),
          fetchCatalog(),
        ])
        if (cancelled) return
        if (!orientation.orientationCompleted) {
          setGate('redirect-orientation')
          return
        }
        const missing = missingRequiredLessons(mission, lessons, concepts)
        if (missing.length > 0) {
          setGate('redirect-briefing')
          return
        }
        const rank = normalizeRank(mission.rank)
        const candidates = catalog.missions
          .filter(
            (row) =>
              row.module === mission.module &&
              normalizeRank(row.rank) === rank &&
              row.playable &&
              row.level > mission.level,
          )
          .sort((a, b) => a.level - b.level)
        setNextMission(candidates[0] ?? null)
        setGate('ready')
      } catch {
        if (!cancelled) setGate('redirect-briefing')
      }
    }
    void check()
    return () => {
      cancelled = true
    }
  }, [mission])

  if (gate === 'loading') {
    return (
      <div className="spy-stage spy-stage--hq">
        <div className="spy-stage__content">
          <div className="auth-panel">
            <p className="auth-eyebrow">FIELD CLEARANCE</p>
            <h1>Checking prerequisites…</h1>
          </div>
        </div>
      </div>
    )
  }
  if (gate === 'redirect-orientation') {
    return <Navigate to={paths.trainingAcademy} replace />
  }
  if (gate === 'redirect-briefing') {
    return <Navigate to={paths.missionBriefing(mission.missionId)} replace />
  }

  return (
    <GameWorkspace
      initialScreen="mission"
      launchMission={mission}
      onBackToMenu={() => navigate(paths.dashboard)}
      onOpenOpsBoard={() => navigate(paths.operationsModule(mission.module))}
      onReturnToBriefing={() => navigate(paths.missionBriefing(mission.missionId))}
      nextMission={nextMission}
      onContinueNextMission={(next) => navigate(paths.missionBriefing(next.missionId))}
    />
  )
}

export type { PracticeFrom }
