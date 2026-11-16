import { getFootprint } from './footprints'
import { footprintIdForMission } from './missionFootprint'
import { allTargetsAccessible, canAccessExit } from './reachability'
import { createRng, rngInt, rngShuffle, type Rng } from './rng'
import { deriveRoomSeed } from './seed'
import { DEFAULT_ROOM_THEME } from './themes'
import type {
  FloorVariant,
  FootprintCell,
  GridPos,
  PlacedInteractive,
  PlacedProp,
  PropKind,
  RoomFootprint,
  RoomGenerateInput,
  RoomLayoutPlan,
  WorldPos,
} from './types'

export const ROOM_TILE_SIZE = 40
export const ROOM_COLS = 20
export const ROOM_ROWS = 15
export const MAX_LAYOUT_ATTEMPTS = 32
/** Optional adaptive practice doors removed — keep 0 slots in live layouts. */
export const PRACTICE_SLOT_COUNT = 0

const NON_SOLID: PropKind[] = ['security_camera', 'security_light']

function cellCenter(col: number, row: number): WorldPos {
  return {
    x: col * ROOM_TILE_SIZE + ROOM_TILE_SIZE / 2,
    y: row * ROOM_TILE_SIZE + ROOM_TILE_SIZE / 2,
  }
}

function keyOf(col: number, row: number): string {
  return `${col},${row}`
}

function cloneGrid<T>(grid: T[][]): T[][] {
  return grid.map((row) => [...row])
}

function blockedFromFootprint(cells: FootprintCell[][]): boolean[][] {
  return cells.map((row) => row.map((cell) => cell !== 'floor'))
}

function paintFloors(
  footprint: RoomFootprint,
  rng: Rng,
): FloorVariant[][] {
  const floor: FloorVariant[][] = Array.from({ length: footprint.rows }, () =>
    Array.from({ length: footprint.cols }, () => 'base' as FloorVariant),
  )
  for (let r = 0; r < footprint.rows; r += 1) {
    for (let c = 0; c < footprint.cols; c += 1) {
      if (footprint.cells[r]![c] !== 'floor') continue
      const roll = rng()
      if (roll < 0.2) floor[r]![c] = 'alt'
      else if (roll < 0.32) floor[r]![c] = 'worn'
      else floor[r]![c] = 'base'
    }
  }
  for (const cell of footprint.entranceCells) {
    if (footprint.cells[cell.row]?.[cell.col] === 'floor') {
      floor[cell.row]![cell.col] = 'entrance'
    }
  }
  for (const cell of footprint.checkpointCells) {
    if (footprint.cells[cell.row]?.[cell.col] === 'floor') {
      floor[cell.row]![cell.col] = 'checkpoint'
    }
  }
  // Corridor mark accents along the coarser spawn→exit axis (works for non-horizontal exits).
  const c0 = Math.min(footprint.spawn.col, footprint.exit.col)
  const c1 = Math.max(footprint.spawn.col, footprint.exit.col)
  const r0 = Math.min(footprint.spawn.row, footprint.exit.row)
  const r1 = Math.max(footprint.spawn.row, footprint.exit.row)
  for (let c = c0 + 1; c < c1; c += 1) {
    const r = footprint.spawn.row
    if (footprint.cells[r]?.[c] === 'floor' && c % 2 === 0) {
      floor[r]![c] = 'mark'
    }
  }
  for (let r = r0 + 1; r < r1; r += 1) {
    const c = footprint.exit.col
    if (footprint.cells[r]?.[c] === 'floor' && r % 2 === 0) {
      floor[r]![c] = 'mark'
    }
  }
  return floor
}

function isFloor(footprint: RoomFootprint, col: number, row: number): boolean {
  return footprint.cells[row]?.[col] === 'floor'
}

function tryOccupy(
  blocked: boolean[][],
  footprint: RoomFootprint,
  col: number,
  row: number,
  reserved: Set<string>,
): boolean {
  if (!isFloor(footprint, col, row)) return false
  if (blocked[row]![col]) return false
  const k = keyOf(col, row)
  if (reserved.has(k)) return false
  blocked[row]![col] = true
  reserved.add(k)
  return true
}

