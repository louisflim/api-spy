import Phaser from 'phaser'
import type { WeaponDefinition } from './types'

export interface ProjectileSpawnRequest {
  x: number
  y: number
  vx: number
  vy: number
  weapon: WeaponDefinition
}

/**
 * Owns projectile sprites. Pausing freezes velocities so terminal UI can halt combat.
 * Extensible: any WeaponDefinition can spawn through the same pool.
 */
export class ProjectileManager {
  private readonly scene: Phaser.Scene
  private readonly group: Phaser.Physics.Arcade.Group
  private paused = false
  private readonly pausedVelocities = new Map<number, { x: number; y: number }>()

  constructor(scene: Phaser.Scene) {
    this.scene = scene
    this.group = scene.physics.add.group({
      classType: Phaser.Physics.Arcade.Image,
      maxSize: 24,
      runChildUpdate: false,
    })
  }

  getGroup(): Phaser.Physics.Arcade.Group {
    return this.group
  }

  private forEachProjectile(fn: (sprite: Phaser.Physics.Arcade.Image) => void): void {
    for (const child of this.group.getChildren()) {
      fn(child as Phaser.Physics.Arcade.Image)
    }
  }

  setPaused(paused: boolean): void {
    if (this.paused === paused) return
    this.paused = paused
    const now = this.scene.time.now
    this.forEachProjectile((sprite) => {
      if (!sprite.active) return
      const body = sprite.body as Phaser.Physics.Arcade.Body | null
      if (!body) return
      if (paused) {
        this.pausedVelocities.set(sprite.getData('pid') as number, {
          x: body.velocity.x,
          y: body.velocity.y,
        })
        body.setVelocity(0, 0)
        // Freeze remaining lifespan while the terminal is open.
        sprite.setData('remainingLife', Math.max(0, (sprite.getData('expiresAt') as number) - now))
      } else {
        const stored = this.pausedVelocities.get(sprite.getData('pid') as number)
        if (stored) body.setVelocity(stored.x, stored.y)
        this.pausedVelocities.delete(sprite.getData('pid') as number)
        const remaining = Number(sprite.getData('remainingLife') ?? 0)
        sprite.setData('expiresAt', now + remaining)
      }
    })
  }

  spawn(req: ProjectileSpawnRequest): Phaser.Physics.Arcade.Image | null {
    const sprite = this.group.get(req.x, req.y, req.weapon.projectileKey) as
      | Phaser.Physics.Arcade.Image
      | null
    if (!sprite) return null
    sprite.setActive(true)
    sprite.setVisible(true)
    sprite.setScale(req.weapon.projectileScale)
    sprite.setDepth(30)
    sprite.setData('damage', req.weapon.damage)
    sprite.setData('weaponId', req.weapon.id)
    sprite.setData('pid', Phaser.Math.RND.integer())
    sprite.setData('expiresAt', this.scene.time.now + req.weapon.projectileLifespanMs)
    const body = sprite.body as Phaser.Physics.Arcade.Body
    body.setAllowGravity(false)
    body.setSize(8, 4)
    if (this.paused) {
      this.pausedVelocities.set(sprite.getData('pid') as number, { x: req.vx, y: req.vy })
      body.setVelocity(0, 0)
    } else {
      body.setVelocity(req.vx, req.vy)
    }
    const angle = Math.atan2(req.vy, req.vx)
    sprite.setRotation(angle)
    return sprite
  }

  update(nowMs: number): void {
    if (this.paused) return
    this.forEachProjectile((sprite) => {
      if (!sprite.active) return
      const expires = sprite.getData('expiresAt') as number
      if (nowMs >= expires) {
        this.despawn(sprite)
      }
    })
  }

  despawn(sprite: Phaser.Physics.Arcade.Image): void {
    this.pausedVelocities.delete(sprite.getData('pid') as number)
    sprite.setActive(false)
    sprite.setVisible(false)
    const body = sprite.body as Phaser.Physics.Arcade.Body | null
    body?.setVelocity(0, 0)
  }

  clearAll(): void {
    this.pausedVelocities.clear()
    this.forEachProjectile((sprite) => this.despawn(sprite))
  }

  destroy(): void {
    this.clearAll()
    this.group.destroy(true)
  }
}
