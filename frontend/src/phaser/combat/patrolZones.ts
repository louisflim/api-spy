import { ROOM_TILE_SIZE } from '../room/generateRoomLayout'
import type { FootprintId, RoomLayoutPlan } from '../room/types'
import type { GridCell, PatrolRoute } from './types'

/**
 * Per-footprint patrol bands away from spawn, mandatory terminals, and exit.
 * Waypoints are resolved against the live blocked grid after room generation.
 */
export const FOOTPRINT_PATROL_ROUTES: Record<FootprintId, PatrolRoute[]> = {
  security_office: [
    {
      id: 'north-records',
      waypoints: [
        { col: 5, row: 1 },
        { col: 9, row: 1 },
        { col: 12, row: 2 },
        { col: 7, row: 2 },
      ],
    },
    {
      id: 'south-supply',
      waypoints: [
        { col: 4, row: 12 },
        { col: 8, row: 11 },
        { col: 11, row: 12 },
        { col: 6, row: 12 },
      ],
    },
  ],
  security_annex: [
    {
      id: 'north-gallery',
      waypoints: [
        { col: 3, row: 1 },
        { col: 9, row: 1 },
        { col: 16, row: 1 },
        { col: 9, row: 2 },
      ],
    },
    {
      id: 'cross-hall',
      waypoints: [
        { col: 4, row: 7 },
        { col: 9, row: 7 },
        { col: 15, row: 7 },
        { col: 9, row: 6 },
      ],
    },
  ],
  security_server_wing: [
    {
      id: 'spine-loop',
      waypoints: [
        { col: 8, row: 4 },
        { col: 9, row: 6 },
        { col: 8, row: 8 },
        { col: 9, row: 10 },
      ],
    },
    {
      id: 'south-hall',
      waypoints: [
        { col: 4, row: 12 },
        { col: 10, row: 12 },
        { col: 15, row: 12 },
        { col: 10, row: 11 },
      ],
    },
  ],
  security_checkpoint: [
    {
      id: 'north-desk',
      waypoints: [
        { col: 4, row: 1 },
        { col: 10, row: 1 },
        { col: 15, row: 1 },
        { col: 10, row: 2 },
      ],
    },
    {
      id: 'approach-loop',
      waypoints: [
        { col: 5, row: 7 },
        { col: 10, row: 7 },
        { col: 14, row: 7 },
        { col: 10, row: 9 },
      ],
    },
  ],
  security_clearance_hall: [
    {
      id: 'hall-eastbound',
      waypoints: [
        { col: 4, row: 5 },
        { col: 10, row: 5 },
        { col: 16, row: 5 },
        { col: 10, row: 4 },
      ],
    },
    {
      id: 'south-alcove',
      waypoints: [
        { col: 4, row: 9 },
        { col: 10, row: 8 },
        { col: 16, row: 9 },
        { col: 10, row: 9 },
      ],
    },
  ],
  security_purge_bay: [
    {
      id: 'west-arm',
      waypoints: [
        { col: 2, row: 1 },
        { col: 3, row: 4 },
        { col: 2, row: 6 },
        { col: 4, row: 7 },
      ],
    },
    {
      id: 'east-arm',
      waypoints: [
        { col: 16, row: 1 },
        { col: 15, row: 4 },
        { col: 16, row: 6 },
        { col: 14, row: 7 },
      ],
    },
  ],
}

/** @deprecated Prefer {@link patrolRoutesForFootprint} — kept for older selftests. */
export const SECURITY_OFFICE_PATROL_ROUTES = FOOTPRINT_PATROL_ROUTES.security_office

export function patrolRoutesForFootprint(footprintId: FootprintId): PatrolRoute[] {
  return FOOTPRINT_PATROL_ROUTES[footprintId] ?? FOOTPRINT_PATROL_ROUTES.security_office
}

const CLEARANCE_RADIUS = 2

function keyOf(col: number, row: number): string {
  return `${col},${row}`
}

/** Cells where enemies must never spawn or park as patrol anchors. */
export function buildCombatExclusionSet(layout: RoomLayoutPlan): Set<string> {
  const excluded = new Set<string>()
  const mark = (col: number, row: number, radius: number) => {
    for (let r = row - radius; r <= row + radius; r += 1) {
      for (let c = col - radius; c <= col + radius; c += 1) {
        if (r < 0 || c < 0 || r >= layout.rows || c >= layout.cols) continue
        excluded.add(keyOf(c, r))
      }
    }
  }

  const spawnCol = Math.floor(layout.playerStart.x / ROOM_TILE_SIZE)
  const spawnRow = Math.floor(layout.playerStart.y / ROOM_TILE_SIZE)
  mark(spawnCol, spawnRow, CLEARANCE_RADIUS)

  const exitCol = Math.floor(layout.exit.x / ROOM_TILE_SIZE)
  const exitRow = Math.floor(layout.exit.y / ROOM_TILE_SIZE)
  mark(exitCol, exitRow, CLEARANCE_RADIUS)

  for (const terminal of layout.mandatoryTerminals) {
    mark(
      Math.floor(terminal.x / ROOM_TILE_SIZE),
      Math.floor(terminal.y / ROOM_TILE_SIZE),
      CLEARANCE_RADIUS,
    )
  }
  for (const slot of layout.practiceSlots) {
    mark(Math.floor(slot.x / ROOM_TILE_SIZE), Math.floor(slot.y / ROOM_TILE_SIZE), 1)
  }
  return excluded
}

function isWalkable(layout: RoomLayoutPlan, col: number, row: number): boolean {
  if (row < 0 || col < 0 || row >= layout.rows || col >= layout.cols) return false
  return !layout.blocked[row]![col]
}

/** Prefer the authored cell; otherwise search a small ring for a walkable non-excluded cell. */
export function resolveWalkableCell(
  layout: RoomLayoutPlan,
  preferred: GridCell,
  excluded: Set<string>,
): GridCell | null {
  const candidates: GridCell[] = [preferred]
  for (let radius = 1; radius <= 3; radius += 1) {
    for (let dr = -radius; dr <= radius; dr += 1) {
      for (let dc = -radius; dc <= radius; dc += 1) {
        if (Math.abs(dr) !== radius && Math.abs(dc) !== radius) continue
        candidates.push({ col: preferred.col + dc, row: preferred.row + dr })
      }
    }
  }
  for (const cell of candidates) {
    if (!isWalkable(layout, cell.col, cell.row)) continue
    if (excluded.has(keyOf(cell.col, cell.row))) continue
    return cell
  }
  return null
}

export function resolvePatrolRoute(
  layout: RoomLayoutPlan,
  route: PatrolRoute,
  excluded: Set<string>,
): GridCell[] {
  const resolved: GridCell[] = []
  for (const wp of route.waypoints) {
    const cell = resolveWalkableCell(layout, wp, excluded)
    if (!cell) continue
    const last = resolved[resolved.length - 1]
    if (last && last.col === cell.col && last.row === cell.row) continue
    resolved.push(cell)
  }
  return resolved
}

export function cellToWorld(cell: GridCell): { x: number; y: number } {
  return {
    x: cell.col * ROOM_TILE_SIZE + ROOM_TILE_SIZE / 2,
    y: cell.row * ROOM_TILE_SIZE + ROOM_TILE_SIZE / 2,
  }
}
