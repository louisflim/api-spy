import { createRng, rngShuffle } from '../room/rng'
import type { RoomLayoutPlan } from '../room/types'
import { enemyConfigForRank, enemyRosterForRank, type PlannedGuardSpawn } from './enemyTypes'
import { NavigationGrid } from './navigationGrid'
import { hasPath } from './pathfind'
import {
  buildCombatExclusionSet,
  patrolRoutesForFootprint,
  resolvePatrolRoute,
} from './patrolZones'

/**
 * Deterministic guard placement from room seed + rank.
 * Never places on spawn / terminals / exit clearance or blocked cells.
 * Consecutive patrol waypoints must be mutually path-connected on the nav grid.
 */
export function planPatrolGuardSpawns(
  layout: RoomLayoutPlan,
  rank: string,
  nav: NavigationGrid = NavigationGrid.fromLayout(layout),
): PlannedGuardSpawn[] {
  const roster = enemyRosterForRank(rank)
  const count = roster.length
  const excluded = buildCombatExclusionSet(layout)
  const rng = createRng((layout.seed ^ 0xc0a7) >>> 0)
  const routes = patrolRoutesForFootprint(layout.footprintId)

  const candidates = rngShuffle(
    rng,
    routes.map((route) => {
      const waypoints = resolvePatrolRoute(layout, route, excluded)
      return { route, waypoints }
    }).filter((c) => c.waypoints.length >= 2 && routeIsPathConnected(nav, c.waypoints)),
  )

  const planned: PlannedGuardSpawn[] = []
  for (let i = 0; i < count && i < candidates.length; i += 1) {
    const pick = candidates[i]!
    const typeId = roster[i]!
    planned.push({
      typeId,
      config: enemyConfigForRank(typeId, rank),
      routeId: pick.route.id,
      waypoints: pick.waypoints,
    })
  }
  return planned
}

function routeIsPathConnected(nav: NavigationGrid, waypoints: { col: number; row: number }[]): boolean {
  for (let i = 0; i < waypoints.length; i += 1) {
    const a = waypoints[i]!
    const b = waypoints[(i + 1) % waypoints.length]!
    if (!hasPath(nav, a, b)) return false
  }
  return true
}
