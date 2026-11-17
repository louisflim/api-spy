import { useEffect, useRef } from 'react'
import Phaser from 'phaser'
import { createGame, MissionScene } from '../phaser/createGame'
import { GAME_EVENTS } from '../phaser/MissionScene'
import { ROOM_LAYOUT } from '../phaser/roomLayout'
import type { RoomLayoutPlan } from '../phaser/room/types'
import type { MandatoryDeviceState } from '../types/api'
import type { BossSnapshot } from '../phaser/combat'

export type PlayerFacing = 'down' | 'left' | 'right' | 'up'
export type PlayerPosition = { x: number; y: number; facing?: PlayerFacing }

export type OpenTerminalPayload = {
  documentation: string
  deviceId: string
  challengeId?: string
  finalLock?: boolean
  terminalIndex: number
  missionId?: string
}

interface GameCanvasProps {
  missionId: string
  deviceId: string
  documentation: string
  exitReady: boolean
  inputLocked?: boolean
  rank?: string
  roomInstance?: number
  mandatoryTerminalCount?: number
  /** Physical terminal → authored challenge bindings (index-aligned). */
  terminalBindings?: MandatoryDeviceState[]
  /** False for Practice Mode and Module 0 (enemy-free). */
  combatEnabled?: boolean
  onOpenTerminal: (payload: OpenTerminalPayload) => void
  onExitReached: () => void
  onPlayerMoved?: (position: PlayerPosition) => void
  onRoomLayoutReady?: (layout: RoomLayoutPlan) => void
  onHeartsChanged?: (hearts: number, maxHearts: number) => void
  onCombatDeath?: () => void
  /**
   * Level 7 boss room; shieldDown follows the server's mandatory-device state. When the
   * server already records the defeat (e.g. after a page refresh) the boss is not respawned.
   */
  boss?: { shieldDown: boolean; defeated: boolean } | null
  onBossState?: (state: BossSnapshot) => void
  onBossDefeated?: () => void
  /** Equipped outfit spritesheet (cosmetic only). */
  playerSheet?: { key: string; path: string } | null
}

