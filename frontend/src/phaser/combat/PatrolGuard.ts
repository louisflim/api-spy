import Phaser from 'phaser'
import { ROOM_TILE_SIZE } from '../room/generateRoomLayout'
import { COMBAT_TEXTURE_KEYS } from './combatArt'
import { EnemyStateMachine } from './EnemyStateMachine'
import type { EnemyTypeConfig } from './enemyTypes'
import type { NavigationGrid } from './navigationGrid'
import { findPath, pathFromCurrent } from './pathfind'
import { GUARD_HURTBOX, spriteHurtbox } from './hurtbox'
import { cellToWorld } from './patrolZones'
import type { EnemyAiState, GridCell } from './types'

type Facing = 'down' | 'left' | 'right' | 'up'

const WAYPOINT_REACH_PX = 12
const CHASE_REPATH_MS = 380
const STUCK_MS = 750
const STUCK_MOVE_EPS = 2.5
const MAX_REPATH_BURST = 3

/**
 * Security Patrol Guard — reusable enemy driven by {@link EnemyTypeConfig}.
 * FSM: PATROL → ALERT → CHASE → ATTACK → RETURN
 * Movement follows A* paths on the room {@link NavigationGrid}.
 */
export type EnemyStrike =
  | { kind: 'melee'; damage: number }
  | { kind: 'shot'; damage: number; x: number; y: number; vx: number; vy: number; lifespanMs: number }

export class PatrolGuard {
  readonly sprite: Phaser.Physics.Arcade.Sprite
  private readonly fsm = new EnemyStateMachine('PATROL')
  private readonly waypoints: GridCell[]
  private readonly blocked: boolean[][]
  private readonly nav: NavigationGrid
  private readonly config: EnemyTypeConfig
  private readonly alertIcon: Phaser.GameObjects.Image
  /** Health bar drawn above the guard's head (hidden once defeated). */
  private readonly hpBar: Phaser.GameObjects.Graphics
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
  private readonly homeCell: GridCell

  private navPath: GridCell[] = []
  private navPathIndex = 0
  private lastPathGoal: GridCell | null = null
  private lastPathAt = 0
  private repathBurst = 0

  private stuckSince = 0
  private lastStuckX = 0
  private lastStuckY = 0

  constructor(
    scene: Phaser.Scene,
    waypoints: GridCell[],
    blocked: boolean[][],
    config: EnemyTypeConfig,
    nav: NavigationGrid,
  ) {
    if (!nav) {
      throw new Error('PatrolGuard requires a NavigationGrid from the room layout')
    }
    this.waypoints = waypoints
    this.blocked = blocked
    this.nav = nav
    this.config = config
    const start = cellToWorld(waypoints[0]!)
    this.home = { ...start }
    this.homeCell = { ...waypoints[0]! }
    this.hp = config.maxHealth
    this.sprite = scene.physics.add.sprite(
      start.x,
      start.y,
      config.spritesheetKey,
      0,
    )
    this.sprite.setScale(config.displayScale)
    this.sprite.setOrigin(0.5, 0.85)
    this.sprite.setDepth(15 + start.y)
    this.sprite.setCollideWorldBounds(true)
    this.sprite.setData('enemy', this)
    this.sprite.setData('kind', config.typeId)
    // World collision footprint stays near player size; art may be larger.
    const scale = Math.max(0.01, config.displayScale)
    const worldBodyW = 14
    const worldBodyH = 10
    const bodyW = worldBodyW / scale
    const bodyH = worldBodyH / scale
    const body = this.sprite.body as Phaser.Physics.Arcade.Body
    body.setSize(bodyW, bodyH)
    body.setOffset((config.frameWidth - bodyW) / 2, config.frameHeight - bodyH - 2)
    body.setAllowGravity(false)
    this.sprite.anims.play(`${this.config.animPrefix}-idle-down`)
    this.lastStuckX = start.x
    this.lastStuckY = start.y

    this.alertIcon = scene.add
      .image(start.x, start.y - this.alertOffsetY(), COMBAT_TEXTURE_KEYS.alert)
      .setDepth(60)
      .setVisible(false)
    this.hpBar = scene.add.graphics().setDepth(61)
    this.drawHealthBar()
    // Initial path is requested on first update so HMR/partial reloads cannot
    // call requestPath before the nav grid reference is fully wired.
  }

  getState(): EnemyAiState {
    return this.fsm.getState()
  }

  isDefeated(): boolean {
    return this.defeated
  }

