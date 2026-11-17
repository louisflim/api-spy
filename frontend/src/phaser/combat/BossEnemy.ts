import Phaser from 'phaser'
import type { BossConfig } from './bossConfig'
import { COMBAT_TEXTURE_KEYS } from './combatArt'
import { PATROL_GUARD_CONFIG } from './enemyTypes'
import { NavigationGrid } from './navigationGrid'
import { GUARD_HURTBOX, spriteHurtbox } from './hurtbox'
import { findPath, pathFromCurrent } from './pathfind'
import type { ProjectileManager } from './ProjectileManager'
import type { DamageableEnemy, GridCell, WeaponDefinition } from './types'

type Facing = 'down' | 'left' | 'right' | 'up'

export interface BossSnapshot {
  hp: number
  maxHp: number
  shielded: boolean
  defeated: boolean
}

const REPATH_MS = 450

/**
 * Level 7 boss ("Nexus Warden"). Shield is driven from the server's mandatory-device
 * state: while it is up the boss holds its post, fires volleys at agents who come close
 * and ignores damage. Once every API device is operated the shield drops, the boss
 * becomes vulnerable and starts chasing. Combat never changes any API state.
 */
export class BossEnemy implements DamageableEnemy {
  readonly sprite: Phaser.Physics.Arcade.Sprite
  private readonly scene: Phaser.Scene
  private readonly cfg: BossConfig
  private readonly nav: NavigationGrid
  private readonly orbs: ProjectileManager
  private readonly orbWeapon: WeaponDefinition
  private readonly blocked: boolean[][]
  private readonly home: { x: number; y: number }
  private readonly shieldRing: Phaser.GameObjects.Arc
  private readonly shieldTween: Phaser.Tweens.Tween
  private readonly bar: Phaser.GameObjects.Graphics
  private readonly nameTag: Phaser.GameObjects.Text
  private hp: number
  private shielded = true
  private defeated = false
  private paused = false
  private facing: Facing = 'down'
  private lastVolleyAt = 0
  private lastContactAt = 0
  private wanderTarget: { x: number; y: number } | null = null
  private wanderUntil = 0
  private path: GridCell[] = []
  private pathIndex = 0
  private lastPathAt = 0
  private stuckSince = 0
  /** Scene time until which the shield-break effect may still show the ring. */
  private shatterUntil = 0
  private lastX = 0
  private lastY = 0
  private readonly onChange: (s: BossSnapshot) => void
  private readonly onDefeated: () => void

  constructor(opts: {
    scene: Phaser.Scene
    cell: GridCell
    config: BossConfig
    nav: NavigationGrid
    blocked: boolean[][]
    orbs: ProjectileManager
    shielded: boolean
    onChange: (s: BossSnapshot) => void
    onDefeated: () => void
  }) {
    this.scene = opts.scene
    this.cfg = opts.config
    this.nav = opts.nav
    this.blocked = opts.blocked
    this.orbs = opts.orbs
    this.onChange = opts.onChange
    this.onDefeated = opts.onDefeated
    this.hp = opts.config.maxHealth
    this.shielded = opts.shielded
    this.home = opts.nav.cellToWorld(opts.cell)
    this.orbWeapon = {
      id: 'boss_volley',
      displayName: 'Warden volley',
      cooldownMs: opts.config.volleyIntervalMs,
      projectileSpeed: opts.config.orbSpeed,
      damage: opts.config.orbDamage,
      projectileKey: COMBAT_TEXTURE_KEYS.bossOrb,
      projectileLifespanMs: opts.config.orbLifespanMs,
      projectileScale: 1.2,
    }

    const { x, y } = this.home
    this.sprite = this.scene.physics.add.sprite(x, y, PATROL_GUARD_CONFIG.spritesheetKey, 0)
    this.sprite.setScale(this.cfg.displayScale)
    this.sprite.setOrigin(0.5, 0.85)
    this.sprite.setTint(this.cfg.tint)
    this.sprite.setCollideWorldBounds(true)
    this.sprite.setData('kind', 'BOSS')
    const scale = this.cfg.displayScale
    // World footprint stays guard-sized so 4-neighbour paths clear props; the sprite is only visual.
    const bodyW = 16 / scale
    const bodyH = 12 / scale
    const body = this.sprite.body as Phaser.Physics.Arcade.Body
    body.setSize(bodyW, bodyH)
    body.setOffset((PATROL_GUARD_CONFIG.frameWidth - bodyW) / 2, PATROL_GUARD_CONFIG.frameHeight - bodyH - 2)
    body.setAllowGravity(false)
    body.setImmovable(true)
    this.sprite.anims.play('guard-idle-down')

    this.shieldRing = this.scene.add
      .circle(x, y - 30, 42, 0x37e0ff, 0.14)
      .setStrokeStyle(3, 0x7af5ff, 0.9)
    this.shieldTween = this.scene.tweens.add({
      targets: this.shieldRing,
      scale: { from: 0.94, to: 1.06 },
      alpha: { from: 0.75, to: 1 },
      duration: 650,
      yoyo: true,
      repeat: -1,
    })
    this.bar = this.scene.add.graphics()
    this.nameTag = this.scene.add
      .text(x, y, this.cfg.displayName, {
        fontFamily: 'Share Tech Mono, Consolas, monospace',
        fontSize: '11px',
        color: '#ffd0da',
        backgroundColor: '#1a0610cc',
        padding: { x: 4, y: 1 },
      })
      .setOrigin(0.5, 1)
    this.syncOverlays()
    this.applyShieldVisual(false)
    this.emit()
  }

