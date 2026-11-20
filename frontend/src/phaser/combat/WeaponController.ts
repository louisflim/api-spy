import Phaser from 'phaser'
import { requireWeapon, shotDirections, STARTER_SIDEARM } from './weapons'
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
 * Semi-automatic guns fire once per click / Space press; automatic guns keep firing while held.
 * Shotguns spray several pellets per shot. Every gun has unlimited ammunition.
 */
export class WeaponController {
  private readonly scene: Phaser.Scene
  private readonly projectiles: ProjectileManager
  private weapon: WeaponDefinition
  private lastShotAt = 0
  private fireKey!: Phaser.Input.Keyboard.Key
  private pointerDown = false
  /** A click that has not fired yet (semi-automatic guns fire once per click). */
  private pointerPressed = false
  private enabled = true
  private paused = false
  /** Direction of the latest shot and when it happened (the held gun points there briefly). */
  private lastAim: { x: number; y: number; at: number } | null = null
  /** Rounds in the current continuous burst, and when an overheated gun can fire again. */
  private burst = 0
  private overheatedUntil = 0

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

  /** Equips a gun whose stats already include EXP upgrades. */
  equipDefinition(weapon: WeaponDefinition): void {
    this.weapon = weapon
  }

  getWeapon(): WeaponDefinition {
    return this.weapon
  }

  /** True while an automatic gun is cooling down after a long burst. */
  isOverheated(nowMs: number): boolean {
    return nowMs < this.overheatedUntil
  }

  /** Latest shot direction if it was fired within `withinMs` (combat clock). */
  recentAim(nowMs: number, withinMs: number): { x: number; y: number } | null {
    if (!this.lastAim || nowMs - this.lastAim.at > withinMs) return null
    return { x: this.lastAim.x, y: this.lastAim.y }
  }

  setEnabled(enabled: boolean): void {
    this.enabled = enabled
  }

  setPaused(paused: boolean): void {
    this.paused = paused
    if (paused) this.pointerPressed = false
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
    this.pointerPressed = true
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
    const auto = (this.weapon.fireMode ?? 'auto') === 'auto'
    const spaceFire = this.fireKey
      ? auto
        ? this.fireKey.isDown
        : Phaser.Input.Keyboard.JustDown(this.fireKey)
      : false
    const mouseFire = auto ? this.pointerDown : this.pointerPressed
    if (!spaceFire && !mouseFire) return false
    // Anti-spam: a click during the cooldown is dropped (not queued), so mashing never fires
    // faster than the gun's rate; overheated guns cannot fire at all.
    if (nowMs - this.lastShotAt < this.weapon.cooldownMs || nowMs < this.overheatedUntil) {
      this.pointerPressed = false
      return false
    }
    if (mouseFire) this.pointerPressed = false
    // Overheat tracking for automatic guns: a pause longer than a few shots resets the burst.
    if (nowMs - this.lastShotAt > this.weapon.cooldownMs * 3) this.burst = 0
    this.burst += 1
    if (this.weapon.heatShots && this.burst >= this.weapon.heatShots) {
      this.overheatedUntil = nowMs + (this.weapon.overheatMs ?? 1000)
      this.burst = 0
    }

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
    for (const d of shotDirections(this.weapon, dirX, dirY, () => Math.random() * 2 - 1)) {
      this.projectiles.spawn({
        x: muzzleX,
        y: muzzleY,
        vx: d.x * this.weapon.projectileSpeed,
        vy: d.y * this.weapon.projectileSpeed,
        weapon: this.weapon,
      })
    }
    this.lastShotAt = nowMs
    this.lastAim = { x: dirX, y: dirY, at: nowMs }
    return true
  }
}
