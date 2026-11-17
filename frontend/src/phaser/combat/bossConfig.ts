import type { RoomLayoutPlan } from '../room/types'
import { NavigationGrid } from './navigationGrid'
import { buildCombatExclusionSet } from './patrolZones'
import type { GridCell } from './types'

/**
 * Level 7 boss tuning. One reusable boss behaviour; ranks only change durability and
 * aggression (proposal: "one boss behavior template per module", rank = enemy tuning).
 */
export type BossPattern = 'fan' | 'ring' | 'spiral'

export interface BossConfig {
  displayName: string
  /** Spritesheet (guard layout, 4x6 frames of 32x40) derived from the guard art. */
  sheetKey: string
  sheetPath: string
  animPrefix: string
  /** fan: aimed spread; ring: burst in every direction; spiral: rotating stream. */
  pattern: BossPattern
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
  sheetKey: 'spy-boss-nexus-warden',
  sheetPath: '/assets/bosses/boss-nexus-warden.png',
  animPrefix: 'warden',
  pattern: 'fan',
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
  tint: 0xffffff,
}

/** Module 2 boss: slow, armoured, fires rings of orbs in every direction. */
const PACKET_JUGGERNAUT: BossConfig = {
  ...BASE,
  displayName: 'Packet Juggernaut',
  sheetKey: 'spy-boss-packet-juggernaut',
  sheetPath: '/assets/bosses/boss-packet-juggernaut.png',
  animPrefix: 'juggernaut',
  pattern: 'ring',
  maxHealth: 30,
  guardSpeed: 35,
  enragedSpeed: 60,
  fireRangePx: 300,
  volleyIntervalMs: 3000,
  enragedVolleyIntervalMs: 2300,
  volleySize: 8,
  orbSpeed: 140,
  orbLifespanMs: 2600,
  contactCooldownMs: 1300,
}

/** Module 3 boss: agile, fires a rotating spiral stream. */
const CIPHER_QUEEN: BossConfig = {
  ...BASE,
  displayName: 'Cipher Queen',
  sheetKey: 'spy-boss-cipher-queen',
  sheetPath: '/assets/bosses/boss-cipher-queen.png',
  animPrefix: 'cipher',
  pattern: 'spiral',
  maxHealth: 28,
  guardSpeed: 55,
  enragedSpeed: 92,
  fireRangePx: 280,
  volleyIntervalMs: 1400,
  enragedVolleyIntervalMs: 700,
  volleySize: 3,
  orbSpeed: 175,
  orbLifespanMs: 2000,
}

const BY_MODULE: Record<number, BossConfig> = { 1: BASE, 2: PACKET_JUGGERNAUT, 3: CIPHER_QUEEN }

/** Every boss config (for preloading sheets). */
export const BOSS_CONFIGS: BossConfig[] = Object.values(BY_MODULE)

/** One boss per module; ranks change durability and aggression only (proposal). */
export function bossConfigFor(module: number, rank: string): BossConfig {
  const base = BY_MODULE[module] ?? BASE
  const normalized = rank.trim().toUpperCase()
  if (normalized === 'SPECIAL_AGENT') {
    return {
      ...base,
      maxHealth: Math.round(base.maxHealth * 1.65),
      enragedSpeed: base.enragedSpeed + 14,
      volleySize: base.volleySize + 2,
      volleySpreadRad: base.volleySpreadRad + 0.18,
      enragedVolleyIntervalMs: Math.round(base.enragedVolleyIntervalMs * 0.76),
    }
  }
  if (normalized === 'FIELD_AGENT') {
    return {
      ...base,
      maxHealth: Math.round(base.maxHealth * 1.33),
      enragedSpeed: base.enragedSpeed + 7,
      volleySize: base.volleySize + 1,
      volleySpreadRad: base.volleySpreadRad + 0.1,
      enragedVolleyIntervalMs: Math.round(base.enragedVolleyIntervalMs * 0.87),
    }
  }
  return base
}

/** Module 1 boss for a rank (kept for existing callers). */
export function bossConfigForRank(rank: string): BossConfig {
  return bossConfigFor(1, rank)
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
