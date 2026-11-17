/**
 * Deterministic spawn plan self-test — npx tsx src/phaser/combat/spawnPlan.selftest.ts
 */
import { generateRoomLayout } from '../room/generateRoomLayout'
import { patrolGuardConfigForRank, patrolGuardCountForRank } from './enemyTypes'
import { planPatrolGuardSpawns } from './spawnPlan'
import { buildCombatExclusionSet } from './patrolZones'
import { ROOM_TILE_SIZE } from '../room/generateRoomLayout'

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg)
}

assert(patrolGuardCountForRank('RECRUIT') === 1, 'recruit → 1 guard')
assert(patrolGuardCountForRank('FIELD_AGENT') === 2, 'field → 2 guards')
assert(patrolGuardCountForRank('SPECIAL_AGENT') === 2, 'special → 2 guards')
// Guards must never die to a single sidearm bolt (damage 1), and get tougher by rank.
const hp = ['RECRUIT', 'FIELD_AGENT', 'SPECIAL_AGENT'].map((r) => patrolGuardConfigForRank(r).maxHealth)
assert(hp[0]! >= 3, 'recruit guard survives several hits')
assert(hp[0]! < hp[1]! && hp[1]! < hp[2]!, 'guard health rises with rank')

const plan = generateRoomLayout({
  missionId: 'm1-recruit-l1',
  rank: 'RECRUIT',
  roomInstance: 0,
  mandatoryTerminalCount: 1,
})

const a = planPatrolGuardSpawns(plan, 'RECRUIT')
const b = planPatrolGuardSpawns(plan, 'RECRUIT')
assert(a.length === 1, 'recruit spawn count')
assert(a[0]!.routeId === b[0]!.routeId, 'deterministic route')
assert(
  JSON.stringify(a[0]!.waypoints) === JSON.stringify(b[0]!.waypoints),
  'deterministic waypoints',
)

const excluded = buildCombatExclusionSet(plan)
for (const wp of a[0]!.waypoints) {
  assert(!plan.blocked[wp.row]![wp.col], 'waypoint walkable')
  assert(!excluded.has(`${wp.col},${wp.row}`), 'outside exclusion')
}

const startCol = Math.floor(plan.playerStart.x / ROOM_TILE_SIZE)
const startRow = Math.floor(plan.playerStart.y / ROOM_TILE_SIZE)
assert(
  !(a[0]!.waypoints[0]!.col === startCol && a[0]!.waypoints[0]!.row === startRow),
  'not on player spawn',
)

console.log(`spawnPlan.selftest OK — route=${a[0]!.routeId} waypoints=${a[0]!.waypoints.length}`)
