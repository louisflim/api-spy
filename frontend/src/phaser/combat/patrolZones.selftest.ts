/**
 * Combat spawn clearance self-test — run via: npx tsx src/phaser/combat/patrolZones.selftest.ts
 */
import { generateRoomLayout } from '../room/generateRoomLayout'
import {
  buildCombatExclusionSet,
  resolvePatrolRoute,
  SECURITY_OFFICE_PATROL_ROUTES,
} from './patrolZones'

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg)
}

const plan = generateRoomLayout({
  missionId: 'm1-recruit-l1',
  rank: 'RECRUIT',
  roomInstance: 0,
  mandatoryTerminalCount: 1,
})

const excluded = buildCombatExclusionSet(plan)
assert(excluded.size > 0, 'exclusion set must be non-empty')

let resolvedRoutes = 0
for (const route of SECURITY_OFFICE_PATROL_ROUTES) {
  const waypoints = resolvePatrolRoute(plan, route, excluded)
  if (waypoints.length < 2) continue
  resolvedRoutes += 1
  for (const wp of waypoints) {
    assert(!plan.blocked[wp.row]![wp.col], `${route.id}: waypoint walkable`)
    assert(!excluded.has(`${wp.col},${wp.row}`), `${route.id}: waypoint outside exclusion`)
  }
}

assert(resolvedRoutes >= 1, 'at least one patrol route must resolve')

console.log(
  `patrolZones.selftest OK — ${resolvedRoutes} routes, exclusion=${excluded.size} cells`,
)