function depthBiasFor(kind: PropKind): number {
  if (kind === 'filing_cabinet' || kind === 'server_rack' || kind === 'monitor_bank') return 2
  if (kind === 'desk' || kind === 'computer_console') return 1
  return 0
}

function placeProp(
  kind: PropKind,
  col: number,
  row: number,
  blocked: boolean[][],
  footprint: RoomFootprint,
  reserved: Set<string>,
): PlacedProp | null {
  const solid = !NON_SOLID.includes(kind)
  if (solid) {
    if (!tryOccupy(blocked, footprint, col, row, reserved)) return null
  } else {
    if (!isFloor(footprint, col, row) || blocked[row]![col] || reserved.has(keyOf(col, row))) {
      return null
    }
    reserved.add(keyOf(col, row))
  }
  const center = cellCenter(col, row)
  return {
    kind,
    col,
    row,
    x: center.x,
    y: center.y,
    solid,
    footprintCols: 1,
    footprintRows: 1,
    depthBias: depthBiasFor(kind),
  }
}

/** Keep vestibule / checkpoint / main aisle clear of decorative props only. */
function reservePropClearance(
  footprint: RoomFootprint,
  reserved: Set<string>,
  protectedSlots: Set<string>,
): void {
  const { spawn, exit } = footprint
  const add = (col: number, row: number) => {
    const k = keyOf(col, row)
    if (protectedSlots.has(k)) return
    if (isFloor(footprint, col, row)) reserved.add(k)
  }
  for (const cell of footprint.entranceCells) add(cell.col, cell.row)
  for (const cell of footprint.checkpointCells) add(cell.col, cell.row)
  // Clear the coarser axis between spawn and exit (supports south/north exits).
  const c0 = Math.min(spawn.col, exit.col)
  const c1 = Math.max(spawn.col, exit.col)
  const r0 = Math.min(spawn.row, exit.row)
  const r1 = Math.max(spawn.row, exit.row)
  for (let c = c0; c <= c1; c += 1) add(c, spawn.row)
  for (let r = r0; r <= r1; r += 1) add(exit.col, r)
}

function placeZones(
  footprint: RoomFootprint,
  blocked: boolean[][],
  reserved: Set<string>,
  rng: Rng,
): PlacedProp[] {
  const props: PlacedProp[] = []
  for (const zone of footprint.propZones) {
    const candidates = rngShuffle(
      rng,
      zone.cells.filter((cell) => isFloor(footprint, cell.col, cell.row)),
    )
    const target = rngInt(rng, zone.min, zone.max + 1)
    let placed = 0
    for (const cell of candidates) {
      if (placed >= target) break
      const kind = zone.preferredKinds[rngInt(rng, 0, zone.preferredKinds.length)]!
      const prop = placeProp(kind, cell.col, cell.row, blocked, footprint, reserved)
      if (prop) {
        props.push(prop)
        placed += 1
      }
    }
  }
  return props
}

function finalizePlan(args: {
  input: RoomGenerateInput
  footprint: RoomFootprint
  seed: number
  seedKey: string
  blocked: boolean[][]
  floor: FloorVariant[][]
  mandatoryGrid: GridPos[]
  practiceGrid: GridPos[]
  props: PlacedProp[]
  usedFallback: boolean
}): RoomLayoutPlan {
  const {
    input,
    footprint,
    seed,
    seedKey,
    blocked,
    floor,
    mandatoryGrid,
    practiceGrid,
    props,
    usedFallback,
  } = args

  const interactives: PlacedInteractive[] = [
    ...mandatoryGrid.map((g, index) => ({
      kind: 'mandatory_terminal' as const,
      index,
      col: g.col,
      row: g.row,
      ...cellCenter(g.col, g.row),
    })),
    ...practiceGrid.map((g, index) => ({
      kind: 'practice_terminal' as const,
      index,
      col: g.col,
      row: g.row,
      ...cellCenter(g.col, g.row),
    })),
    {
      kind: 'exit' as const,
      index: 0,
      col: footprint.exit.col,
      row: footprint.exit.row,
      ...cellCenter(footprint.exit.col, footprint.exit.row),
    },
  ]

  return {
    themeId: input.themeId ?? DEFAULT_ROOM_THEME,
    footprintId: footprint.id,
    seed,
    seedKey,
    missionId: input.missionId,
    rank: input.rank,
    roomInstance: input.roomInstance,
    mandatoryTerminalCount: mandatoryGrid.length,
    cols: footprint.cols,
    rows: footprint.rows,
    tileSize: ROOM_TILE_SIZE,
    blocked,
    cellKinds: cloneGrid(footprint.cells),
    floorVariants: floor,
    playerStart: cellCenter(footprint.spawn.col, footprint.spawn.row),
    exit: cellCenter(footprint.exit.col, footprint.exit.row),
    mandatoryTerminals: mandatoryGrid.map((g) => cellCenter(g.col, g.row)),
    practiceSlots: practiceGrid.map((g) => cellCenter(g.col, g.row)),
    props,
    interactives,
    usedFallback,
  }
}

