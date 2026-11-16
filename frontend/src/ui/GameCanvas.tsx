import { useEffect, useRef } from 'react'
import Phaser from 'phaser'
import { createGame, MissionScene } from '../phaser/createGame'
import { GAME_EVENTS } from '../phaser/MissionScene'
import { ROOM_LAYOUT } from '../phaser/roomLayout'
import type { RoomLayoutPlan } from '../phaser/room/types'
import type { MandatoryDeviceState } from '../types/api'

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
}: GameCanvasProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const gameRef = useRef<Phaser.Game | null>(null)
  const onOpenTerminalRef = useRef(onOpenTerminal)
  const onExitReachedRef = useRef(onExitReached)
  const onPlayerMovedRef = useRef(onPlayerMoved)
  const onRoomLayoutReadyRef = useRef(onRoomLayoutReady)
  const onHeartsChangedRef = useRef(onHeartsChanged)
  const onCombatDeathRef = useRef(onCombatDeath)
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
    game.events.on(GAME_EVENTS.OPEN_TERMINAL, openHandler)
    game.events.on(GAME_EVENTS.EXIT_REACHED, exitHandler)
    game.events.on(GAME_EVENTS.PLAYER_MOVED, movedHandler)
    game.events.on(GAME_EVENTS.ROOM_LAYOUT_READY, layoutHandler)
    game.events.on(GAME_EVENTS.HEARTS_CHANGED, heartsHandler)
    game.events.on(GAME_EVENTS.COMBAT_DEATH, deathHandler)

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
    // Bindings identity for this room generation (device/challenge pairing).
    terminalBindings.map((b) => `${b.deviceId}:${b.challengeId}`).join('|'),
  ])

  useEffect(() => {
    if (gameRef.current && startedRef.current) {
      gameRef.current.events.emit(GAME_EVENTS.EXIT_READY, { ready: exitReady })
    }
  }, [exitReady])

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
