import Phaser from 'phaser'
import type { RoomLayoutPlan } from '../room/types'
import { BossEnemy, type BossSnapshot } from './BossEnemy'
import { AGENT_HURTBOX, spriteHurtbox } from './hurtbox'
import { bossConfigFor, planBossSpawn } from './bossConfig'
import {
  ensureBossTextures,
  ensureEnemyShotTexture,
  ensureCombatFxTextures,
  ensureGuardAnimations,
  ensureGuardSpritesheet,
  COMBAT_TEXTURE_KEYS,
} from './combatArt'
import { COMBAT_TUNING } from './combatTuning'
import { CombatCollisionManager } from './CombatCollisionManager'
import { NavigationGrid } from './navigationGrid'
import { PatrolGuard, type EnemyStrike } from './PatrolGuard'
import { ENEMY_SHEETS } from './enemyTypes'
import { PlayerHealth } from './PlayerHealth'
import { planPatrolGuardSpawns } from './spawnPlan'
import { ProjectileManager } from './ProjectileManager'
import { WeaponController } from './WeaponController'
import { STARTER_SIDEARM } from './weapons'

type Facing = 'down' | 'left' | 'right' | 'up'

export type HeartsListener = (hearts: number, maxHearts: number) => void
export type CombatDeathListener = () => void
export type BossStateListener = (state: BossSnapshot) => void

export interface CombatSystemOptions {
  scene: Phaser.Scene
  layout: RoomLayoutPlan
  player: Phaser.Physics.Arcade.Sprite
  solids: Phaser.Physics.Arcade.StaticGroup
  rank: string
  getFacing: () => Facing
  onHeartsChanged: HeartsListener
  onCombatDeath: CombatDeathListener
  /**
   * Level 7 boss-only room: no ordinary guards spawn; one boss whose shield follows the
   * server's mandatory-device state (`shieldDown`).
   */
  boss?: {
    /** Module whose boss guards this room (1 Nexus Warden, 2 Packet Juggernaut, 3 Cipher Queen). */
    module: number
    shieldDown: boolean
    /** Already defeated in this room run (server state) — the room stays empty. */
    defeated: boolean
    onStateChanged: BossStateListener
    onDefeated: () => void
  }
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
  private boss: BossEnemy | null = null
  /** Shared pool for hostile projectiles (rifleman tracers, boss orbs). */
  private readonly enemyShots: ProjectileManager
  private readonly bossColliders: Phaser.Physics.Arcade.Collider[] = []
  private readonly enemyShotCollider: Phaser.Physics.Arcade.Collider
  private readonly agentBox = new Phaser.Geom.Rectangle()

  constructor(options: CombatSystemOptions) {
    this.options = options
    const { scene, layout, player, solids, rank, getFacing, onHeartsChanged, onCombatDeath } =
      options
    this.player = player
    this.getFacing = getFacing
    this.onCombatDeath = onCombatDeath

    ensureCombatFxTextures(scene)
    ensureEnemyShotTexture(scene)
    for (const cfg of ENEMY_SHEETS) {
      ensureGuardSpritesheet(scene, cfg.spritesheetKey, cfg.sheetPath)
      ensureGuardAnimations(scene, cfg.spritesheetKey, cfg.animPrefix)
    }

    this.projectiles = new ProjectileManager(scene)
    this.weapon = new WeaponController(scene, this.projectiles, STARTER_SIDEARM.id)
    this.weapon.bindInput()
    this.collisions = new CombatCollisionManager(scene, this.projectiles)
    this.collisions.bindWalls(solids)
    this.enemyShots = new ProjectileManager(scene)
    this.enemyShotCollider = scene.physics.add.collider(this.enemyShots.getGroup(), solids, (a, b) => {
      const group = this.enemyShots.getGroup()
      const shot = (group.contains(a as Phaser.GameObjects.GameObject) ? a : b) as Phaser.Physics.Arcade.Image
      this.enemyShots.despawn(shot)
    })

    this.health.setListeners(onHeartsChanged, () => this.handleDeath())

    if (options.boss) {
      // Level 7 is boss-only: never spawn ordinary guards, even once the boss is down.
      if (!options.boss.defeated) this.spawnBoss(layout, solids, rank, options.boss)
    } else {
      this.spawnGuards(layout, solids, rank)
    }
  }

