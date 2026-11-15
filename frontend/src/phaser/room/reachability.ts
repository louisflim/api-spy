import type { GridPos } from './types'

const DIRS: GridPos[] = [
  { col: 1, row: 0 },
  { col: -1, row: 0 },
  { col: 0, row: 1 },
  { col: 0, row: -1 },
]

/** Matches MissionScene INTERACT_RANGE (tile centers; ortho neighbor = 40 < 58). */
export const INTERACT_RANGE_TILES = 58 / 40
export const EXIT_INTERACT_RANGE_TILES = 70 / 40

export function inBounds(col: number, row: number, cols: number, rows: number): boolean {
  return col >= 0 && row >= 0 && col < cols && row < rows
}

/** 4-neighbor BFS flood-fill from start across non-blocked cells. */
export function floodReachable(
  blocked: boolean[][],
  start: GridPos,
): Set<string> {
  const rows = blocked.length
  const cols = blocked[0]?.length ?? 0
  const key = (c: number, r: number) => `${c},${r}`
  const seen = new Set<string>()
  if (!inBounds(start.col, start.row, cols, rows) || blocked[start.row]![start.col]) {
    return seen
  }
  const queue: GridPos[] = [start]
  seen.add(key(start.col, start.row))
  while (queue.length > 0) {
    const cur = queue.shift()!
    for (const d of DIRS) {
      const nc = cur.col + d.col
      const nr = cur.row + d.row
      if (!inBounds(nc, nr, cols, rows)) continue
      if (blocked[nr]![nc]) continue
      const k = key(nc, nr)
      if (seen.has(k)) continue
      seen.add(k)
      queue.push({ col: nc, row: nr })
    }
  }
  return seen
}

export function isReachable(
  blocked: boolean[][],
  from: GridPos,
  to: GridPos,
): boolean {
  const reach = floodReachable(blocked, from)
  return reach.has(`${to.col},${to.row}`)
}

function cellDistance(a: GridPos, b: GridPos): number {
  const dc = a.col - b.col
  const dr = a.row - b.row
  return Math.hypot(dc, dr)
}

/**
 * Targets may be solid (terminals/exit). Validate that at least one adjacent
 * walkable cell is reachable from spawn and within interaction range of the
 * target center (aligned with MissionScene INTERACT_RANGE / exit range).
 */
export function canAccessTarget(
  blocked: boolean[][],
  spawn: GridPos,
  target: GridPos,
  interactRangeTiles: number = INTERACT_RANGE_TILES,
): boolean {
  const rows = blocked.length
  const cols = blocked[0]?.length ?? 0
  const reach = floodReachable(blocked, spawn)
  // Walkable target cell: standing on it is within interaction range of itself.
  if (!blocked[target.row]?.[target.col] && reach.has(`${target.col},${target.row}`)) {
    return true
  }
  for (const d of DIRS) {
    const nc = target.col + d.col
    const nr = target.row + d.row
    if (!inBounds(nc, nr, cols, rows)) continue
    if (blocked[nr]![nc]) continue
    if (!reach.has(`${nc},${nr}`)) continue
    // Ortho neighbor centers are 1 tile apart (40px) < INTERACT_RANGE (58).
    if (cellDistance({ col: nc, row: nr }, target) <= interactRangeTiles) return true
  }
  return false
}

export function allTargetsAccessible(
  blocked: boolean[][],
  spawn: GridPos,
  targets: GridPos[],
  interactRangeTiles: number = INTERACT_RANGE_TILES,
): boolean {
  return targets.every((t) => canAccessTarget(blocked, spawn, t, interactRangeTiles))
}

/** Exit uses a slightly larger interact radius in MissionScene. */
export function canAccessExit(
  blocked: boolean[][],
  spawn: GridPos,
  exit: GridPos,
): boolean {
  return canAccessTarget(blocked, spawn, exit, EXIT_INTERACT_RANGE_TILES)
}
