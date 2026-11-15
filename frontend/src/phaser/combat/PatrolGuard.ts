import Phaser from 'phaser'
import { ROOM_TILE_SIZE } from '../room/generateRoomLayout'
import { COMBAT_TEXTURE_KEYS } from './combatArt'
import { EnemyStateMachine } from './EnemyStateMachine'
import type { EnemyTypeConfig } from './enemyTypes'
import { cellToWorld } from './patrolZones'
import type { EnemyAiState, GridCell } from './types'

type Facing = 'down' | 'left' | 'right' | 'up'

/**
 * Security Patrol Guard — reusable enemy driven by {@link EnemyTypeConfig}.
 * FSM: PATROL → ALERT → CHASE → ATTACK → RETURN
 */
export class PatrolGuard {
  readonly sprite: Phaser.Physics.Arcade.Sprite
  private readonly fsm = new EnemyStateMachine('PATROL')
  private readonly waypoints: GridCell[]
  private readonly blocked: boolean[][]
  private readonly config: EnemyTypeConfig
  private readonly alertIcon: Phaser.GameObjects.Image
  private hp: number
  private waypointIndex = 0
  private alertUntil = 0
  private lastAttackAt = 0
  private lostSightSince = 0
  private defeated = false
  private paused = false
  private facing: Facing = 'down'
  private attackLockUntil = 0
  private readonly home: { x: number; y: number }

  constructor(
    scene: Phaser.Scene,
    waypoints: GridCell[],
    blocked: boolean[][],
    config: EnemyTypeConfig,
  ) {
    this.waypoints = waypoints
    this.blocked = blocked
    this.config = config
    const start = cellToWorld(waypoints[0]!)
    this.home = { ...start }
    this.hp = config.maxHealth
    this.sprite = scene.physics.add.sprite(
      start.x,
      start.y,
      config.spritesheetKey,
      0,
    )
    this.sprite.setOrigin(0.5, 0.85)
    this.sprite.setDepth(15 + start.y)
    this.sprite.setCollideWorldBounds(true)
    this.sprite.setData('enemy', this)
    this.sprite.setData('kind', config.typeId)
    const body = this.sprite.body as Phaser.Physics.Arcade.Body
    body.setSize(18, 12)
    body.setOffset((config.frameWidth - 18) / 2, config.frameHeight - 14)
    body.setAllowGravity(false)
    this.sprite.anims.play('guard-idle-down')

    this.alertIcon = scene.add
      .image(start.x, start.y - 28, COMBAT_TEXTURE_KEYS.alert)
      .setDepth(60)
      .setVisible(false)
  }

  getState(): EnemyAiState {
    return this.fsm.getState()
  }

  isDefeated(): boolean {
    return this.defeated
  }

  setPaused(paused: boolean): void {
    this.paused = paused
    if (paused) {
      this.stop()
      this.sprite.anims.pause()
    } else if (!this.defeated) {
      this.sprite.anims.resume()
    }
  }

  applyDamage(amount: number): boolean {
    if (this.defeated || amount <= 0) return false
    this.hp -= amount
    this.sprite.anims.play('guard-hit', true)
    this.sprite.setTint(0xff6666)
    this.sprite.scene.time.delayedCall(140, () => {
      if (!this.defeated) this.sprite.clearTint()
    })
    if (this.hp <= 0) {
      this.playDefeat()
      return true
    }
    return false
  }

  private playDefeat(): void {
    this.defeated = true
    this.stop()
    this.alertIcon.setVisible(false)
    this.sprite.anims.play('guard-defeat', true)
    this.sprite.scene.tweens.add({
      targets: this.sprite,
      alpha: 0,
      duration: 420,
      delay: 180,
      onComplete: () => {
        this.sprite.disableBody(true, true)
        this.sprite.setActive(false)
        this.sprite.setVisible(false)
        this.alertIcon.destroy()
      },
    })
  }

  update(playerX: number, playerY: number, nowMs: number): void {
    if (this.defeated || this.paused || !this.sprite.active) return
    if (nowMs < this.attackLockUntil) {
      this.stop()
      this.syncAlertIcon()
      return
    }

    const dist = Phaser.Math.Distance.Between(this.sprite.x, this.sprite.y, playerX, playerY)
    const los = this.hasLineOfSight(playerX, playerY)
    const state = this.fsm.getState()

    switch (state) {
      case 'PATROL':
        this.stepPatrol()
        if (dist <= this.config.detectRangePx && los) {
          this.fsm.transition('ALERT')
          this.alertUntil = nowMs + this.config.alertMs
          this.stop()
          this.alertIcon.setVisible(true)
          this.faceToward(playerX, playerY)
          this.playIdle()
        }
        break
      case 'ALERT':
        this.stop()
        this.faceToward(playerX, playerY)
        this.playIdle()
        if (nowMs >= this.alertUntil) {
          this.fsm.transition(dist <= this.config.chaseRangePx && los ? 'CHASE' : 'RETURN')
          if (this.fsm.getState() !== 'CHASE') this.alertIcon.setVisible(false)
        }
        break
      case 'CHASE':
        this.alertIcon.setVisible(true)
        if (dist <= this.config.attackRangePx && los) {
          this.fsm.transition('ATTACK')
          this.stop()
          break
        }
        if (dist > this.config.chaseRangePx || !los) {
          if (this.lostSightSince === 0) this.lostSightSince = nowMs
          if (nowMs - this.lostSightSince >= this.config.chaseLoseMs) {
            this.lostSightSince = 0
            this.alertIcon.setVisible(false)
            this.fsm.transition('RETURN')
            break
          }
        } else {
          this.lostSightSince = 0
        }
        this.moveToward(playerX, playerY, this.config.chaseSpeed)
        this.faceToward(playerX, playerY)
        this.playWalk()
        break
      case 'ATTACK':
        this.alertIcon.setVisible(true)
        if (dist > this.config.attackRangePx + 10 || !los) {
          this.fsm.transition(dist <= this.config.chaseRangePx ? 'CHASE' : 'RETURN')
          break
        }
        this.stop()
        this.faceToward(playerX, playerY)
        break
      case 'RETURN': {
        this.alertIcon.setVisible(false)
        const homeDist = Phaser.Math.Distance.Between(
          this.sprite.x,
          this.sprite.y,
          this.home.x,
          this.home.y,
        )
        if (homeDist < 10) {
          this.waypointIndex = 0
          this.fsm.transition('PATROL')
          this.playIdle()
          break
        }
        if (dist <= this.config.detectRangePx && los) {
          this.fsm.transition('CHASE')
          break
        }
        this.moveToward(this.home.x, this.home.y, this.config.patrolSpeed)
        this.faceToward(this.home.x, this.home.y)
        this.playWalk()
        break
      }
    }

    this.sprite.setDepth(10 + Math.floor(this.sprite.y))
    this.syncAlertIcon()
  }