  private spawnBoss(
    layout: RoomLayoutPlan,
    solids: Phaser.Physics.Arcade.StaticGroup,
    rank: string,
    boss: NonNullable<CombatSystemOptions['boss']>,
  ): void {
    const scene = this.options.scene
    ensureBossTextures(scene)
    const nav = NavigationGrid.fromLayout(layout)
    const cell = planBossSpawn(layout, nav)
    if (!cell) return
    const config = bossConfigFor(boss.module, rank)
    ensureGuardSpritesheet(scene, config.sheetKey, config.sheetPath)
    ensureGuardAnimations(scene, config.sheetKey, config.animPrefix)
    this.boss = new BossEnemy({
      scene,
      cell,
      config,
      nav,
      blocked: layout.blocked,
      orbs: this.enemyShots,
      shielded: !boss.shieldDown,
      onChange: boss.onStateChanged,
      onDefeated: boss.onDefeated,
    })
    this.collisions.bindEnemies([this.boss])
    this.bossColliders.push(
      scene.physics.add.collider(this.boss.sprite, solids),
      scene.physics.add.collider(this.player, this.boss.sprite),
    )
  }

  /** Server-driven: true once every mandatory API device in the room is activated. */
  setBossShieldDown(down: boolean): void {
    this.boss?.setShieldDown(down)
  }

  private spawnGuards(
    layout: RoomLayoutPlan,
    solids: Phaser.Physics.Arcade.StaticGroup,
    rank: string,
  ): void {
    const scene = this.options.scene
    this.enemyGroup = scene.physics.add.group()
    // One nav grid per room — shared by all guards (cached for the layout lifetime).
    const nav = NavigationGrid.fromLayout(layout)
    const planned = planPatrolGuardSpawns(layout, rank, nav)
    for (const spawn of planned) {
      const guard = new PatrolGuard(scene, spawn.waypoints, layout.blocked, spawn.config, nav)
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
    this.boss?.setPaused(paused)
    this.enemyShots.setPaused(paused)
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
      this.collisions.update()
      this.enemyShots.update(time)
      this.checkEnemyShotHits(now)
      if (this.boss && !this.boss.isDefeated()) {
        this.boss.update(this.player.x, this.player.y, now)
        const dmg = this.boss.tryContact(this.player.x, this.player.y, now)
        if (dmg > 0) this.applyPlayerHit(dmg, now)
      }

      for (const guard of this.guards) {
        if (guard.isDefeated()) continue
        guard.update(this.player.x, this.player.y, now)
        const strike = guard.tryAttack(this.player.x, this.player.y, now)
        if (strike) this.resolveStrike(strike, now)
      }
    }
  }

  private resolveStrike(strike: EnemyStrike, nowMs: number): void {
    if (strike.kind === 'melee') {
      this.applyPlayerHit(strike.damage, nowMs)
      return
    }
    // Ranged: only hurts if the tracer actually reaches the agent.
    this.enemyShots.spawn({
      x: strike.x,
      y: strike.y,
      vx: strike.vx,
      vy: strike.vy,
      weapon: {
        id: 'enemy_rifle',
        displayName: 'Rifle',
        cooldownMs: 0,
        projectileSpeed: Math.hypot(strike.vx, strike.vy),
        damage: strike.damage,
        projectileKey: COMBAT_TEXTURE_KEYS.enemyBullet,
        projectileLifespanMs: strike.lifespanMs,
        projectileScale: 1.4,
      },
    })
  }

  /** Enemy shots hit the agent's body-sized hurtbox, not the feet-level movement body. */
  private checkEnemyShotHits(nowMs: number): void {
    const orbs = this.enemyShots
    const box = spriteHurtbox(this.player, AGENT_HURTBOX, this.agentBox)
    for (const child of orbs.getGroup().getChildren()) {
      const orb = child as Phaser.Physics.Arcade.Image
      if (!orb.active || !box.contains(orb.x, orb.y)) continue
      orbs.despawn(orb)
      this.applyPlayerHit(Number(orb.getData('damage') ?? 1), nowMs)
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
    for (const c of this.bossColliders) c.destroy()
    this.bossColliders.length = 0
    this.boss?.destroy()
    this.boss = null
    this.enemyShotCollider.destroy()
    this.enemyShots.destroy()
    this.guards.length = 0
    this.enemyGroup?.clear(false, false)
    this.enemyGroup?.destroy(false)
    this.enemyGroup = null
  }
}
