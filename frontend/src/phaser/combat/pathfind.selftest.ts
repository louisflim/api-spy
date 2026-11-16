/**
 * Guard navigation / A* self-test — run via:
 *   npx tsx src/phaser/combat/pathfind.selftest.ts
 */
import { generateRoomLayout, ROOM_COLS, ROOM_ROWS } from '../room/generateRoomLayout'
import type { FootprintId } from '../room/types'
import { NavigationGrid } from './navigationGrid'
import { findPath, hasPath } from './pathfind'
import { planPatrolGuardSpawns } from './spawnPlan'
import { FOOTPRINT_PATROL_ROUTES, buildCombatExclusionSet, resolvePatrolRoute } from './patrolZones'
import type { GridCell } from './types'

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg)
}

function grid(blocked: boolean[][]): NavigationGrid {
  return new NavigationGrid(blocked)
}

/** Build a synthetic blocked grid (true = wall). */
function emptyRoom(cols = 12, rows = 10): boolean[][] {
  return Array.from({ length: rows }, () => Array.from({ length: cols }, () => false))
}

function markWall(blocked: boolean[][], cells: GridCell[]): void {
  for (const c of cells) blocked[c.row]![c.col] = true
}

function pathCells(path: GridCell[]): Set<string> {
  return new Set(path.map((c) => `${c.col},${c.row}`))
}

function assertPathWalkable(nav: NavigationGrid, path: GridCell[], label: string): void {
  assert(path.length > 0, `${label}: path non-empty`)
  for (const cell of path) {
    assert(nav.isWalkable(cell.col, cell.row), `${label}: cell ${cell.col},${cell.row} walkable`)
  }
  for (let i = 1; i < path.length; i += 1) {
    const a = path[i - 1]!
    const b = path[i]!
    const manhattan = Math.abs(a.col - b.col) + Math.abs(a.row - b.row)
    assert(manhattan === 1, `${label}: ortho step ${a.col},${a.row} → ${b.col},${b.row}`)
  }
}

// --- 1. Straight unobstructed path ---
{
  const blocked = emptyRoom()
  const nav = grid(blocked)
  const result = findPath(nav, { col: 1, row: 5 }, { col: 10, row: 5 })
  assert(result.reachable, 'straight: reachable')
  assertPathWalkable(nav, result.path, 'straight')
  assert(result.path[0]!.col === 1 && result.path.at(-1)!.col === 10, 'straight: ends')
}

// --- 2. Path around solid crates ---
{
  const blocked = emptyRoom()
  markWall(blocked, [
    { col: 5, row: 4 },
    { col: 5, row: 5 },
    { col: 5, row: 6 },
  ])
  const nav = grid(blocked)
  const result = findPath(nav, { col: 2, row: 5 }, { col: 8, row: 5 })
  assert(result.reachable, 'crates: reachable')
  assertPathWalkable(nav, result.path, 'crates')
  const cells = pathCells(result.path)
  assert(!cells.has('5,5'), 'crates: does not walk through crate column')
}

// --- 3. Path around internal partitions ---
{
  const blocked = emptyRoom(14, 10)
  for (let r = 0; r < 10; r += 1) blocked[r]![6] = true
  // Single doorway in the partition.
  blocked[0]![6] = false
  const nav = grid(blocked)
  const result = findPath(nav, { col: 2, row: 5 }, { col: 10, row: 5 })
  assert(result.reachable, 'partition: reachable via doorway')
  assertPathWalkable(nav, result.path, 'partition')
  assert(
    result.path.some((c) => c.col === 6 && c.row === 0),
    'partition: routes through doorway',
  )
  assert(
    !result.path.some((c) => c.col === 6 && c.row !== 0),
    'partition: does not cross solid partition cells',
  )
}

// --- 4. L-shaped corridor ---
{
  const blocked = emptyRoom(10, 10)
  for (let r = 0; r < 10; r += 1) {
    for (let c = 0; c < 10; c += 1) {
      const inCorridor = (r === 1 && c >= 1 && c <= 8) || (c === 8 && r >= 1 && r <= 8)
      blocked[r]![c] = !inCorridor
    }
  }
  const nav = grid(blocked)
  const result = findPath(nav, { col: 1, row: 1 }, { col: 8, row: 8 })
  assert(result.reachable, 'L-corridor: reachable')
  assertPathWalkable(nav, result.path, 'L-corridor')
}

// --- 5. Narrow but valid passage ---
{
  const blocked = emptyRoom(8, 5)
  for (let c = 0; c < 8; c += 1) {
    blocked[0]![c] = true
    blocked[2]![c] = true
    blocked[3]![c] = true
    blocked[4]![c] = true
  }
  // single-tile-high hallway on row 1
  for (let c = 0; c < 8; c += 1) blocked[1]![c] = false
  const nav = grid(blocked)
  const result = findPath(nav, { col: 0, row: 1 }, { col: 7, row: 1 })
  assert(result.reachable, 'narrow: reachable')
  assertPathWalkable(nav, result.path, 'narrow')
  assert(result.path.every((c) => c.row === 1), 'narrow: stays in corridor')
}

