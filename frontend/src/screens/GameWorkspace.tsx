import { useCallback, useEffect, useRef, useState } from 'react'
import {
  completeMissionAtExit,
  fetchAllMissionProgress,
  fetchHealth,
  fetchLessons,
  fetchMissionDetail,
  fetchMissionProgress,
  fetchMyConcepts,
  reportBossDefeated,
  reportRoomEntered,
  reportTerminalOpened,
  reportCombatDeath,
  resetMissionRoom,
  startMissionReplay,
  type ConceptProgressRow,
} from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { publicErrorMessage } from '../security/publicErrors'
import { clearedMissionIds, effectiveOutfit, OUTFITS, type Outfit } from '../content/outfits'
import { lessonTitle, missingRequiredLessons } from '../learning/prerequisites'
import { bossConfigFor, type BossSnapshot } from '../phaser/combat'
import type { RoomLayoutPlan } from '../phaser/room/types'
import { ROOM_LAYOUT } from '../phaser/roomLayout'
import { loadMissionProgress, saveMissionProgress } from '../storage/attemptStorage'
import type {
  Lesson,
  MandatoryDeviceState,
  MissionDetail,
  MissionProgressState,
  MissionSummary,
} from '../types/api'
import { ApiRequestEditor } from '../ui/ApiRequestEditor'
import { FieldHud } from '../ui/field/FieldHud'
import { FieldMinimap } from '../ui/field/FieldMinimap'
import { FieldObjectives } from '../ui/field/FieldObjectives'
import { FieldPauseMenu } from '../ui/field/FieldPauseMenu'
import { GameCanvas, type OpenTerminalPayload, type PlayerPosition } from '../ui/GameCanvas'
import { GameErrorBoundary } from '../ui/GameErrorBoundary'
import { MissionCompleteOverlay } from '../ui/MissionCompleteOverlay'

interface GameWorkspaceProps {
  initialScreen?: 'mission'
  /** When set, opens this mission in the practice room (from Operations Board launch). */
  launchMission?: MissionSummary | null
  onBackToMenu: () => void
  /** Return to the shared Operations Board under the application navbar. */
  onOpenOpsBoard?: () => void
  /** Prefer Mission Briefing after training instead of auto-entering the room. */
  onReturnToBriefing?: () => void
  /** Next playable mission on the same track, if any. */
  nextMission?: MissionSummary | null
  onContinueNextMission?: (mission: MissionSummary) => void
}

/**
 * Graded field-room workspace, keyed by authenticated Spring user id.
 * Lesson review lives on Mission Training — this shell never shows the legacy Academy page.
 */