  isDefeated(): boolean {
    return this.defeated
  }

  hurtbox(out?: Phaser.Geom.Rectangle): Phaser.Geom.Rectangle {
    return spriteHurtbox(this.sprite, GUARD_HURTBOX, out)
  }

  snapshot(): BossSnapshot {
    return { hp: Math.max(0, this.hp), maxHp: this.cfg.maxHealth, shielded: this.shielded, defeated: this.defeated }
  }

  setPaused(paused: boolean): void {
    this.paused = paused
    if (paused) {
      this.stop()
      this.sprite.anims.pause()
      this.shieldTween.pause()
    } else if (!this.defeated) {
      this.sprite.anims.resume()
      // Never restart the pulse on a broken shield (it would redraw a stale ring).
      if (this.shielded) this.shieldTween.resume()
    }
  }

  /** Server says every mandatory API device is operated (true) or not (false). */
  setShieldDown(down: boolean): void {
    if (this.defeated || this.shielded === !down) return
    this.shielded = !down
    this.applyShieldVisual(true)
    this.emit()
  }

  applyDamage(amount: number, hitX?: number, hitY?: number): boolean {
    if (this.defeated || amount <= 0) return false
    if (this.shielded) {
      const spark = this.scene.add
        .image(hitX ?? this.sprite.x, hitY ?? this.sprite.y - 30, COMBAT_TEXTURE_KEYS.shieldSpark)
        .setDepth(56)
      this.scene.tweens.add({ targets: spark, alpha: 0, scale: 1.8, duration: 220, onComplete: () => spark.destroy() })
      return false
    }
    this.hp -= amount
    // Hit flash: brighten + flicker (Phaser 4 tint-fill API differs from v3).
    this.sprite.setTint(0xffe0e8).setAlpha(0.6)
    this.scene.time.delayedCall(90, () => {
      if (!this.defeated) this.sprite.setTint(this.cfg.tint).setAlpha(1)
    })
    if (this.hp <= 0) {
      this.playDefeat()
      return true
    }
    this.emit()
    return false
  }

  update(playerX: number, playerY: number, nowMs: number): void {
    if (this.defeated || this.paused || !this.sprite.active) return
    if (this.shielded) this.stepGuarding(nowMs)
    else this.stepChase(playerX, playerY, nowMs)

    const dist = Phaser.Math.Distance.Between(this.sprite.x, this.sprite.y, playerX, playerY)
    const interval = this.shielded ? this.cfg.volleyIntervalMs : this.cfg.enragedVolleyIntervalMs
    if (
      dist <= this.cfg.fireRangePx &&
      nowMs - this.lastVolleyAt >= interval &&
      this.hasLineOfSight(playerX, playerY)
    ) {
      this.lastVolleyAt = nowMs
      this.fireVolley(playerX, playerY)
    }
    this.sprite.setDepth(15 + this.sprite.y)
    this.syncOverlays()
  }