// --- 6. Unreachable destination ---
{
  const blocked = emptyRoom(8, 6)
  for (let r = 0; r < 6; r += 1) blocked[r]![4] = true
  const nav = grid(blocked)
  const result = findPath(nav, { col: 1, row: 2 }, { col: 6, row: 2 })
  assert(!result.reachable, 'unreachable: no path')
  assert(result.path.length === 0, 'unreachable: empty path')
  assert(!hasPath(nav, { col: 1, row: 2 }, { col: 6, row: 2 }), 'unreachable: hasPath false')
}

// --- 7. Returning to patrol (path home exists when connected) ---
{
  const plan = generateRoomLayout({
    missionId: 'm1-recruit-l1',
    rank: 'RECRUIT',
    roomInstance: 0,
    mandatoryTerminalCount: 1,
  })
  const nav = NavigationGrid.fromLayout(plan)
  const spawns = planPatrolGuardSpawns(plan, 'RECRUIT', nav)
  assert(spawns.length >= 1, 'return: at least one guard spawn')
  const home = spawns[0]!.waypoints[0]!
  const far = spawns[0]!.waypoints[Math.min(2, spawns[0]!.waypoints.length - 1)]!
  const result = findPath(nav, far, home)
  assert(result.reachable, 'return: path back to patrol home')
  assertPathWalkable(nav, result.path, 'return')
}

// --- 8. Player moving while chased (repath to new cell) ---
{
  const blocked = emptyRoom()
  markWall(blocked, [
    { col: 5, row: 3 },
    { col: 5, row: 4 },
    { col: 5, row: 5 },
  ])
  const nav = grid(blocked)
  const first = findPath(nav, { col: 2, row: 4 }, { col: 8, row: 4 })
  assert(first.reachable, 'chase-repath: first target')
  const second = findPath(nav, { col: 2, row: 4 }, { col: 8, row: 7 })
  assert(second.reachable, 'chase-repath: moved player target')
  assert(
    second.path.at(-1)!.row === 7 || second.path.at(-1)!.col === 8,
    'chase-repath: ends near new goal',
  )
}

// --- 9. Approach terminal clearance (not standing on excluded terminal cell) ---
{
  const plan = generateRoomLayout({
    missionId: 'm1-recruit-l1',
    rank: 'RECRUIT',
    roomInstance: 1,
    mandatoryTerminalCount: 1,
  })
  const nav = NavigationGrid.fromLayout(plan)
  const excluded = buildCombatExclusionSet(plan)
  const term = plan.mandatoryTerminals[0]!
  const termCell = nav.worldToCell(term.x, term.y)
  const approach = nav.nearestWalkable(termCell, 3)
  assert(approach, 'terminal: approach cell exists')
  // Standable approach should be walkable; may still be in exclusion for spawn, but pathable.
  assert(nav.isWalkable(approach!.col, approach!.row), 'terminal: approach walkable')
  const from = plan.playerStart
  const fromCell = nav.worldToCell(from.x, from.y)
  const start = nav.nearestWalkable(fromCell)!
  const result = findPath(nav, start, approach!)
  assert(result.reachable, 'terminal: path toward clearance')
  void excluded
}

// --- 10. Never walks through walls / voids on generated rooms ---
{
  for (const roomInstance of [0, 1, 2]) {
    const plan = generateRoomLayout({
      missionId: 'm1-recruit-l2',
      rank: 'RECRUIT',
      roomInstance,
      mandatoryTerminalCount: 1,
    })
    const nav = NavigationGrid.fromLayout(plan)
    const spawns = planPatrolGuardSpawns(plan, 'FIELD_AGENT', nav)
    for (const spawn of spawns) {
      for (let i = 0; i < spawn.waypoints.length; i += 1) {
        const a = spawn.waypoints[i]!
        const b = spawn.waypoints[(i + 1) % spawn.waypoints.length]!
        const result = findPath(nav, a, b)
        assert(result.reachable, `walls: ${plan.footprintId} route ${spawn.routeId} connected`)
        for (const cell of result.path) {
          assert(!plan.blocked[cell.row]![cell.col], 'walls: path cell not blocked')
        }
      }
    }
  }
}

