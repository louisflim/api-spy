/** Shared combat contracts — weapons and enemies are data-driven. */

export type EnemyAiState = 'PATROL' | 'ALERT' | 'CHASE' | 'ATTACK' | 'RETURN'

export interface WeaponDefinition {
  id: string
  displayName: string
  /** Milliseconds between shots. */
  cooldownMs: number
  projectileSpeed: number
  damage: number
  projectileKey: string
  projectileLifespanMs: number
  /** Visual scale of the projectile sprite. */
  projectileScale: number
}

export interface GridCell {
  col: number
  row: number
}

export interface PatrolRoute {
  id: string
  /** Preferred waypoint cells; may be adjusted to nearest walkable after layout. */
  waypoints: GridCell[]
}

/** Player-side combat constants (hearts / invuln). Enemy stats live in EnemyTypeConfig. */
export interface CombatTuning {
  maxHearts: number
  invulnerabilityMs: number
}
