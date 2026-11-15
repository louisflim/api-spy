import Phaser from 'phaser'
import { COMBAT_TEXTURE_KEYS } from './combatArt'
import type { PatrolGuard } from './PatrolGuard'
import type { ProjectileManager } from './ProjectileManager'

/**
 * Projectile ↔ wall/prop solids and projectile ↔ enemy hits.
 * Terminals/doors are solids: bolts despawn on contact without enemy damage.
 */
export class CombatCollisionManager {
  private readonly scene: Phaser.Scene
  private readonly projectiles: ProjectileManager
  private wallCollider: Phaser.Physics.Arcade.Collider | null = null
  private enemyOverlaps: Phaser.Physics.Arcade.Collider[] = []

  constructor(scene: Phaser.Scene, projectiles: ProjectileManager) {
    this.scene = scene
    this.projectiles = projectiles
  }

  bindWalls(solids: Phaser.Physics.Arcade.StaticGroup): void {
    this.wallCollider?.destroy()
    this.wallCollider = this.scene.physics.add.collider(
      this.projectiles.getGroup(),
      solids,
      (proj) => {
        const bolt = proj as Phaser.Physics.Arcade.Image
        this.spawnImpact(bolt.x, bolt.y)
        this.projectiles.despawn(bolt)
      },
    )
  }

  bindEnemies(guards: PatrolGuard[]): void {
    for (const overlap of this.enemyOverlaps) overlap.destroy()
    this.enemyOverlaps = []
    for (const guard of guards) {
      const overlap = this.scene.physics.add.overlap(
        this.projectiles.getGroup(),
        guard.sprite,
        (proj) => {
          if (guard.isDefeated()) return
          const bolt = proj as Phaser.Physics.Arcade.Image
          const damage = Number(bolt.getData('damage') ?? 1)
          this.spawnImpact(bolt.x, bolt.y)
          this.projectiles.despawn(bolt)
          guard.applyDamage(damage)
        },
      )
      this.enemyOverlaps.push(overlap)
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
    for (const overlap of this.enemyOverlaps) overlap.destroy()
    this.enemyOverlaps = []
  }
}