export function GameWorkspace({
  initialScreen = 'mission',
  launchMission = null,
  onBackToMenu,
  onOpenOpsBoard,
  onReturnToBriefing,
  nextMission = null,
  onContinueNextMission,
}: GameWorkspaceProps) {
  const { profile } = useAuth()
  const playerId = profile?.userId ?? ''
  const enterFieldDirectly = Boolean(launchMission) && initialScreen === 'mission'

  const [openingFieldRoom, setOpeningFieldRoom] = useState(enterFieldDirectly)
  const [lessons, setLessons] = useState<Lesson[]>([])
  const [missionDetail, setMissionDetail] = useState<MissionDetail | null>(null)
  const [gateMessage, setGateMessage] = useState<string | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  /** Physical terminal the agent opened — drives which challenge the editor shows. */
  const [openedTerminal, setOpenedTerminal] = useState<
    (MandatoryDeviceState & { terminalIndex: number }) | null
  >(null)
  const [exitReady, setExitReady] = useState(false)
  const [roomProgress, setRoomProgress] = useState<MissionProgressState | null>(null)
  const [missionCompleteOpen, setMissionCompleteOpen] = useState(false)
  const [replayClear, setReplayClear] = useState(false)
  const [pauseOpen, setPauseOpen] = useState(false)
  const [terminalLocated, setTerminalLocated] = useState(false)
  const [playerPos, setPlayerPos] = useState<PlayerPosition>(ROOM_LAYOUT.playerStart)
  const [roomLayout, setRoomLayout] = useState<RoomLayoutPlan | null>(null)
  /** Temporary run hearts — reset with each roomGeneration; never career progress. */
  const [hearts, setHearts] = useState(3)
  const [combatDeathPending, setCombatDeathPending] = useState(false)
  const [resetLevelBusy, setResetLevelBusy] = useState(false)
  /** Equipped outfit (cosmetic): validated against the agent's cleared missions. */
  const [outfit, setOutfit] = useState<Outfit>(OUTFITS[0]!)
  /** Canvas waits for the outfit so the room is not restarted when it arrives. */
  const [outfitReady, setOutfitReady] = useState(false)
  useEffect(() => {
    if (!playerId) return
    let live = true
    fetchAllMissionProgress()
      .then((rows) => live && setOutfit(effectiveOutfit(playerId, clearedMissionIds(rows))))
      .catch(() => undefined)
      .finally(() => live && setOutfitReady(true))
    return () => {
      live = false
    }
  }, [playerId])
  /** Level 7 boss HUD snapshot from Phaser (display only; the server owns the defeat). */
  const [bossState, setBossState] = useState<BossSnapshot | null>(null)

  const refreshConcepts = useCallback(async () => {
    try {
      return await fetchMyConcepts()
    } catch {
      return [] as ConceptProgressRow[]
    }
  }, [])

  const applyRoomProgress = useCallback((server: MissionProgressState, _code: string, _missionId: string) => {
    setRoomProgress(server)
    const activeRun = server.replayActive || server.activeRunKind === 'PRIMARY'
    setExitReady(activeRun ? server.exitReady : false)
  }, [])

  /** Ensures PRIMARY or REPLAY active run; restores mid-replay after refresh. */
  const ensureActiveRoomRun = useCallback(async (missionId: string): Promise<MissionProgressState> => {
    const server = await fetchMissionProgress(missionId)
    if (server.replayActive || server.activeRunKind === 'PRIMARY') {
      return server
    }
    if (server.completed || server.replayable) {
      return startMissionReplay(missionId)
    }
    return server
  }, [])

  const refreshSavedAttempt = useCallback(
    (code: string, missionId: string | null) => {
      if (!missionId) {
        setExitReady(false)
        return
      }
      void fetchMissionProgress(missionId)
        .then((server) => applyRoomProgress(server, code, missionId))
        .catch(() => {
          const local = loadMissionProgress(code, missionId)
          setExitReady(Boolean(local?.completed))
        })
    },
    [applyRoomProgress],
  )

  const enterMissionRoom = useCallback(
    (detail: MissionDetail) => {
      setGateMessage(null)
      setOpeningFieldRoom(false)
      setMissionDetail(detail)
      setMissionCompleteOpen(false)
      setReplayClear(false)
      setPauseOpen(false)
      setTerminalLocated(false)
      setPlayerPos(ROOM_LAYOUT.playerStart)
      setRoomLayout(null)
      void ensureActiveRoomRun(detail.mission.missionId)
        .then((server) => {
          applyRoomProgress(server, playerId, detail.mission.missionId)
          setPlayerPos(ROOM_LAYOUT.playerStart)
        })
        .catch((err) => {
          setGateMessage(publicErrorMessage(err, 'Could not start room run'))
          refreshSavedAttempt(playerId, detail.mission.missionId)
        })
    },
    [playerId, ensureActiveRoomRun, applyRoomProgress, refreshSavedAttempt],
  )

  const returnToOps = useCallback(() => {
    setPauseOpen(false)
    setEditorOpen(false)
    if (onOpenOpsBoard) {
      onOpenOpsBoard()
    } else {
      onBackToMenu()
    }
  }, [onBackToMenu, onOpenOpsBoard])

  const leaveForTraining = useCallback(() => {
    setOpeningFieldRoom(false)
    setMissionDetail(null)
    if (onReturnToBriefing) {
      onReturnToBriefing()
      return
    }
    if (onOpenOpsBoard) {
      onOpenOpsBoard()
      return
    }
    onBackToMenu()
  }, [onBackToMenu, onOpenOpsBoard, onReturnToBriefing])

  const openMission = useCallback(
    async (mission: MissionSummary) => {
      if (!mission.playable) {
        setOpeningFieldRoom(false)
        setGateMessage(`Mission not playable: ${mission.title}`)
        leaveForTraining()
        return
      }
      try {
        const detail = await fetchMissionDetail(mission.module, mission.rank, mission.level)
        const progressGate = await fetchMissionProgress(detail.mission.missionId)
        if (progressGate.lessonReviewRequired) {
          const lessonId = progressGate.requiredReviewLessonId
          const title = lessonId ? lessonTitle(lessonId, lessons) : 'the required lesson'
          setOpeningFieldRoom(false)
          setGateMessage(
            `After 15 unresolved API-driven room attempts, revisit ${title} before returning to the field. This does not reveal the graded answer.`,
          )
          leaveForTraining()
          return
        }
        const conceptRows = await refreshConcepts()
        const missing = missingRequiredLessons(detail.mission, lessons, conceptRows)
        if (missing.length > 0) {
          const titles = missing.map((id) => lessonTitle(id, lessons))
          setGateMessage(
            `Before your first independent attempt on ${detail.mission.title}, review these topics: ${titles.join(', ')}.`,
          )
          leaveForTraining()
          return
        }
        enterMissionRoom(detail)
      } catch (err) {
        setOpeningFieldRoom(false)
        setGateMessage(publicErrorMessage(err, 'Failed to open mission'))
        leaveForTraining()
      }
    },
    [enterMissionRoom, leaveForTraining, lessons, refreshConcepts],
  )

  useEffect(() => {
    let cancelled = false
    const lessonModule = launchMission?.module && launchMission.module > 0 ? launchMission.module : 1

    async function bootstrap() {
      for (let attempt = 0; attempt < 12; attempt += 1) {
        try {
          await fetchHealth()
          if (cancelled) return
          const lessonData = await fetchLessons(lessonModule)
          if (cancelled) return
          setLessons(lessonData)
          return
        } catch {
          await new Promise((resolve) => setTimeout(resolve, 800))
        }
      }
    }

    void bootstrap()
    return () => {
      cancelled = true
    }
  }, [launchMission?.module])

  useEffect(() => {
    if (missionDetail && playerId) {
      refreshSavedAttempt(playerId, missionDetail.mission.missionId)
    }
  }, [playerId, missionDetail, refreshSavedAttempt])

  useEffect(() => {
    if (roomProgress && roomProgress.unlockedDeviceIds.length > 0) {
      setTerminalLocated(true)
    }
  }, [roomProgress])

  const openedMissionIdRef = useRef<string | null>(null)

  useEffect(() => {
    if (!launchMission || lessons.length === 0) return
    // One open per missionId mount — refresh/re-render must not start a second run.
    if (openedMissionIdRef.current === launchMission.missionId) return
    openedMissionIdRef.current = launchMission.missionId
    void openMission(launchMission)
  }, [launchMission, lessons.length, openMission])

  const requiredCount = roomProgress?.requiredDeviceIds.length ?? 1
  const unlockedCount = roomProgress?.unlockedDeviceIds.length ?? 0
  const devicesComplete =
    Boolean(roomProgress) &&
    roomProgress!.requiredDeviceIds.every((id) => roomProgress!.unlockedDeviceIds.includes(id))
  const missionCompleted = Boolean(roomProgress?.completed) && !roomProgress?.replayActive
  const inputLocked = editorOpen || pauseOpen || missionCompleteOpen

  const handleResetLevel = useCallback(() => {
    if (!missionDetail || !roomProgress?.roomGeneration || resetLevelBusy) return
    setGateMessage(null)
    setResetLevelBusy(true)
    // Close terminal before remount so combat/input locks clear with the new generation.
    setEditorOpen(false)
    setOpenedTerminal(null)
    const missionId = missionDetail.mission.missionId
    const generation = roomProgress.roomGeneration
    void resetMissionRoom(missionId, 'PLAYER', generation)
      .then((server) => {
        setBossState(null)
        setRoomProgress(server)
        setExitReady(false)
        setTerminalLocated(false)
        setPauseOpen(false)
        setPlayerPos(ROOM_LAYOUT.playerStart)
        setRoomLayout(null)
        setHearts(3)
        setCombatDeathPending(false)
        refreshSavedAttempt(playerId, missionId)
      })
      .catch((err) => {
        setGateMessage(publicErrorMessage(err, 'Level reset rejected'))
      })
      .finally(() => {
        setResetLevelBusy(false)
      })
  }, [missionDetail, playerId, refreshSavedAttempt, resetLevelBusy, roomProgress?.roomGeneration])

  useEffect(() => {
    if (!missionDetail && !openingFieldRoom) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      if (missionCompleteOpen) return
      event.preventDefault()
      if (editorOpen) {
        setEditorOpen(false)
        return
      }
      setPauseOpen((open) => !open)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [missionDetail, openingFieldRoom, missionCompleteOpen, editorOpen])

  // Dev-only QA helper — never registered in production builds.
  useEffect(() => {
    if (!import.meta.env.DEV || !missionDetail) return
    const open = () => {
      setTerminalLocated(true)
      setEditorOpen(true)
    }
    window.addEventListener('apispy:open-terminal', open)
    return () => window.removeEventListener('apispy:open-terminal', open)
  }, [missionDetail])

  const applyCombatRoomReset = useCallback(
    (server: MissionProgressState) => {
      setBossState(null)
      setRoomProgress(server)
      setExitReady(false)
      setPauseOpen(false)
      setHearts(3)
      setCombatDeathPending(false)
      setPlayerPos(roomLayout?.playerStart ?? ROOM_LAYOUT.playerStart)
      setRoomLayout(null)
      if (missionDetail) {
        refreshSavedAttempt(playerId, missionDetail.mission.missionId)
      }
    },
    [missionDetail, playerId, refreshSavedAttempt, roomLayout?.playerStart],
  )

  const handleCombatRestart = useCallback(() => {
    if (!missionDetail || !roomProgress?.combatRestartAuthorized) return
    void resetMissionRoom(missionDetail.mission.missionId, 'COMBAT')
      .then(applyCombatRoomReset)
      .catch((err) => {
        setGateMessage(publicErrorMessage(err, 'Combat reset rejected'))
      })
  }, [applyCombatRoomReset, missionDetail, roomProgress?.combatRestartAuthorized])

  const handleCombatDeath = useCallback(() => {
    if (!missionDetail || !roomProgress?.roomGeneration || combatDeathPending) return
    setCombatDeathPending(true)
    const missionId = missionDetail.mission.missionId
    const generation = roomProgress.roomGeneration
    void reportCombatDeath(missionId, generation)
      .then(() => resetMissionRoom(missionId, 'COMBAT'))
      .then(applyCombatRoomReset)
      .catch((err) => {
        setCombatDeathPending(false)
        setGateMessage(publicErrorMessage(err, 'Combat restart rejected'))
      })
  }, [
    applyCombatRoomReset,
    combatDeathPending,
    missionDetail,
    roomProgress?.roomGeneration,
  ])

  const bossRoom = roomProgress?.boss ?? null

  const handleBossDefeated = useCallback(() => {
    const missionId = missionDetail?.mission.missionId
    const generation = roomProgress?.roomGeneration
    if (!missionId || !generation) return
    void reportBossDefeated(missionId, generation)
      .then((server) => {
        setRoomProgress(server)
        setExitReady(Boolean(server.exitReady))
      })
      .catch((err) => {
        setGateMessage(publicErrorMessage(err, 'Boss defeat could not be confirmed'))
      })
  }, [missionDetail?.mission.missionId, roomProgress?.roomGeneration])

  return (
    <div className="field-room">
      {missionDetail ? (
        <>
          <FieldHud
            missionTitle={missionDetail.mission.title}
            module={missionDetail.mission.module}
            level={missionDetail.mission.level}
            rank={missionDetail.mission.rank}
            hearts={hearts}
            boss={bossRoom ? bossState : null}
            pauseOpen={pauseOpen}
            onTogglePause={() => setPauseOpen((open) => !open)}
            onReturnToOps={returnToOps}
          />
          {gateMessage ? (
            <p className="field-room__gate-error" role="alert">
              {gateMessage}
            </p>
          ) : null}
          <div className="field-room__body">
            <aside className="field-room__sidebar">
              <FieldObjectives
                terminalLocated={terminalLocated}
                unlockedCount={unlockedCount}
                requiredCount={requiredCount}
                devicesComplete={devicesComplete}
                exitReady={exitReady}
                missionCompleted={missionCompleted}
                boss={bossRoom}
                bossName={
                  bossConfigFor(missionDetail.mission.module, missionDetail.mission.rank).displayName
                }
              />
              <FieldMinimap
                player={playerPos}
                exitReady={exitReady}
                devicesComplete={devicesComplete}
                layout={roomLayout}
              />
            </aside>
            <section className="field-room__stage">
              <div className="field-room__stage-chrome">
                <h2 className="field-room__stage-title">Security room · top-down view</h2>
              </div>
              <div className="field-room__canvas-frame">
                {roomProgress && outfitReady ? (
                  <GameErrorBoundary>
                    <GameCanvas
                      key={roomProgress.roomGeneration}
                      missionId={missionDetail.mission.missionId}
                      deviceId={
                        roomProgress?.mandatoryDevices?.[0]?.deviceId ??
                        missionDetail.challenge.deviceId
                      }
                      documentation={
                        roomProgress?.mandatoryDevices?.[0]?.documentation ??
                        missionDetail.challenge.documentation
                      }
                      exitReady={exitReady}
                      inputLocked={inputLocked}
                      rank={missionDetail.mission.rank}
                      roomInstance={roomProgress.apiResetCount}
                      mandatoryTerminalCount={Math.max(
                        1,
                        roomProgress.mandatoryDevices?.length ||
                          roomProgress.requiredDeviceIds.length ||
                          roomProgress.requiredDeviceCount ||
                          1,
                      )}
                      terminalBindings={roomProgress.mandatoryDevices ?? []}
                      combatEnabled
                      boss={bossRoom}
                      playerSheet={{ key: `agent-${outfit.id}`, path: outfit.sheet }}
                      onBossState={setBossState}
                      onBossDefeated={handleBossDefeated}
                      onPlayerMoved={setPlayerPos}
                      onRoomLayoutReady={(plan) => {
                        // Research log: this room generation was actually entered (SCR denominator).
                        reportRoomEntered(missionDetail.mission.missionId, roomProgress.roomGeneration)
                        setRoomLayout(plan)
                        setPlayerPos(plan.playerStart)
                        setHearts(3)
                      }}
                      onHeartsChanged={(next) => setHearts(next)}
                      onCombatDeath={handleCombatDeath}
                      onOpenTerminal={(payload: OpenTerminalPayload) => {
                        const devices = roomProgress.mandatoryDevices ?? []
                        const byIndex = devices[payload.terminalIndex]
                        const byDevice = devices.find((d) => d.deviceId === payload.deviceId)
                        const bound = byIndex ?? byDevice
                        const openedDevice = bound?.deviceId ?? payload.deviceId
                        // Research log: an opened-but-unsubmitted mandatory decision counts in RCA.
                        reportTerminalOpened(missionDetail.mission.missionId, roomProgress.roomGeneration, openedDevice)
                        setOpenedTerminal({
                          deviceId: bound?.deviceId ?? payload.deviceId,
                          challengeId: bound?.challengeId ?? payload.challengeId ?? '',
                          documentation:
                            bound?.documentation ??
                            payload.documentation ??
                            missionDetail.challenge.documentation,
                          unlocked: bound?.unlocked ?? false,
                          finalLock: bound?.finalLock ?? Boolean(payload.finalLock),
                          terminalIndex: payload.terminalIndex,
                        })
                        setTerminalLocated(true)
                        setEditorOpen(true)
                      }}
                      onExitReached={() => {
                        const generation = roomProgress?.roomGeneration
                        if (!generation) {
                          setGateMessage('Active room generation missing — reload mission progress.')
                          return
                        }
                        const finishingReplay = Boolean(roomProgress?.replayActive)
                        void completeMissionAtExit(missionDetail.mission.missionId, generation)
                          .then((server) => {
                            setRoomProgress(server)
                            setExitReady(false)
                            setReplayClear(finishingReplay)
                            saveMissionProgress({
                              participantCode: playerId,
                              missionId: missionDetail.mission.missionId,
                              doorUnlocked: true,
                              unlockedDeviceIds: server.unlockedDeviceIds,
                              completed: true,
                              updatedAt: new Date().toISOString(),
                            })
                            setMissionCompleteOpen(true)
                            refreshSavedAttempt(playerId, missionDetail.mission.missionId)
                          })
                          .catch((err) => {
                            setGateMessage(
                              publicErrorMessage(err, 'Exit completion failed'),
                            )
                          })
                      }}
                    />
                  </GameErrorBoundary>
                ) : (
                  <p className="assist" style={{ margin: 'auto' }}>
                    Preparing security room…
                  </p>
                )}
              </div>
            </section>
          </div>
          <FieldPauseMenu
            open={pauseOpen && !editorOpen && !missionCompleteOpen}
            onResume={() => setPauseOpen(false)}
            onResetLevel={handleResetLevel}
            onCombatRestart={handleCombatRestart}
            combatRestartAvailable={Boolean(roomProgress?.combatRestartAuthorized)}
            onReturnToOps={returnToOps}
            resetBusy={resetLevelBusy}
            currentDeviceCount={roomProgress?.requiredDeviceCount ?? 1}
            nextDeviceCount={roomProgress?.nextRequiredDeviceCount ?? roomProgress?.requiredDeviceCount ?? 1}
            wouldEscalate={Boolean(roomProgress?.manualRestartWouldEscalate)}
            finalLockRequired={Boolean(roomProgress?.finalLockRequired)}
          />
        </>
      ) : (
        <div className="field-room__body" style={{ placeItems: 'center' }}>
          <p className="assist">{openingFieldRoom ? 'Opening field room…' : 'Preparing mission…'}</p>
        </div>
      )}

      {editorOpen && missionDetail && playerId ? (
        <ApiRequestEditor
          participantCode={playerId}
          mission={
            openedTerminal?.challengeId
              ? {
                  ...missionDetail,
                  challenge: {
                    ...missionDetail.challenge,
                    challengeId: openedTerminal.challengeId,
                    deviceId: openedTerminal.deviceId,
                    documentation: openedTerminal.documentation,
                  },
                }
              : roomProgress?.mandatoryDevices?.[0]
                ? {
                    ...missionDetail,
                    challenge: {
                      ...missionDetail.challenge,
                      challengeId: roomProgress.mandatoryDevices[0].challengeId,
                      deviceId: roomProgress.mandatoryDevices[0].deviceId,
                      documentation: roomProgress.mandatoryDevices[0].documentation,
                    },
                  }
                : missionDetail
          }
          resumeTerminal={
            // Only resume the global TVP cursor when it belongs to this physical device.
            roomProgress?.activeTerminalChallenge &&
            openedTerminal &&
            roomProgress.activeTerminalChallenge.deviceId === openedTerminal.deviceId
              ? roomProgress.activeTerminalChallenge
              : null
          }
          mode="MISSION"
          onClose={() => {
            setEditorOpen(false)
            setOpenedTerminal(null)
          }}
          onSuccess={(payload) => {
            // Exit readiness is server-authoritative — never infer from a single unlock.
            if (payload?.exitReady) {
              setExitReady(true)
            }
            refreshSavedAttempt(playerId, missionDetail.mission.missionId)
          }}
          onAttemptSaved={() => refreshSavedAttempt(playerId, missionDetail.mission.missionId)}
          onMissionProgress={(server) => {
            setRoomProgress(server)
            setExitReady(Boolean(server.exitReady))
            if (openedTerminal) {
              const refreshed = server.mandatoryDevices?.find(
                (d) => d.deviceId === openedTerminal.deviceId,
              )
              if (refreshed) {
                setOpenedTerminal({ ...openedTerminal, ...refreshed })
              }
            }
          }}
        />
      ) : null}

      {missionCompleteOpen && missionDetail ? (
        <MissionCompleteOverlay
          missionId={missionDetail.mission.missionId}
          title={missionDetail.mission.title}
          replayClear={replayClear}
          nextMissionTitle={!replayClear && nextMission ? nextMission.title : null}
          onContinueNext={
            !replayClear && nextMission && onContinueNextMission
              ? () => {
                  setMissionCompleteOpen(false)
                  setReplayClear(false)
                  onContinueNextMission(nextMission)
                }
              : undefined
          }
          onContinue={() => {
            setMissionCompleteOpen(false)
            setReplayClear(false)
            returnToOps()
          }}
        />
      ) : null}
    </div>
  )
}