  /** Melee contact damage; returns hearts to remove (0 when on cooldown / out of range). */
  tryContact(playerX: number, playerY: number, nowMs: number): number {
    if (this.defeated || this.paused) return 0
    const dist = Phaser.Math.Distance.Between(this.sprite.x, this.sprite.y, playerX, playerY)
    if (dist > this.cfg.contactRangePx) return 0
    if (nowMs - this.lastContactAt < this.cfg.contactCooldownMs) return 0
    this.lastContactAt = nowMs
    this.faceToward(playerX, playerY)
    this.sprite.anims.play(`guard-attack-${this.facing}`, true)
    return this.cfg.contactDamage
  }

  destroy(): void {
    this.shieldTween.stop()
    this.shieldRing.destroy()
    this.bar.destroy()
    this.nameTag.destroy()
    this.sprite.destroy()
  }

  // ---- behaviour -------------------------------------------------------------------

  private stepGuarding(nowMs: number): void {
    if (!this.wanderTarget || nowMs >= this.wanderUntil) {
      const angle = Math.random() * Math.PI * 2
      const r = Math.random() * this.cfg.leashPx
      const candidate = { x: this.home.x + Math.cos(angle) * r, y: this.home.y + Math.sin(angle) * r }
      const cell = this.nav.worldToCell(candidate.x, candidate.y)
      this.wanderTarget = this.nav.isWalkable(cell.col, cell.row) ? candidate : { ...this.home }
      this.wanderUntil = nowMs + 1800 + Math.random() * 1200
    }
    this.moveToward(this.wanderTarget.x, this.wanderTarget.y, this.cfg.guardSpeed, 6)
  }

  private stepChase(playerX: number, playerY: number, nowMs: number): void {
    const dist = Phaser.Math.Distance.Between(this.sprite.x, this.sprite.y, playerX, playerY)
    if (dist <= this.cfg.contactRangePx * 0.8) {
      this.stop()
      this.faceToward(playerX, playerY)
      return
    }
    // Stuck on a prop corner: drop the direct line and force a fresh grid path.
    const moved = Math.hypot(this.sprite.x - this.lastX, this.sprite.y - this.lastY)
    this.lastX = this.sprite.x
    this.lastY = this.sprite.y
    if (moved < 0.5) {
      if (this.stuckSince === 0) this.stuckSince = nowMs
    } else {
      this.stuckSince = 0
    }
    const stuck = this.stuckSince > 0 && nowMs - this.stuckSince > 350
    if (!stuck && this.hasLineOfSight(playerX, playerY) && dist < 140) {
      this.moveToward(playerX, playerY, this.cfg.enragedSpeed, 4)
      return
    }
    if (stuck || nowMs - this.lastPathAt > REPATH_MS || this.pathIndex >= this.path.length) {
      this.stuckSince = 0
      this.lastPathAt = nowMs
      const here = this.nav.nearestWalkable(this.nav.worldToCell(this.sprite.x, this.sprite.y))
      const goal = this.nav.nearestWalkable(this.nav.worldToCell(playerX, playerY))
      if (here && goal) {
        const result = findPath(this.nav, here, goal)
        this.path = result.reachable ? pathFromCurrent(result.path, here) : []
        this.pathIndex = 0
      }
    }
    const next = this.path[this.pathIndex]
    if (!next) {
      this.stop()
      return
    }
    const target = this.nav.cellToWorld(next)
    if (this.moveToward(target.x, target.y, this.cfg.enragedSpeed, 8)) this.pathIndex += 1
  }

  /** Returns true once within `arrive` px of the target. */
  private moveToward(tx: number, ty: number, speed: number, arrive: number): boolean {
    const dx = tx - this.sprite.x
    const dy = ty - this.sprite.y
    const d = Math.hypot(dx, dy)
    const body = this.sprite.body as Phaser.Physics.Arcade.Body
    if (d <= arrive) {
      body.setVelocity(0, 0)
      this.playAnim('idle')
      return true
    }
    body.setVelocity((dx / d) * speed, (dy / d) * speed)
    this.faceToward(tx, ty)
    this.playAnim('walk')
    return false
  }