function attemptLayout(
  input: RoomGenerateInput,
  footprint: RoomFootprint,
  seed: number,
  seedKey: string,
  rng: Rng,
): RoomLayoutPlan | null {
  const blocked = blockedFromFootprint(footprint.cells)
  const floor = paintFloors(footprint, rng)
  const reserved = new Set<string>([
    keyOf(footprint.spawn.col, footprint.spawn.row),
    keyOf(footprint.exit.col, footprint.exit.row),
  ])

  const mandCount = Math.max(1, Math.min(5, input.mandatoryTerminalCount))
  const mandatoryGrid: GridPos[] = []
  for (const slot of footprint.mandatorySlots.slice(0, mandCount)) {
    if (!tryOccupy(blocked, footprint, slot.col, slot.row, reserved)) return null
    mandatoryGrid.push(slot)
  }
  if (mandatoryGrid.length < mandCount) return null

  const practiceGrid: GridPos[] = []
  for (const slot of footprint.practiceSlots.slice(0, PRACTICE_SLOT_COUNT)) {
    if (!tryOccupy(blocked, footprint, slot.col, slot.row, reserved)) return null
    practiceGrid.push(slot)
  }
  if (practiceGrid.length < PRACTICE_SLOT_COUNT) return null

  const protectedSlots = new Set<string>([
    ...mandatoryGrid.map((g) => keyOf(g.col, g.row)),
    ...practiceGrid.map((g) => keyOf(g.col, g.row)),
  ])
  reservePropClearance(footprint, reserved, protectedSlots)

  let props = placeZones(footprint, blocked, reserved, rng)

  const walkTargets: GridPos[] = [...mandatoryGrid, ...practiceGrid]
  let ok =
    allTargetsAccessible(cloneGrid(blocked), footprint.spawn, walkTargets) &&
    canAccessExit(cloneGrid(blocked), footprint.spawn, footprint.exit)

  // Decorative props must never invalidate an authored footprint. Drop props if they pinch paths.
  if (!ok) {
    const cleared = blockedFromFootprint(footprint.cells)
    for (const g of [...mandatoryGrid, ...practiceGrid]) {
      cleared[g.row]![g.col] = true
    }
    if (
      !allTargetsAccessible(cleared, footprint.spawn, walkTargets) ||
      !canAccessExit(cleared, footprint.spawn, footprint.exit)
    ) {
      return null
    }
    props = []
    for (let r = 0; r < blocked.length; r += 1) {
      blocked[r] = [...cleared[r]!]
    }
    ok = true
  }

  return finalizePlan({
    input,
    footprint,
    seed,
    seedKey,
    blocked,
    floor,
    mandatoryGrid,
    practiceGrid,
    props,
    usedFallback: false,
  })
}

function layoutTargetsOk(
  blocked: boolean[][],
  spawn: GridPos,
  mandatoryGrid: GridPos[],
  practiceGrid: GridPos[],
  exit: GridPos,
): boolean {
  return (
    allTargetsAccessible(blocked, spawn, [...mandatoryGrid, ...practiceGrid]) &&
    canAccessExit(blocked, spawn, exit)
  )
}