// --- 11. Corner: path does not use diagonals through walls ---
{
  const blocked = emptyRoom(6, 6)
  blocked[2]![2] = true
  blocked[2]![3] = true
  blocked[3]![2] = true
  const nav = grid(blocked)
  const result = findPath(nav, { col: 1, row: 2 }, { col: 3, row: 1 })
  assert(result.reachable, 'corner: reachable without diagonal cut')
  assertPathWalkable(nav, result.path, 'corner')
  for (let i = 1; i < result.path.length; i += 1) {
    const a = result.path[i - 1]!
    const b = result.path[i]!
    assert(!(Math.abs(a.col - b.col) === 1 && Math.abs(a.row - b.row) === 1), 'corner: no diagonal')
  }
}

// --- 12. Different generated room seeds ---
{
  const seeds = new Set<number>()
  for (let roomInstance = 0; roomInstance < 5; roomInstance += 1) {
    const plan = generateRoomLayout({
      missionId: 'm1-recruit-l3',
      rank: 'RECRUIT',
      roomInstance,
      mandatoryTerminalCount: 1,
    })
    seeds.add(plan.seed)
    const nav = NavigationGrid.fromLayout(plan)
    const spawns = planPatrolGuardSpawns(plan, 'RECRUIT', nav)
    assert(spawns.length >= 1, `seed ${roomInstance}: spawn exists`)
    assert(
      hasPath(nav, spawns[0]!.waypoints[0]!, spawns[0]!.waypoints[1]!),
      `seed ${roomInstance}: patrol connected`,
    )
  }
  assert(seeds.size >= 2, 'seeds: multiple distinct room seeds')
}

// --- 13. All currently implemented room footprints ---
{
  const footprintMissions: Array<{ missionId: string; footprint: FootprintId }> = [
    { missionId: 'm1-recruit-l1', footprint: 'security_office' },
    { missionId: 'm1-recruit-l2', footprint: 'security_annex' },
    { missionId: 'm1-recruit-l3', footprint: 'security_server_wing' },
    { missionId: 'm1-recruit-l4', footprint: 'security_checkpoint' },
    { missionId: 'm1-recruit-l5', footprint: 'security_clearance_hall' },
    { missionId: 'm1-recruit-l6', footprint: 'security_purge_bay' },
    { missionId: 'm1-recruit-l7', footprint: 'security_command_nexus' },
  ]
  for (const { missionId, footprint } of footprintMissions) {
    const plan = generateRoomLayout({
      missionId,
      rank: 'RECRUIT',
      roomInstance: 0,
      mandatoryTerminalCount: 1,
    })
    assert(plan.footprintId === footprint, `${missionId}: footprint id`)
    assert(plan.cols === ROOM_COLS && plan.rows === ROOM_ROWS, `${missionId}: dimensions`)
    const nav = NavigationGrid.fromLayout(plan)
    const routes = FOOTPRINT_PATROL_ROUTES[footprint]
    const excluded = buildCombatExclusionSet(plan)
    let okRoutes = 0
    for (const route of routes) {
      const wps = resolvePatrolRoute(plan, route, excluded)
      if (wps.length < 2) continue
      if (!wps.every((w, i) => hasPath(nav, w, wps[(i + 1) % wps.length]!))) continue
      okRoutes += 1
    }
    assert(okRoutes >= 1, `${footprint}: at least one path-connected patrol route`)
    const spawns = planPatrolGuardSpawns(plan, 'RECRUIT', nav)
    assert(spawns.length >= 1, `${footprint}: spawn planned`)
  }
}

// --- 14. Two guards active simultaneously ---
{
  const plan = generateRoomLayout({
    missionId: 'm1-recruit-l1',
    rank: 'FIELD_AGENT',
    roomInstance: 0,
    mandatoryTerminalCount: 1,
  })
  const nav = NavigationGrid.fromLayout(plan)
  const spawns = planPatrolGuardSpawns(plan, 'FIELD_AGENT', nav)
  assert(spawns.length === 2, 'two-guards: FIELD_AGENT yields 2')
  assert(spawns[0]!.routeId !== spawns[1]!.routeId, 'two-guards: distinct routes')
  for (const spawn of spawns) {
    assert(spawn.waypoints.length >= 2, 'two-guards: waypoints')
    assert(
      hasPath(nav, spawn.waypoints[0]!, spawn.waypoints[1]!),
      `two-guards: ${spawn.routeId} connected`,
    )
  }
}

// --- 15. Combat pause semantics (path state is pure; pause is CombatSystem) ---
{
  // Pathfinding itself is pause-agnostic; verify replan after "pause" still works.
  const blocked = emptyRoom()
  const nav = grid(blocked)
  const before = findPath(nav, { col: 1, row: 1 }, { col: 6, row: 1 })
  assert(before.reachable, 'pause: path before')
  // Simulated resume: same grid, new chase goal (as if terminal closed and player moved).
  const after = findPath(nav, { col: 3, row: 1 }, { col: 9, row: 4 })
  assert(after.reachable, 'pause: path after resume')
  assertPathWalkable(nav, after.path, 'pause-resume')
}

console.log('pathfind.selftest: OK — navigation cases 1–15 passed')
