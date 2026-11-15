/**
 * Shared room dimensions and fallback anchors.
 * Live play uses seeded `generateRoomLayout` plans; minimap syncs from that plan.
 */
export const ROOM_WIDTH = 800
export const ROOM_HEIGHT = 600

/** Legacy fallback anchors (also used before a layout plan is ready). */
export const ROOM_LAYOUT = {
  width: ROOM_WIDTH,
  height: ROOM_HEIGHT,
  playerStart: { x: 100, y: 300 },
  terminal: { x: 420, y: 300 },
  exit: { x: 740, y: 300 },
  practicePositions: [
    { x: 580, y: 180 },
    { x: 580, y: 420 },
  ] as const,
} as const

export type { RoomLayoutPlan } from './room/types'
export { generateRoomLayout } from './room/generateRoomLayout'
export { deriveRoomSeed } from './room/seed'
