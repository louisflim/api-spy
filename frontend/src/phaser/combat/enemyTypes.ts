import type { GridCell } from './types'

/**
 * Reusable enemy type ids across all modules / ranks.
 * Phase 1 implements PATROL_GUARD only — DRONE / ELITE are reserved extension points.
 */
export type EnemyTypeId = 'PATROL_GUARD' | 'SECURITY_DRONE' | 'ELITE_SECURITY_GUARD'

export type EnemyAnimSetId = 'patrol_guard_v1'

export interface EnemyTypeConfig {
  typeId: EnemyTypeId
  displayName: string
  maxHealth: number
  patrolSpeed: number
  chaseSpeed: number
  detectRangePx: number
  chaseRangePx: number
  /** Lose chase if player is beyond chase range / no LoS for this long. */
  chaseLoseMs: number
  attackRangePx: number
  attackCooldownMs: number
  attackDamage: number
  alertMs: number
  animSetId: EnemyAnimSetId
  /** Texture key for the dedicated spritesheet. */
  spritesheetKey: string
  frameWidth: number
  frameHeight: number
  /**
   * On-screen scale. Patrol guards match the Rookie Detective display size
   * (272px frame × 0.24 scale ≈ 65px).
   */
  displayScale: number
}

/** Security Patrol Guard — only fully authored enemy for Phase 1. */
export const PATROL_GUARD_CONFIG: EnemyTypeConfig = {
  typeId: 'PATROL_GUARD',
  displayName: 'Security Patrol Guard',
  // Recruit baseline: 5 sidearm hits. Higher ranks scale via patrolGuardConfigForRank.
  maxHealth: 5,
  patrolSpeed: 55,
  chaseSpeed: 105,
  detectRangePx: 130,
  chaseRangePx: 200,
  chaseLoseMs: 1600,
  attackRangePx: 42,
  attackCooldownMs: 700,
  attackDamage: 1,
  alertMs: 380,
  animSetId: 'patrol_guard_v1',
  spritesheetKey: 'spy-patrol-guard-sheet',
  frameWidth: 32,
  frameHeight: 40,
  // 272 * 0.24 / 40 — same on-screen height as the player sprite
  displayScale: 1.632,
}

/**
 * Registry of enemy type configs. Future SECURITY_DRONE / ELITE_SECURITY_GUARD
 * entries register here without new per-mission classes.
 */
export const ENEMY_TYPE_REGISTRY: Partial<Record<EnemyTypeId, EnemyTypeConfig>> = {
  PATROL_GUARD: PATROL_GUARD_CONFIG,
}

export function requireEnemyType(typeId: EnemyTypeId): EnemyTypeConfig {
  const cfg = ENEMY_TYPE_REGISTRY[typeId]
  if (!cfg) {
    throw new Error(`Enemy type not implemented yet: ${typeId}`)
  }
  return cfg
}

/** Guard durability by rank (sidearm deals 1 per hit): Recruit 5, Field Agent 6, Special Agent 8. */
export function patrolGuardConfigForRank(rank: string): EnemyTypeConfig {
  const normalized = rank.trim().toUpperCase()
  if (normalized === 'SPECIAL_AGENT') return { ...PATROL_GUARD_CONFIG, maxHealth: 8 }
  if (normalized === 'FIELD_AGENT') return { ...PATROL_GUARD_CONFIG, maxHealth: 6 }
  return PATROL_GUARD_CONFIG
}

/** Rank → how many PATROL_GUARD instances to place (Phase 1). */
export function patrolGuardCountForRank(rank: string): number {
  const normalized = rank.trim().toUpperCase()
  if (normalized === 'SPECIAL_AGENT') return 2
  if (normalized === 'FIELD_AGENT') return 2
  return 1 // RECRUIT / default — one guard for foundation testing
}

export interface PlannedGuardSpawn {
  typeId: EnemyTypeId
  config: EnemyTypeConfig
  routeId: string
  waypoints: GridCell[]
}