  hurtbox(out?: Phaser.Geom.Rectangle): Phaser.Geom.Rectangle {
    return spriteHurtbox(this.sprite, GUARD_HURTBOX, out)
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
    this.drawHealthBar()
    this.sprite.anims.play(`${this.config.animPrefix}-hit`, true)
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
    this.clearPath()
    this.alertIcon.setVisible(false)
    this.hpBar.clear()
    this.sprite.anims.play(`${this.config.animPrefix}-defeat`, true)
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
        this.stepPatrol(nowMs)
        if (dist <= this.config.detectRangePx && los) {
          this.fsm.transition('ALERT')
          this.alertUntil = nowMs + this.config.alertMs
          this.stop()
          this.clearPath()
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
          if (this.fsm.getState() === 'CHASE') {
            this.requestPath(this.playerGoalCell(playerX, playerY), nowMs, true)
          } else {
            this.requestPath(this.homeCell, nowMs, true)
          }
        }
        break
      case 'CHASE':
        this.alertIcon.setVisible(true)
        if (dist <= this.config.attackRangePx && los) {
          this.fsm.transition('ATTACK')
          this.stop()
          this.clearPath()
          break
        }
        if (dist > this.config.chaseRangePx || !los) {
          if (this.lostSightSince === 0) this.lostSightSince = nowMs
          if (nowMs - this.lostSightSince >= this.config.chaseLoseMs) {
            this.lostSightSince = 0
            this.alertIcon.setVisible(false)
            this.fsm.transition('RETURN')
            this.requestPath(this.homeCell, nowMs, true)
            break
          }
        } else {
          this.lostSightSince = 0
        }
        this.stepChase(playerX, playerY, nowMs)
        break
      case 'ATTACK':
        this.alertIcon.setVisible(true)
        if (dist > this.config.attackRangePx + 10 || !los) {
          const next = dist <= this.config.chaseRangePx ? 'CHASE' : 'RETURN'
          this.fsm.transition(next)
          if (next === 'CHASE') {
            this.requestPath(this.playerGoalCell(playerX, playerY), nowMs, true)
          } else {
            this.requestPath(this.homeCell, nowMs, true)
          }
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
        if (homeDist < WAYPOINT_REACH_PX) {
          this.waypointIndex = 0
          this.clearPath()
          this.fsm.transition('PATROL')
          this.requestPath(this.waypoints[0]!, nowMs, true)
          this.playIdle()
          break
        }
        if (dist <= this.config.detectRangePx && los) {
          this.fsm.transition('CHASE')
          this.requestPath(this.playerGoalCell(playerX, playerY), nowMs, true)
          break
        }
        this.stepReturn(nowMs)
        break
      }
    }

