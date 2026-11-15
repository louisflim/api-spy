import Phaser from 'phaser'
import type { RoomLayoutPlan } from '../room/types'
import {
  ensureCombatFxTextures,
  ensureGuardAnimations,
  ensureGuardSpritesheet,
  COMBAT_TEXTURE_KEYS,
} from './combatArt'
import { COMBAT_TUNING } from './combatTuning'
import { CombatCollisionManager } from './CombatCollisionManager'
import { PatrolGuard } from './PatrolGuard'
import { PlayerHealth } from './PlayerHealth'
import { planPatrolGuardSpawns } from './spawnPlan'
import { ProjectileManager } from './ProjectileManager'
import { WeaponController } from './WeaponController'
import { STARTER_SIDEARM } from './weapons'

type Facing = 'down' | 'left' | 'right' | 'up'

export type HeartsListener = (hearts: number, maxHearts: number) => void
export type CombatDeathListener = () => void

export interface CombatSystemOptions {
  scene: Phaser.Scene
  layout: RoomLayoutPlan
  player: Phaser.Physics.Arcade.Sprite
  solids: Phaser.Physics.Arcade.StaticGroup
  rank: string
  getFacing: () => Facing
  onHeartsChanged: HeartsListener
  onCombatDeath: CombatDeathListener
}

/**
 * Orchestrates Phase-1 combat inside MissionScene.
 * Practice / Module 0 skip construction entirely (combatEnabled=false).
 */
export class CombatSystem {
  private readonly health = new PlayerHealth(COMBAT_TUNING)
  private readonly projectiles: ProjectileManager
  private readonly weapon: WeaponController
  private readonly collisions: CombatCollisionManager
  private readonly guards: PatrolGuard[] = []
  private paused = false
  private pauseStartedAt = 0
  private totalPausedMs = 0
  private deathEmitted = false
  private readonly player: Phaser.Physics.Arcade.Sprite
  private readonly getFacing: () => Facing
  private readonly onCombatDeath: CombatDeathListener
  private readonly options: CombatSystemOptions
  private enemyCollider: Phaser.Physics.Arcade.Collider | null = null
  private enemyGroup: Phaser.Physics.Arcade.Group | null = null

  constructor(options: CombatSystemOptions) {
    this.options = options
    const { scene, layout, player, solids, rank, getFacing, onHeartsChanged, onCombatDeath } =
      options
    this.player = player
    this.getFacing = getFacing
    this.onCombatDeath = onCombatDeath

    ensureCombatFxTextures(scene)
    ensureGuardSpritesheet(scene)
    ensureGuardAnimations(scene)

    this.projectiles = new ProjectileManager(scene)
    this.weapon = new WeaponController(scene, this.projectiles, STARTER_SIDEARM.id)
    this.weapon.bindInput()
    this.collisions = new CombatCollisionManager(scene, this.projectiles)
    this.collisions.bindWalls(solids)

    this.health.setListeners(onHeartsChanged, () => this.handleDeath())

    this.spawnGuards(layout, solids, rank)
  }

  private spawnGuards(
    layout: RoomLayoutPlan,
    solids: Phaser.Physics.Arcade.StaticGroup,
    rank: string,
  ): void {
    const scene = this.options.scene
    this.enemyGroup = scene.physics.add.group()
    const planned = planPatrolGuardSpawns(layout, rank)
    for (const spawn of planned) {
      const guard = new PatrolGuard(scene, spawn.waypoints, layout.blocked, spawn.config)
      this.guards.push(guard)
      this.enemyGroup.add(guard.sprite)
      scene.physics.add.collider(guard.sprite, solids)
    }
    this.collisions.bindEnemies(this.guards)
    this.enemyCollider = scene.physics.add.collider(this.player, this.enemyGroup)
  }

  setPaused(paused: boolean): void {
    if (this.paused === paused) return
    const now = this.options.scene.time.now
    if (paused) {
      this.pauseStartedAt = now
    } else if (this.pauseStartedAt > 0) {
      this.totalPausedMs += now - this.pauseStartedAt
      this.pauseStartedAt = 0
    }
    this.paused = paused
    this.weapon.setPaused(paused)
    this.projectiles.setPaused(paused)
    for (const guard of this.guards) guard.setPaused(paused)
    if (paused) {
      const body = this.player.body as Phaser.Physics.Arcade.Body
      body.setVelocity(0, 0)
    }
  }

  isPaused(): boolean {
    return this.paused
  }

  getHearts(): number {
    return this.health.getHearts()
  }

  /** Combat clock that does not advance while the API terminal is open. */
  private combatNow(sceneTime: number): number {
    const openPause = this.paused && this.pauseStartedAt > 0 ? sceneTime - this.pauseStartedAt : 0
    return sceneTime - this.totalPausedMs - openPause
  }

  update(time: number): void {
    if (this.health.isDead()) return
    // Projectiles use wall-clock scene time (lifespan frozen explicitly on pause).
    // AI / weapon / invuln use combatNow so terminal time does not advance combat.
    const now = this.combatNow(time)

    if (!this.paused) {
      this.weapon.tryFire(this.player.x, this.player.y, this.getFacing(), now)
      this.projectiles.update(time)

      for (const guard of this.guards) {
        if (guard.isDefeated()) continue
        guard.update(this.player.x, this.player.y, now)
        const dmg = guard.tryAttack(this.player.x, this.player.y, now)
        if (dmg > 0) {
          this.applyPlayerHit(dmg, now)
        }
      }
    }
  }

  private applyPlayerHit(damage: number, nowMs: number): void {
    const hit = this.health.takeDamage(damage, nowMs, COMBAT_TUNING.invulnerabilityMs)
    if (!hit) return
    this.playHitFeedback()
  }

  private playHitFeedback(): void {
    const scene = this.options.scene
    this.player.setTint(0xff4444)
    scene.cameras.main.shake(120, 0.006)
    const flash = scene.add
      .image(this.player.x, this.player.y - 28, COMBAT_TEXTURE_KEYS.hitFlash)
      .setDepth(50)
      .setAlpha(0.9)
    scene.tweens.add({
      targets: flash,
      alpha: 0,
      y: flash.y - 16,
      duration: 280,
      onComplete: () => flash.destroy(),
    })
    scene.time.delayedCall(COMBAT_TUNING.invulnerabilityMs, () => {
      if (!this.health.isDead()) this.player.clearTint()
    })
    scene.tweens.add({
      targets: this.player,
      alpha: 0.45,
      duration: 90,
      yoyo: true,
      repeat: Math.max(1, Math.floor(COMBAT_TUNING.invulnerabilityMs / 180)),
      onComplete: () => {
        if (!this.health.isDead()) this.player.setAlpha(1)
      },
    })
  }

  private handleDeath(): void {
    if (this.deathEmitted) return
    this.deathEmitted = true
    this.setPaused(true)
    this.weapon.setEnabled(false)
    this.player.setTint(0x660000)
    const body = this.player.body as Phaser.Physics.Arcade.Body
    body.setVelocity(0, 0)
    this.options.scene.tweens.add({
      targets: this.player,
      alpha: 0.2,
      duration: 400,
      onComplete: () => this.onCombatDeath(),
    })
  }

  destroy(): void {
    this.weapon.unbindInput()
    this.collisions.destroy()
    this.projectiles.destroy()
    this.enemyCollider?.destroy()
    this.enemyCollider = null
    for (const guard of this.guards) guard.destroy()
    this.guards.length = 0
    this.enemyGroup?.clear(false, false)
    this.enemyGroup?.destroy(false)
    this.enemyGroup = null
  }
}
