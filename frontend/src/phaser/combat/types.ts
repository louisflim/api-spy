import type Phaser from 'phaser'
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

/** Anything player bolts can hit (patrol guards, the Level 7 boss). */
export interface DamageableEnemy {
  readonly sprite: Phaser.Physics.Arcade.Sprite
  isDefeated(): boolean
  /** Returns true when this hit defeated the enemy. */
  applyDamage(amount: number, hitX?: number, hitY?: number): boolean
  /** Body-sized area that bullets must touch (not the feet-level movement body). */
  hurtbox(out?: Phaser.Geom.Rectangle): Phaser.Geom.Rectangle
}