export function GameCanvas({
  missionId,
  deviceId,
  documentation,
  exitReady,
  inputLocked = false,
  rank = 'RECRUIT',
  roomInstance = 0,
  mandatoryTerminalCount = 1,
  terminalBindings = [],
  combatEnabled = false,
  onOpenTerminal,
  onExitReached,
  onPlayerMoved,
  onRoomLayoutReady,
  onHeartsChanged,
  onCombatDeath,
  boss = null,
  onBossState,
  onBossDefeated,
  playerSheet = null,
}: GameCanvasProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const gameRef = useRef<Phaser.Game | null>(null)
  const onOpenTerminalRef = useRef(onOpenTerminal)
  const onExitReachedRef = useRef(onExitReached)
  const onPlayerMovedRef = useRef(onPlayerMoved)
  const onRoomLayoutReadyRef = useRef(onRoomLayoutReady)
  const onHeartsChangedRef = useRef(onHeartsChanged)
  const onCombatDeathRef = useRef(onCombatDeath)
  const onBossStateRef = useRef(onBossState)
  const onBossDefeatedRef = useRef(onBossDefeated)
  const bossShieldDownRef = useRef(Boolean(boss?.shieldDown))
  const bossDefeatedRef = useRef(Boolean(boss?.defeated))
  const bossRoomActive = Boolean(boss)
  const playerSheetKey = playerSheet?.key ?? ''
  bossDefeatedRef.current = Boolean(boss?.defeated)
  const startedRef = useRef(false)
  const inputLockedRef = useRef(inputLocked)

  useEffect(() => {
    onOpenTerminalRef.current = onOpenTerminal
  }, [onOpenTerminal])
  useEffect(() => {
    onExitReachedRef.current = onExitReached
  }, [onExitReached])
  useEffect(() => {
    onPlayerMovedRef.current = onPlayerMoved
  }, [onPlayerMoved])
  useEffect(() => {
    onRoomLayoutReadyRef.current = onRoomLayoutReady
  }, [onRoomLayoutReady])
  useEffect(() => {
    onHeartsChangedRef.current = onHeartsChanged
  }, [onHeartsChanged])
  useEffect(() => {
    onCombatDeathRef.current = onCombatDeath
  }, [onCombatDeath])
  useEffect(() => {
    onBossStateRef.current = onBossState
  }, [onBossState])
  useEffect(() => {
    onBossDefeatedRef.current = onBossDefeated
  }, [onBossDefeated])

  useEffect(() => {
    const parent = containerRef.current
    if (!parent) return

    let cancelled = false
    startedRef.current = false

    const game = createGame(parent, { doorUnlocked: exitReady })
    gameRef.current = game
    game.scene.add('MissionScene', MissionScene, false)

    const openHandler = (payload: OpenTerminalPayload) => {
      onOpenTerminalRef.current(payload)
    }
    const exitHandler = () => {
      onExitReachedRef.current()
    }
    const movedHandler = (payload: PlayerPosition) => {
      onPlayerMovedRef.current?.(payload)
    }
    const layoutHandler = (layout: RoomLayoutPlan) => {
      onRoomLayoutReadyRef.current?.(layout)
    }
    const heartsHandler = (payload: { hearts: number; maxHearts: number }) => {
      onHeartsChangedRef.current?.(payload.hearts, payload.maxHearts)
    }
    const deathHandler = () => {
      onCombatDeathRef.current?.()
    }
    const bossStateHandler = (state: BossSnapshot) => {
      onBossStateRef.current?.(state)
    }
    const bossDefeatedHandler = () => {
      onBossDefeatedRef.current?.()
    }
    game.events.on(GAME_EVENTS.OPEN_TERMINAL, openHandler)
    game.events.on(GAME_EVENTS.EXIT_REACHED, exitHandler)
    game.events.on(GAME_EVENTS.PLAYER_MOVED, movedHandler)
    game.events.on(GAME_EVENTS.ROOM_LAYOUT_READY, layoutHandler)
    game.events.on(GAME_EVENTS.HEARTS_CHANGED, heartsHandler)
    game.events.on(GAME_EVENTS.COMBAT_DEATH, deathHandler)
    game.events.on(GAME_EVENTS.BOSS_STATE, bossStateHandler)
    game.events.on(GAME_EVENTS.BOSS_DEFEATED, bossDefeatedHandler)

    const timer = window.setTimeout(() => {
      if (cancelled) return
      startedRef.current = true
      game.scene.start('MissionScene', {
        exitReady,
        missionId,
        deviceId,
        documentation,
        inputLocked: inputLockedRef.current,
        rank,
        roomInstance,
        mandatoryTerminalCount,
        terminalBindings,
        combatEnabled,
        boss: boss
          ? { shieldDown: bossShieldDownRef.current, defeated: bossDefeatedRef.current }
          : null,
        playerSheet,
      })
    }, 0)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
      game.events.off(GAME_EVENTS.OPEN_TERMINAL, openHandler)
      game.events.off(GAME_EVENTS.EXIT_REACHED, exitHandler)
      game.events.off(GAME_EVENTS.PLAYER_MOVED, movedHandler)
      game.events.off(GAME_EVENTS.ROOM_LAYOUT_READY, layoutHandler)
      game.events.off(GAME_EVENTS.HEARTS_CHANGED, heartsHandler)
      game.events.off(GAME_EVENTS.COMBAT_DEATH, deathHandler)
      game.events.off(GAME_EVENTS.BOSS_STATE, bossStateHandler)
      game.events.off(GAME_EVENTS.BOSS_DEFEATED, bossDefeatedHandler)
      game.destroy(true)
      if (gameRef.current === game) gameRef.current = null
    }
    // Recreate only when mission identity, room instance, bindings, or combat mode changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    missionId,
    deviceId,
    documentation,
    rank,
    roomInstance,
    mandatoryTerminalCount,
    combatEnabled,
    bossRoomActive,
    playerSheetKey,
    // Bindings identity for this room generation (device/challenge pairing).
    terminalBindings.map((b) => `${b.deviceId}:${b.challengeId}`).join('|'),
  ])

  useEffect(() => {
    if (gameRef.current && startedRef.current) {
      gameRef.current.events.emit(GAME_EVENTS.EXIT_READY, { ready: exitReady })
    }
  }, [exitReady])

  useEffect(() => {
    bossShieldDownRef.current = Boolean(boss?.shieldDown)
    if (gameRef.current && startedRef.current) {
      gameRef.current.events.emit(GAME_EVENTS.BOSS_SHIELD, { down: Boolean(boss?.shieldDown) })
    }
  }, [boss?.shieldDown])

  useEffect(() => {
    inputLockedRef.current = inputLocked
    const game = gameRef.current
    if (game && startedRef.current) {
      game.events.emit(GAME_EVENTS.INPUT_LOCK, { locked: inputLocked })
    }
    // Also blur the Phaser canvas so focus can move into terminal text fields.
    if (inputLocked) {
      const canvas = containerRef.current?.querySelector('canvas')
      if (canvas instanceof HTMLElement) {
        canvas.blur()
        canvas.setAttribute('tabindex', '-1')
      }
    }
  }, [inputLocked])

  return (
    <div
      className="game-shell field-room__game-shell"
      ref={containerRef}
      data-room-width={ROOM_LAYOUT.width}
      data-room-height={ROOM_LAYOUT.height}
    />
  )
}
