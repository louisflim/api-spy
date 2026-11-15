import Phaser from 'phaser'
import { requireWeapon, STARTER_SIDEARM } from './weapons'
import type { ProjectileManager } from './ProjectileManager'
import type { WeaponDefinition } from './types'

type Facing = 'down' | 'left' | 'right' | 'up'

const FACING_VECTOR: Record<Facing, { x: number; y: number }> = {
  down: { x: 0, y: 1 },
  up: { x: 0, y: -1 },
  left: { x: -1, y: 0 },
  right: { x: 1, y: 0 },
}

/**
 * Desktop-friendly aim/fire with mouse aim + Space fallback along facing.
 * Weapon id is swappable via {@link equip} for future guns.
 */
export class WeaponController {
  private readonly scene: Phaser.Scene
  private readonly projectiles: ProjectileManager
  private weapon: WeaponDefinition
  private lastShotAt = 0
  private fireKey!: Phaser.Input.Keyboard.Key
  private pointerDown = false
  private enabled = true
  private paused = false

  constructor(
    scene: Phaser.Scene,
    projectiles: ProjectileManager,
    weaponId: string = STARTER_SIDEARM.id,
  ) {
    this.scene = scene
    this.projectiles = projectiles
    this.weapon = requireWeapon(weaponId)
  }

  equip(weaponId: string): void {
    this.weapon = requireWeapon(weaponId)
  }

  getWeapon(): WeaponDefinition {
    return this.weapon
  }

  setEnabled(enabled: boolean): void {
    this.enabled = enabled
  }

  setPaused(paused: boolean): void {
    this.paused = paused
  }

  bindInput(): void {
    this.fireKey = this.scene.input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.SPACE)
    this.scene.input.on('pointerdown', this.onPointerDown)
    this.scene.input.on('pointerup', this.onPointerUp)
  }

  unbindInput(): void {
    this.scene.input.off('pointerdown', this.onPointerDown)
    this.scene.input.off('pointerup', this.onPointerUp)
  }

  private onPointerDown = (pointer: Phaser.Input.Pointer): void => {
    // Ignore clicks that originate outside the game canvas (React UI / terminal).
    if (!pointer.leftButtonDown()) return
    if (!pointer.downElement || pointer.downElement.tagName !== 'CANVAS') return
    this.pointerDown = true
  }

  private onPointerUp = (): void => {
    this.pointerDown = false
  }

  tryFire(
    originX: number,
    originY: number,
    facing: Facing,
    nowMs: number,
  ): boolean {
    if (!this.enabled || this.paused) return false
    const spaceFire = this.fireKey && Phaser.Input.Keyboard.JustDown(this.fireKey)
    const mouseFire = this.pointerDown
    if (!spaceFire && !mouseFire) return false
    if (nowMs - this.lastShotAt < this.weapon.cooldownMs) return false

    let dirX: number
    let dirY: number
    if (mouseFire) {
      const pointer = this.scene.input.activePointer
      const world = this.scene.cameras.main.getWorldPoint(pointer.x, pointer.y)
      const aimX = world.x - originX
      const aimY = world.y - (originY - 12)
      const len = Math.hypot(aimX, aimY) || 1
      dirX = aimX / len
      dirY = aimY / len
    } else {
      const dir = FACING_VECTOR[facing]
      dirX = dir.x
      dirY = dir.y
    }

    const muzzleX = originX + dirX * 18
    const muzzleY = originY - 12 + dirY * 10
    this.projectiles.spawn({
      x: muzzleX,
      y: muzzleY,
      vx: dirX * this.weapon.projectileSpeed,
      vy: dirY * this.weapon.projectileSpeed,
      weapon: this.weapon,
    })
    this.lastShotAt = nowMs
    return true
  }
}
