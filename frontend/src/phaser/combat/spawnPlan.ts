import { createRng, rngShuffle } from '../room/rng'
import type { RoomLayoutPlan } from '../room/types'
import {
  PATROL_GUARD_CONFIG,
  patrolGuardCountForRank,
  type PlannedGuardSpawn,
} from './enemyTypes'
import {
  buildCombatExclusionSet,
  patrolRoutesForFootprint,
  resolvePatrolRoute,
} from './patrolZones'

/**
 * Deterministic guard placement from room seed + rank.
 * Never places on spawn / terminals / exit clearance or blocked cells.
 */
export function planPatrolGuardSpawns(
  layout: RoomLayoutPlan,
  rank: string,
): PlannedGuardSpawn[] {
  const count = patrolGuardCountForRank(rank)
  const excluded = buildCombatExclusionSet(layout)
  const rng = createRng((layout.seed ^ 0xc0a7) >>> 0)
  const routes = patrolRoutesForFootprint(layout.footprintId)

  const candidates = rngShuffle(
    rng,
    routes.map((route) => {
      const waypoints = resolvePatrolRoute(layout, route, excluded)
      return { route, waypoints }
    }).filter((c) => c.waypoints.length >= 2),
  )

  const planned: PlannedGuardSpawn[] = []
  for (let i = 0; i < count && i < candidates.length; i += 1) {
    const pick = candidates[i]!
    planned.push({
      typeId: 'PATROL_GUARD',
      config: PATROL_GUARD_CONFIG,
      routeId: pick.route.id,
      waypoints: pick.waypoints,
    })
  }
  return planned
}