/**
 * Last-resort layout: terminals + exit only (no decorative solids) so authored
 * footprint reachability is preserved. Still runs the same access checks.
 */
function buildFallback(
  input: RoomGenerateInput,
  footprint: RoomFootprint,
  seed: number,
  seedKey: string,
): RoomLayoutPlan {
  const blocked = blockedFromFootprint(footprint.cells)
  const floor = paintFloors(footprint, createRng(seed ^ 0xabc123))
  const reserved = new Set<string>([
    keyOf(footprint.spawn.col, footprint.spawn.row),
    keyOf(footprint.exit.col, footprint.exit.row),
  ])

  const mandCount = Math.max(1, Math.min(5, input.mandatoryTerminalCount))
  const mandatoryGrid: GridPos[] = []
  for (const slot of footprint.mandatorySlots.slice(0, mandCount)) {
    tryOccupy(blocked, footprint, slot.col, slot.row, reserved)
    mandatoryGrid.push(slot)
  }
  const practiceGrid: GridPos[] = []
  for (const slot of footprint.practiceSlots.slice(0, PRACTICE_SLOT_COUNT)) {
    tryOccupy(blocked, footprint, slot.col, slot.row, reserved)
    practiceGrid.push(slot)
  }
  reservePropClearance(
    footprint,
    reserved,
    new Set([
      ...mandatoryGrid.map((g) => keyOf(g.col, g.row)),
      ...practiceGrid.map((g) => keyOf(g.col, g.row)),
    ]),
  )

  // Non-solid decor only — never risk soft-locking with solid fallback props.
  const props: PlacedProp[] = []
  for (const g of [
    { kind: 'security_camera' as PropKind, col: 2, row: 2 },
    { kind: 'security_light' as PropKind, col: 18, row: 2 },
  ]) {
    const prop = placeProp(g.kind, g.col, g.row, blocked, footprint, reserved)
    if (prop) props.push(prop)
  }

  if (
    !layoutTargetsOk(
      cloneGrid(blocked),
      footprint.spawn,
      mandatoryGrid,
      practiceGrid,
      footprint.exit,
    )
  ) {
    throw new Error(
      `Room footprint fallback failed reachability for mission=${input.missionId} footprint=${footprint.id} instance=${input.roomInstance}`,
    )
  }

  return finalizePlan({
    input,
    footprint,
    seed,
    seedKey,
    blocked,
    floor,
    mandatoryGrid,
    practiceGrid,
    props,
    usedFallback: true,
  })
}

/**
 * Deterministic layout over an authored footprint.
 * Walls/void never randomize — only props inside safe zones.
 */
export function generateRoomLayout(input: RoomGenerateInput): RoomLayoutPlan {
  const footprintId = input.footprintId ?? footprintIdForMission(input.missionId)
  const footprint = getFootprint(footprintId)
  const { seed, seedKey } = deriveRoomSeed(input.missionId, input.rank, input.roomInstance)
  const baseRng = createRng(seed)

  for (let attempt = 0; attempt < MAX_LAYOUT_ATTEMPTS; attempt += 1) {
    const attemptSeed = (seed ^ Math.imul(attempt + 1, 0x9e3779b9)) >>> 0
    const rng = createRng(attemptSeed)
    baseRng()
    const plan = attemptLayout(input, footprint, seed, seedKey, rng)
    if (plan) return plan
  }

  return buildFallback(input, footprint, seed, seedKey)
}

export function layoutsEqualForSeed(a: RoomLayoutPlan, b: RoomLayoutPlan): boolean {
  return (
    a.seed === b.seed &&
    a.footprintId === b.footprintId &&
    a.playerStart.x === b.playerStart.x &&
    a.playerStart.y === b.playerStart.y &&
    a.exit.x === b.exit.x &&
    a.exit.y === b.exit.y &&
    JSON.stringify(a.mandatoryTerminals) === JSON.stringify(b.mandatoryTerminals) &&
    JSON.stringify(a.practiceSlots) === JSON.stringify(b.practiceSlots) &&
    JSON.stringify(a.props.map((p) => [p.kind, p.col, p.row])) ===
      JSON.stringify(b.props.map((p) => [p.kind, p.col, p.row]))
  )
}
