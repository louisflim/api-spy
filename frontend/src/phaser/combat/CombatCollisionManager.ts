import Phaser from 'phaser'
import { COMBAT_TEXTURE_KEYS } from './combatArt'
import type { DamageableEnemy } from './types'
import type { ProjectileManager } from './ProjectileManager'

/**
 * Projectile ↔ wall/prop solids (physics) and projectile ↔ enemy hits (hurtboxes).
 * Terminals/doors are solids: bolts despawn on contact without enemy damage.
 * Enemy hits test the bolt against each enemy's body-sized hurtbox every frame,
 * not the small feet-level physics body used for walking.
 */
export class CombatCollisionManager {
  private readonly scene: Phaser.Scene
  private readonly projectiles: ProjectileManager
  private wallCollider: Phaser.Physics.Arcade.Collider | null = null
  private enemies: DamageableEnemy[] = []
  private readonly scratch = new Phaser.Geom.Rectangle()

  constructor(scene: Phaser.Scene, projectiles: ProjectileManager) {
    this.scene = scene
    this.projectiles = projectiles
  }

  bindWalls(solids: Phaser.Physics.Arcade.StaticGroup): void {
    this.wallCollider?.destroy()
    this.wallCollider = this.scene.physics.add.collider(
      this.projectiles.getGroup(),
      solids,
      (a, b) => {
        const group = this.projectiles.getGroup()
        const bolt = (group.contains(a as Phaser.GameObjects.GameObject) ? a : b) as Phaser.Physics.Arcade.Image
        if (!bolt.active) return
        this.spawnImpact(bolt.x, bolt.y)
        this.projectiles.despawn(bolt)
      },
    )
  }

  bindEnemies(guards: DamageableEnemy[]): void {
    this.enemies = [...guards]
  }

  /** Call once per (unpaused) frame after projectiles move. */
  update(): void {
    if (this.enemies.length === 0) return
    for (const child of this.projectiles.getGroup().getChildren()) {
      const bolt = child as Phaser.Physics.Arcade.Image
      if (!bolt.active) continue
      for (const enemy of this.enemies) {
        if (enemy.isDefeated() || !enemy.sprite.active || !enemy.sprite.visible) continue
        if (!enemy.hurtbox(this.scratch).contains(bolt.x, bolt.y)) continue
        const damage = Number(bolt.getData('damage') ?? 1)
        this.spawnImpact(bolt.x, bolt.y)
        this.projectiles.despawn(bolt)
        enemy.applyDamage(damage, bolt.x, bolt.y)
        break
      }
    }
  }

  private spawnImpact(x: number, y: number): void {
    const spark = this.scene.add
      .image(x, y, COMBAT_TEXTURE_KEYS.impact)
      .setDepth(55)
      .setAlpha(1)
    this.scene.tweens.add({
      targets: spark,
      alpha: 0,
      scale: 1.6,
      duration: 160,
      onComplete: () => spark.destroy(),
    })
  }

  destroy(): void {
    this.wallCollider?.destroy()
    this.wallCollider = null
    this.enemies = []
  }
}