    this.sprite.setDepth(10 + Math.floor(this.sprite.y))
    this.syncAlertIcon()
  }

  /**
   * Melee types return contact damage; ranged types return a shot for the combat system
   * to spawn (it only hurts if it actually reaches the agent's hurtbox).
   */
  tryAttack(playerX: number, playerY: number, nowMs: number): EnemyStrike | null {
    if (this.defeated || this.paused || this.fsm.getState() !== 'ATTACK') return null
    if (nowMs < this.attackLockUntil) return null
    const dist = Phaser.Math.Distance.Between(this.sprite.x, this.sprite.y, playerX, playerY)
    if (dist > this.config.attackRangePx) return null
    if (!this.hasLineOfSight(playerX, playerY)) return null
    if (nowMs - this.lastAttackAt < this.config.attackCooldownMs) return null
    this.lastAttackAt = nowMs
    this.attackLockUntil = nowMs + 280
    this.faceToward(playerX, playerY)
    this.sprite.anims.play(`${this.config.animPrefix}-attack-${this.facing}`, true)
    if (this.config.attackKind === 'ranged') {
      const fromX = this.sprite.x
      const fromY = this.sprite.y - this.config.frameHeight * this.config.displayScale * 0.45
      const angle = Math.atan2(playerY - 22 - fromY, playerX - fromX)
      const speed = this.config.shotSpeed ?? 220
      return {
        kind: 'shot',
        damage: this.config.attackDamage,
        x: fromX,
        y: fromY,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        lifespanMs: this.config.shotLifespanMs ?? 1200,
      }
    }
    return { kind: 'melee', damage: this.config.attackDamage }
  }

  destroy(): void {
    this.hpBar.destroy()
    this.alertIcon.destroy()
    this.sprite.destroy()
  }

  private alertOffsetY(): number {
    return this.config.frameHeight * this.config.displayScale * 0.75
  }

  /** Bar width 32px; green → amber → red as health drops. */
  private drawHealthBar(): void {
    this.hpBar.clear()
    if (this.defeated || this.hp <= 0) return
    const frac = Math.max(0, Math.min(1, this.hp / this.config.maxHealth))
    const w = 32
    const x = this.sprite.x - w / 2
    const y = this.sprite.y - this.config.frameHeight * this.config.displayScale * 0.95
    const color = frac > 0.6 ? 0x4ade80 : frac > 0.3 ? 0xfacc15 : 0xf87171
    this.hpBar.fillStyle(0x050b16, 0.85).fillRect(x - 1, y - 1, w + 2, 6)
    this.hpBar.fillStyle(color, 1).fillRect(x, y, Math.max(1, w * frac), 4)
  }

  private syncAlertIcon(): void {
    this.drawHealthBar()
    if (!this.alertIcon.active) return
    this.alertIcon.setPosition(this.sprite.x, this.sprite.y - this.alertOffsetY())
  }

  private stop(): void {
    const body = this.sprite.body as Phaser.Physics.Arcade.Body
    body.setVelocity(0, 0)
  }

  private clearPath(): void {
    this.navPath = []
    this.navPathIndex = 0
    this.lastPathGoal = null
    this.repathBurst = 0
  }

  private currentCell(): GridCell {
    return this.nav.worldToCell(this.sprite.x, this.sprite.y)
  }

  private playerGoalCell(playerX: number, playerY: number): GridCell {
    const preferred = this.nav.worldToCell(playerX, playerY)
    return this.nav.nearestWalkable(preferred) ?? preferred
  }

  private requestPath(goal: GridCell, nowMs: number, force = false): boolean {
    const resolvedGoal = this.nav.nearestWalkable(goal) ?? goal
    if (
      !force &&
      this.lastPathGoal &&
      this.nav.sameCell(this.lastPathGoal, resolvedGoal) &&
      nowMs - this.lastPathAt < CHASE_REPATH_MS &&
      this.navPath.length > 0
    ) {
      return true
    }

    const start = this.nav.nearestWalkable(this.currentCell()) ?? this.currentCell()
    const result = findPath(this.nav, start, resolvedGoal)
    this.lastPathAt = nowMs
    this.lastPathGoal = resolvedGoal

    if (!result.reachable || result.path.length === 0) {
      this.navPath = []
      this.navPathIndex = 0
      return false
    }

    const trimmed = pathFromCurrent(result.path, start)
    this.navPath = trimmed.length > 0 ? trimmed : result.path.slice(-1)
    this.navPathIndex = 0
    this.repathBurst = 0
    this.stuckSince = 0
    return true
  }

  private stepPatrol(nowMs: number): void {
    if (this.waypoints.length === 0) {
      this.stop()
      this.playIdle()
      return
    }
    const target = this.waypoints[this.waypointIndex]!
    const world = this.nav.cellToWorld(target)
    const dist = Phaser.Math.Distance.Between(this.sprite.x, this.sprite.y, world.x, world.y)
    if (dist < WAYPOINT_REACH_PX) {
      this.waypointIndex = (this.waypointIndex + 1) % this.waypoints.length
      this.requestPath(this.waypoints[this.waypointIndex]!, nowMs, true)
      this.playIdle()
      return
    }
    if (
      this.navPath.length === 0 ||
      !this.lastPathGoal ||
      !this.nav.sameCell(this.lastPathGoal, target)
    ) {
      this.requestPath(target, nowMs, true)
    }
    const follow = this.followPath(this.config.patrolSpeed, nowMs)
    if (follow === 'blocked' || follow === 'stuck') {
      this.handleStuck(nowMs, 'patrol')
    }
  }

  private stepChase(playerX: number, playerY: number, nowMs: number): void {
    const goal = this.playerGoalCell(playerX, playerY)
    const needRepath =
      !this.lastPathGoal ||
      !this.nav.sameCell(this.lastPathGoal, goal) ||
      nowMs - this.lastPathAt >= CHASE_REPATH_MS ||
      this.navPath.length === 0

    if (needRepath) {
      const ok = this.requestPath(goal, nowMs, true)
      if (!ok) {
        // No route — stop safely rather than ramming walls.
        this.stop()
        this.playIdle()
        this.faceToward(playerX, playerY)
        return
      }
    }

    const follow = this.followPath(this.config.chaseSpeed, nowMs)
    if (follow === 'arrived') {
      this.faceToward(playerX, playerY)
      this.playIdle()
      return
    }
    if (follow === 'blocked' || follow === 'stuck') {
      this.handleStuck(nowMs, 'chase', goal)
    }
  }

  private stepReturn(nowMs: number): void {
    if (!this.lastPathGoal || !this.nav.sameCell(this.lastPathGoal, this.homeCell)) {
      this.requestPath(this.homeCell, nowMs, true)
    }
    const follow = this.followPath(this.config.patrolSpeed, nowMs)
    if (follow === 'blocked' || follow === 'stuck') {
      this.handleStuck(nowMs, 'return')
    }
  }

  private followPath(
    speed: number,
    nowMs: number,
  ): 'moving' | 'arrived' | 'blocked' | 'stuck' {
    if (this.navPath.length === 0 || this.navPathIndex >= this.navPath.length) {
      this.stop()
      return 'arrived'
    }

    const targetCell = this.navPath[this.navPathIndex]!
    const target = this.nav.cellToWorld(targetCell)
    const dist = Phaser.Math.Distance.Between(this.sprite.x, this.sprite.y, target.x, target.y)

    if (dist < WAYPOINT_REACH_PX) {
      this.navPathIndex += 1
      this.stuckSince = 0
      this.lastStuckX = this.sprite.x
      this.lastStuckY = this.sprite.y
      if (this.navPathIndex >= this.navPath.length) {
        this.stop()
        return 'arrived'
      }
    }

    const next = this.navPath[this.navPathIndex]!
    const nextWorld = this.nav.cellToWorld(next)
    const dx = nextWorld.x - this.sprite.x
    const dy = nextWorld.y - this.sprite.y
    const len = Math.hypot(dx, dy) || 1
    const vx = (dx / len) * speed
    const vy = (dy / len) * speed
    const body = this.sprite.body as Phaser.Physics.Arcade.Body
    body.setVelocity(vx, vy)
    this.faceToward(nextWorld.x, nextWorld.y)
    this.playWalk()

    const moved = Math.hypot(this.sprite.x - this.lastStuckX, this.sprite.y - this.lastStuckY)
    if (moved < STUCK_MOVE_EPS) {
      if (this.stuckSince === 0) this.stuckSince = nowMs
      if (nowMs - this.stuckSince >= STUCK_MS) return 'stuck'
    } else {
      this.stuckSince = 0
      this.lastStuckX = this.sprite.x
      this.lastStuckY = this.sprite.y
    }
    return 'moving'
  }

  private handleStuck(
    nowMs: number,
    mode: 'patrol' | 'chase' | 'return',
    chaseGoal?: GridCell,
  ): void {
    this.stuckSince = 0
    this.lastStuckX = this.sprite.x
    this.lastStuckY = this.sprite.y
    this.repathBurst += 1

    if (this.repathBurst > MAX_REPATH_BURST) {
      this.repathBurst = 0
      this.stop()
      if (mode === 'patrol' && this.waypoints.length > 1) {
        this.waypointIndex = (this.waypointIndex + 1) % this.waypoints.length
        this.requestPath(this.waypoints[this.waypointIndex]!, nowMs, true)
      } else if (mode === 'chase') {
        this.fsm.transition('RETURN')
        this.alertIcon.setVisible(false)
        this.requestPath(this.homeCell, nowMs, true)
      } else {
        // Return with no route: idle in place until player re-engages or room resets.
        this.clearPath()
        this.playIdle()
      }
      return
    }

    if (mode === 'patrol') {
      this.requestPath(this.waypoints[this.waypointIndex]!, nowMs, true)
    } else if (mode === 'chase' && chaseGoal) {
      this.requestPath(chaseGoal, nowMs, true)
    } else {
      this.requestPath(this.homeCell, nowMs, true)
    }
  }

  private faceToward(tx: number, ty: number): void {
    const dx = tx - this.sprite.x
    const dy = ty - this.sprite.y
    if (Math.abs(dx) > Math.abs(dy)) this.facing = dx < 0 ? 'left' : 'right'
    else if (Math.abs(dy) > 0) this.facing = dy < 0 ? 'up' : 'down'
  }

  private playWalk(): void {
    const key = `${this.config.animPrefix}-walk-${this.facing}`
    if (this.sprite.anims.currentAnim?.key !== key) this.sprite.anims.play(key, true)
  }

  private playIdle(): void {
    const key = `${this.config.animPrefix}-idle-${this.facing}`
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
