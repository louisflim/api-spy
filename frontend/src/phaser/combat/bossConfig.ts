import type { RoomLayoutPlan } from '../room/types'
import { NavigationGrid } from './navigationGrid'
import { buildCombatExclusionSet } from './patrolZones'
import type { GridCell } from './types'

/**
 * Level 7 boss tuning. One reusable boss behaviour; ranks only change durability and
 * aggression (proposal: "one boss behavior template per module", rank = enemy tuning).
 */
export interface BossConfig {
  displayName: string
  maxHealth: number
  /** Movement while the shield is up (holds its post near home). */
  guardSpeed: number
  /** Movement once the shield is down (chases the agent). */
  enragedSpeed: number
  /** Agent must be within this range for the boss to open fire. */
  fireRangePx: number
  volleyIntervalMs: number
  enragedVolleyIntervalMs: number
  /** Orbs per volley (fanned around the aim direction). */
  volleySize: number
  volleySpreadRad: number
  orbSpeed: number
  orbLifespanMs: number
  orbDamage: number
  contactRangePx: number
  contactCooldownMs: number
  contactDamage: number
  /** How far (px) the shielded boss may drift from its post. */
  leashPx: number
  displayScale: number
  tint: number
}

const BASE: BossConfig = {
  displayName: 'Nexus Warden',
  maxHealth: 24,
  guardSpeed: 45,
  enragedSpeed: 78,
  fireRangePx: 260,
  volleyIntervalMs: 2600,
  enragedVolleyIntervalMs: 1900,
  volleySize: 3,
  volleySpreadRad: 0.32,
  orbSpeed: 165,
  orbLifespanMs: 2200,
  orbDamage: 1,
  contactRangePx: 44,
  contactCooldownMs: 1100,
  contactDamage: 1,
  leashPx: 70,
  displayScale: 2.4,
  tint: 0xff5a7a,
}

export function bossConfigForRank(rank: string): BossConfig {
  const normalized = rank.trim().toUpperCase()
  if (normalized === 'SPECIAL_AGENT') {
    return { ...BASE, maxHealth: 40, enragedSpeed: 92, volleySize: 5, volleySpreadRad: 0.5, enragedVolleyIntervalMs: 1450 }
  }
  if (normalized === 'FIELD_AGENT') {
    return { ...BASE, maxHealth: 32, enragedSpeed: 85, volleySize: 4, volleySpreadRad: 0.42, enragedVolleyIntervalMs: 1650 }
  }
  return BASE
}

/**
 * Boss post: the walkable, non-excluded cell farthest (by walking distance) from the
 * agent's entrance, so the agent can reach terminals before engaging. Deterministic per
 * layout; never on terminals, the spawn or the exit clearance.
 */
export function planBossSpawn(
  layout: RoomLayoutPlan,
  nav: NavigationGrid = NavigationGrid.fromLayout(layout),
): GridCell | null {
  const excluded = buildCombatExclusionSet(layout)
  const start = nav.nearestWalkable(nav.worldToCell(layout.playerStart.x, layout.playerStart.y))
  if (!start) return null

  const dist = new Map<string, number>()
  const key = (c: GridCell) => `${c.col},${c.row}`
  const queue: GridCell[] = [start]
  dist.set(key(start), 0)
  let best: GridCell | null = null
  let bestDist = -1
  for (let i = 0; i < queue.length; i += 1) {
    const cell = queue[i]!
    const d = dist.get(key(cell))!
    if (!excluded.has(key(cell)) && openNeighbours(nav, cell) >= 3 && d > bestDist) {
      best = cell
      bestDist = d
    }
    for (const [dc, dr] of [[1, 0], [-1, 0], [0, 1], [0, -1]] as const) {
      const next = { col: cell.col + dc, row: cell.row + dr }
      if (!nav.isWalkable(next.col, next.row) || dist.has(key(next))) continue
      dist.set(key(next), d + 1)
      queue.push(next)
    }
  }
  return best
}

/** Prefer open floor (not a 1-wide corridor) so the large boss sprite does not wedge. */
function openNeighbours(nav: NavigationGrid, cell: GridCell): number {
  let n = 0
  for (const [dc, dr] of [[1, 0], [-1, 0], [0, 1], [0, -1]] as const) {
    if (nav.isWalkable(cell.col + dc, cell.row + dr)) n += 1
  }
  return n
}
