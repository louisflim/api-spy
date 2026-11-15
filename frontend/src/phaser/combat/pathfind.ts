import type { NavigationGrid } from './navigationGrid'
import type { GridCell } from './types'

const ORTHO: ReadonlyArray<{ dc: number; dr: number; cost: number }> = [
  { dc: 1, dr: 0, cost: 1 },
  { dc: -1, dr: 0, cost: 1 },
  { dc: 0, dr: 1, cost: 1 },
  { dc: 0, dr: -1, cost: 1 },
]

function keyOf(col: number, row: number): string {
  return `${col},${row}`
}

function heuristic(a: GridCell, b: GridCell): number {
  return Math.abs(a.col - b.col) + Math.abs(a.row - b.row)
}

export interface PathfindResult {
  /** Empty when no route exists. Includes start cell when start === goal. */
  path: GridCell[]
  reachable: boolean
}

/**
 * 4-neighbor A* on {@link NavigationGrid}.
 * Preferable to diagonal moves: never cuts through wall corners.
 */
export function findPath(
  grid: NavigationGrid,
  startIn: GridCell,
  goalIn: GridCell,
): PathfindResult {
  const start = grid.nearestWalkable(startIn)
  const goal = grid.nearestWalkable(goalIn)
  if (!start || !goal) {
    return { path: [], reachable: false }
  }
  if (grid.sameCell(start, goal)) {
    return { path: [start], reachable: true }
  }

  const startKey = keyOf(start.col, start.row)
  const goalKey = keyOf(goal.col, goal.row)

  const open: GridCell[] = [start]
  const cameFrom = new Map<string, string>()
  const gScore = new Map<string, number>([[startKey, 0]])
  const fScore = new Map<string, number>([[startKey, heuristic(start, goal)]])
  const closed = new Set<string>()

  while (open.length > 0) {
    let bestIdx = 0
    let bestF = fScore.get(keyOf(open[0]!.col, open[0]!.row)) ?? Infinity
    for (let i = 1; i < open.length; i += 1) {
      const k = keyOf(open[i]!.col, open[i]!.row)
      const f = fScore.get(k) ?? Infinity
      if (f < bestF) {
        bestF = f
        bestIdx = i
      }
    }
    const current = open.splice(bestIdx, 1)[0]!
    const currentKey = keyOf(current.col, current.row)
    if (currentKey === goalKey) {
      return { path: reconstruct(cameFrom, current), reachable: true }
    }
    closed.add(currentKey)

    for (const step of ORTHO) {
      const nc = current.col + step.dc
      const nr = current.row + step.dr
      if (!grid.isWalkable(nc, nr)) continue
      const nk = keyOf(nc, nr)
      if (closed.has(nk)) continue
      const tentative = (gScore.get(currentKey) ?? Infinity) + step.cost
      if (tentative >= (gScore.get(nk) ?? Infinity)) continue
      cameFrom.set(nk, currentKey)
      gScore.set(nk, tentative)
      fScore.set(nk, tentative + heuristic({ col: nc, row: nr }, goal))
      if (!open.some((c) => c.col === nc && c.row === nr)) {
        open.push({ col: nc, row: nr })
      }
    }
  }

  return { path: [], reachable: false }
}

function reconstruct(cameFrom: Map<string, string>, current: GridCell): GridCell[] {
  const path: GridCell[] = [current]
  let key = keyOf(current.col, current.row)
  while (cameFrom.has(key)) {
    const prev = cameFrom.get(key)!
    const [col, row] = prev.split(',').map(Number) as [number, number]
    path.push({ col, row })
    key = prev
  }
  path.reverse()
  return path
}

/** True when a path of any length exists between two cells. */
export function hasPath(grid: NavigationGrid, from: GridCell, to: GridCell): boolean {
  return findPath(grid, from, to).reachable
}

/**
 * Drop the leading cell when it matches the guard's current cell so followers
 * immediately aim at the next waypoint.
 */
export function pathFromCurrent(path: GridCell[], current: GridCell): GridCell[] {
  if (path.length === 0) return path
  if (path[0]!.col === current.col && path[0]!.row === current.row) {
    return path.slice(1)
  }
  return path
}
