import type { GridCell } from './types'

/**
 * Reusable enemy type ids across all modules / ranks.
 * PATROL_GUARD (melee), RIFLEMAN (ranged) and SHOCK_ENFORCER (fast melee) are implemented;
 * SECURITY_DRONE / ELITE_SECURITY_GUARD remain reserved extension points.
 */
export type EnemyTypeId =
  | 'PATROL_GUARD'
  | 'RIFLEMAN'
  | 'SHOCK_ENFORCER'
  | 'SECURITY_DRONE'
  | 'ELITE_SECURITY_GUARD'

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
  /** Public path of the 4x6 spritesheet (32x40 frames, guard layout). */
  sheetPath: string
  /** Animation key prefix, e.g. 'guard' -> 'guard-walk-left'. */
  animPrefix: string
  /** melee: damage on contact range. ranged: fires a bullet from attack range. */
  attackKind: 'melee' | 'ranged'
  /** Ranged only: bullet speed (px/s) and lifetime. */
  shotSpeed?: number
  shotLifespanMs?: number
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
  sheetPath: '/assets/enemies/security-patrol-guard.png',
  animPrefix: 'guard',
  attackKind: 'melee',
  frameWidth: 32,
  frameHeight: 40,
  // 272 * 0.24 / 40 — same on-screen height as the player sprite
  displayScale: 1.632,
}

/** Rifleman — keeps its distance and fires aimed shots (derived from the guard art). */
export const RIFLEMAN_CONFIG: EnemyTypeConfig = {
  ...PATROL_GUARD_CONFIG,
  typeId: 'RIFLEMAN',
  displayName: 'Rifleman',
  maxHealth: 4,
  patrolSpeed: 50,
  chaseSpeed: 85,
  detectRangePx: 170,
  chaseRangePx: 250,
  chaseLoseMs: 1800,
  attackRangePx: 190,
  attackCooldownMs: 1400,
  attackDamage: 1,
  alertMs: 450,
  spritesheetKey: 'spy-rifleman-sheet',
  sheetPath: '/assets/enemies/rifleman.png',
  animPrefix: 'rifleman',
  attackKind: 'ranged',
  shotSpeed: 230,
  shotLifespanMs: 1200,
}

/** Shock Enforcer — tough, fast melee charger with a stun baton. */
export const SHOCK_ENFORCER_CONFIG: EnemyTypeConfig = {
  ...PATROL_GUARD_CONFIG,
  typeId: 'SHOCK_ENFORCER',
  displayName: 'Shock Enforcer',
  maxHealth: 7,
  patrolSpeed: 60,
  chaseSpeed: 135,
  detectRangePx: 150,
  chaseRangePx: 230,
  attackRangePx: 44,
  attackCooldownMs: 600,
  alertMs: 250,
  spritesheetKey: 'spy-shock-enforcer-sheet',
  sheetPath: '/assets/enemies/shock-enforcer.png',
  animPrefix: 'enforcer',
}

/** Registry of enemy type configs (reserved ids register here without new classes). */
export const ENEMY_TYPE_REGISTRY: Partial<Record<EnemyTypeId, EnemyTypeConfig>> = {
  PATROL_GUARD: PATROL_GUARD_CONFIG,
  RIFLEMAN: RIFLEMAN_CONFIG,
  SHOCK_ENFORCER: SHOCK_ENFORCER_CONFIG,
}

/** Every implemented enemy sheet, for preloading. */
export const ENEMY_SHEETS: EnemyTypeConfig[] = [PATROL_GUARD_CONFIG, RIFLEMAN_CONFIG, SHOCK_ENFORCER_CONFIG]

export function requireEnemyType(typeId: EnemyTypeId): EnemyTypeConfig {
  const cfg = ENEMY_TYPE_REGISTRY[typeId]
  if (!cfg) {
    throw new Error(`Enemy type not implemented yet: ${typeId}`)
  }
  return cfg
}

/** Durability multiplier by rank (sidearm deals 1 per hit): Recruit x1, Field Agent x1.2, Special Agent x1.6. */
function healthScale(rank: string): number {
  const normalized = rank.trim().toUpperCase()
  if (normalized === 'SPECIAL_AGENT') return 1.6
  if (normalized === 'FIELD_AGENT') return 1.2
  return 1
}

/** Enemy config scaled for a rank (guard: 5 / 6 / 8 health). */
export function enemyConfigForRank(typeId: EnemyTypeId, rank: string): EnemyTypeConfig {
  const base = requireEnemyType(typeId)
  return { ...base, maxHealth: Math.round(base.maxHealth * healthScale(rank)) }
}

/** Guard durability by rank (sidearm deals 1 per hit): Recruit 5, Field Agent 6, Special Agent 8. */
export function patrolGuardConfigForRank(rank: string): EnemyTypeConfig {
  return enemyConfigForRank('PATROL_GUARD', rank)
}

/**
 * Enemy roster per rank (Levels 1-6; Level 7 is boss-only):
 * Recruit 1 guard; Field Agent guard + rifleman; Special Agent rifleman + shock enforcer.
 */
export function enemyRosterForRank(rank: string): EnemyTypeId[] {
  const normalized = rank.trim().toUpperCase()
  if (normalized === 'SPECIAL_AGENT') return ['RIFLEMAN', 'SHOCK_ENFORCER']
  if (normalized === 'FIELD_AGENT') return ['PATROL_GUARD', 'RIFLEMAN']
  return ['PATROL_GUARD']
}

/** Rank → how many enemies to place (kept for existing callers / self-tests). */
export function patrolGuardCountForRank(rank: string): number {
  return enemyRosterForRank(rank).length
}

export interface PlannedGuardSpawn {
  typeId: EnemyTypeId
  config: EnemyTypeConfig
  routeId: string
  waypoints: GridCell[]
}