  /**
   * Returns damage when an attack connects (in range, cooldown ready).
   * Plays attack animation only when damage is applied.
   */
  tryAttack(playerX: number, playerY: number, nowMs: number): number {
    if (this.defeated || this.paused || this.fsm.getState() !== 'ATTACK') return 0
    if (nowMs < this.attackLockUntil) return 0
    const dist = Phaser.Math.Distance.Between(this.sprite.x, this.sprite.y, playerX, playerY)
    if (dist > this.config.attackRangePx) return 0
    if (!this.hasLineOfSight(playerX, playerY)) return 0
    if (nowMs - this.lastAttackAt < this.config.attackCooldownMs) return 0
    this.lastAttackAt = nowMs
    this.attackLockUntil = nowMs + 280
    this.faceToward(playerX, playerY)
    this.sprite.anims.play(`guard-attack-${this.facing}`, true)
    return this.config.attackDamage
  }

  destroy(): void {
    this.alertIcon.destroy()
    this.sprite.destroy()
  }

  private syncAlertIcon(): void {
    if (!this.alertIcon.active) return
    this.alertIcon.setPosition(this.sprite.x, this.sprite.y - 30)
  }

  private stop(): void {
    const body = this.sprite.body as Phaser.Physics.Arcade.Body
    body.setVelocity(0, 0)
  }

  private stepPatrol(): void {
    if (this.waypoints.length === 0) {
      this.stop()
      this.playIdle()
      return
    }
    const target = cellToWorld(this.waypoints[this.waypointIndex]!)
    const dist = Phaser.Math.Distance.Between(this.sprite.x, this.sprite.y, target.x, target.y)
    if (dist < 8) {
      this.waypointIndex = (this.waypointIndex + 1) % this.waypoints.length
      this.playIdle()
      return
    }
    this.moveToward(target.x, target.y, this.config.patrolSpeed)
    this.faceToward(target.x, target.y)
    this.playWalk()
  }

  private moveToward(tx: number, ty: number, speed: number): void {
    const dx = tx - this.sprite.x
    const dy = ty - this.sprite.y
    const len = Math.hypot(dx, dy) || 1
    let vx = (dx / len) * speed
    let vy = (dy / len) * speed

    const nextX = this.sprite.x + Math.sign(vx) * 10
    const nextY = this.sprite.y + Math.sign(vy) * 10
    if (this.isBlockedWorld(nextX, this.sprite.y)) vx = 0
    if (this.isBlockedWorld(this.sprite.x, nextY)) vy = 0
    if (vx === 0 && vy === 0) {
      if (!this.isBlockedWorld(this.sprite.x + Math.sign(dx) * 12, this.sprite.y)) {
        vx = Math.sign(dx) * speed
      } else if (!this.isBlockedWorld(this.sprite.x, this.sprite.y + Math.sign(dy) * 12)) {
        vy = Math.sign(dy) * speed
      }
    }

    const body = this.sprite.body as Phaser.Physics.Arcade.Body
    body.setVelocity(vx, vy)
  }

  private faceToward(tx: number, ty: number): void {
    const dx = tx - this.sprite.x
    const dy = ty - this.sprite.y
    if (Math.abs(dx) > Math.abs(dy)) this.facing = dx < 0 ? 'left' : 'right'
    else if (Math.abs(dy) > 0) this.facing = dy < 0 ? 'up' : 'down'
  }

  private playWalk(): void {
    const key = `guard-walk-${this.facing}`
    if (this.sprite.anims.currentAnim?.key !== key) this.sprite.anims.play(key, true)
  }

  private playIdle(): void {
    const key = `guard-idle-${this.facing}`
    if (this.sprite.anims.currentAnim?.key !== key) this.sprite.anims.play(key, true)
  }

  private isBlockedWorld(x: number, y: number): boolean {
    const col = Math.floor(x / ROOM_TILE_SIZE)
    const row = Math.floor(y / ROOM_TILE_SIZE)
    if (row < 0 || col < 0 || row >= this.blocked.length || col >= (this.blocked[0]?.length ?? 0)) {
      return true
    }
    return Boolean(this.blocked[row]![col])
  }

  private hasLineOfSight(tx: number, ty: number): boolean {
    const steps = 10
    for (let i = 1; i < steps; i += 1) {
      const t = i / steps
      const x = this.sprite.x + (tx - this.sprite.x) * t
      const y = this.sprite.y + (ty - this.sprite.y) * t
      if (this.isBlockedWorld(x, y)) return false
    }
    return true
  }
}