  private fireVolley(playerX: number, playerY: number): void {
    const ox = this.sprite.x
    const oy = this.sprite.y - 30
    const base = Math.atan2(playerY - 14 - oy, playerX - ox)
    const n = this.cfg.volleySize
    for (let i = 0; i < n; i += 1) {
      const offset = n === 1 ? 0 : -this.cfg.volleySpreadRad + (2 * this.cfg.volleySpreadRad * i) / (n - 1)
      const a = base + offset
      this.orbs.spawn({
        x: ox,
        y: oy,
        vx: Math.cos(a) * this.cfg.orbSpeed,
        vy: Math.sin(a) * this.cfg.orbSpeed,
        weapon: this.orbWeapon,
      })
    }
    this.faceToward(playerX, playerY)
    this.sprite.anims.play(`guard-attack-${this.facing}`, true)
  }

  private playDefeat(): void {
    this.defeated = true
    this.hp = 0
    this.stop()
    this.emit()
    this.scene.cameras.main.shake(260, 0.01)
    this.sprite.anims.play('guard-defeat', true)
    this.scene.tweens.add({
      targets: [this.sprite, this.nameTag],
      alpha: 0,
      duration: 700,
      delay: 250,
      onComplete: () => {
        this.sprite.disableBody(true, true)
        this.bar.clear()
        this.nameTag.setVisible(false)
      },
    })
    this.onDefeated()
  }

  private applyShieldVisual(animate: boolean): void {
    if (this.shielded) {
      this.shieldRing.setVisible(true).setAlpha(1).setScale(1)
      this.shieldTween.resume()
      return
    }
    this.shieldTween.pause()
    if (!animate) {
      this.shieldRing.setVisible(false)
      return
    }
    this.shatterUntil = this.scene.time.now + 450
    this.scene.cameras.main.flash(180, 120, 240, 255)
    this.scene.tweens.add({
      targets: this.shieldRing,
      scale: 1.9,
      alpha: 0,
      duration: 420,
      onComplete: () => this.shieldRing.setVisible(false),
    })
  }

  private syncOverlays(): void {
    const x = this.sprite.x
    const top = this.sprite.y - PATROL_GUARD_CONFIG.frameHeight * this.cfg.displayScale * 0.85
    // The ring is driven by the real shield state every frame, not by tween callbacks.
    this.shieldRing
      .setPosition(x, this.sprite.y - 30)
      .setVisible(this.shielded || this.scene.time.now < this.shatterUntil)
    this.nameTag.setPosition(x, top - 8).setDepth(61)
    const w = 64
    const frac = Math.max(0, this.hp) / this.cfg.maxHealth
    this.bar.clear()
    if (this.defeated) return
    this.bar.setDepth(61)
    this.bar.fillStyle(0x12060b, 0.9).fillRect(x - w / 2 - 1, top - 6, w + 2, 6)
    this.bar.fillStyle(this.shielded ? 0x37e0ff : 0xff3d6e, 1).fillRect(x - w / 2, top - 5, w * frac, 4)
  }

  private emit(): void {
    this.onChange(this.snapshot())
  }

  private stop(): void {
    ;(this.sprite.body as Phaser.Physics.Arcade.Body | null)?.setVelocity(0, 0)
  }

  private faceToward(tx: number, ty: number): void {
    const dx = tx - this.sprite.x
    const dy = ty - this.sprite.y
    this.facing = Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? 'left' : 'right') : dy < 0 ? 'up' : 'down'
  }

  private playAnim(kind: 'idle' | 'walk'): void {
    const key = `guard-${kind}-${this.facing}`
    const current = this.sprite.anims.currentAnim?.key
    if (current?.startsWith('guard-attack') && this.sprite.anims.isPlaying) return
    if (current !== key) this.sprite.anims.play(key, true)
  }

  private hasLineOfSight(tx: number, ty: number): boolean {
    const x0 = this.sprite.x
    const y0 = this.sprite.y - 20
    const steps = 12
    for (let i = 1; i < steps; i += 1) {
      const t = i / steps
      const cell = this.nav.worldToCell(x0 + (tx - x0) * t, y0 + (ty - 14 - y0) * t)
      const row = this.blocked[cell.row]
      // Walls, solid props and terminals block line of fire (agents can use them as cover).
      if (!row || row[cell.col] !== false) return false
    }
    return true
  }
}
