import Phaser from 'phaser'
import { PATROL_GUARD_CONFIG } from './enemyTypes'

function registerCanvas(scene: Phaser.Scene, key: string, canvas: HTMLCanvasElement): void {
  if (scene.textures.exists(key)) return
  scene.textures.addCanvas(key, canvas)
  scene.textures.get(key).setFilter(Phaser.Textures.FilterMode.NEAREST)
}

function px(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  color: string,
): void {
  ctx.fillStyle = color
  ctx.fillRect(x, y, w, h)
}

function makeProjectileBolt(): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = 10
  c.height = 6
  const ctx = c.getContext('2d')!
  ctx.imageSmoothingEnabled = false
  ctx.clearRect(0, 0, 10, 6)
  px(ctx, 0, 2, 10, 2, '#5de8ff')
  px(ctx, 1, 1, 8, 4, '#9ef6ff')
  px(ctx, 2, 2, 6, 2, '#e8ffff')
  return c
}

function makeHitFlash(): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = 16
  c.height = 16
  const ctx = c.getContext('2d')!
  ctx.imageSmoothingEnabled = false
  ctx.clearRect(0, 0, 16, 16)
  px(ctx, 6, 2, 4, 12, '#ff6b6b')
  px(ctx, 2, 6, 12, 4, '#ff6b6b')
  px(ctx, 7, 4, 2, 8, '#ffd0d0')
  return c
}

function makeImpactSpark(): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = 12
  c.height = 12
  const ctx = c.getContext('2d')!
  ctx.imageSmoothingEnabled = false
  ctx.clearRect(0, 0, 12, 12)
  px(ctx, 5, 1, 2, 10, '#5de8ff')
  px(ctx, 1, 5, 10, 2, '#5de8ff')
  px(ctx, 4, 4, 4, 4, '#e8ffff')
  return c
}

function makeAlertBang(): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = 12
  c.height = 14
  const ctx = c.getContext('2d')!
  ctx.imageSmoothingEnabled = false
  ctx.clearRect(0, 0, 12, 14)
  px(ctx, 4, 0, 4, 9, '#e8c44a')
  px(ctx, 5, 1, 2, 7, '#fff3a0')
  px(ctx, 4, 11, 4, 3, '#e8c44a')
  return c
}

export const COMBAT_TEXTURE_KEYS = {
  guardSheet: PATROL_GUARD_CONFIG.spritesheetKey,
  projectile: 'spy-projectile-bolt',
  hitFlash: 'spy-hit-flash',
  impact: 'spy-impact-spark',
  alert: 'spy-alert-bang',
  bossOrb: 'spy-boss-orb',
  enemyBullet: 'spy-enemy-bullet',
  shieldSpark: 'spy-shield-spark',
} as const

/** Dedicated original guard spritesheet (generated, not a Rookie palette-swap). */
export const GUARD_SHEET_PATH = '/assets/enemies/security-patrol-guard.png'

/** Frame indices on the 4×6 guard sheet. */
export const GUARD_FRAMES = {
  idleDown: 0,
  walkDown: [1, 2, 3],
  idleLeft: 4,
  walkLeft: [5, 6, 7],
  idleRight: 8,
  walkRight: [9, 10, 11],
  idleUp: 12,
  walkUp: [13, 14, 15],
  attackDown: 16,
  attackLeft: 17,
  attackRight: 18,
  attackUp: 19,
  hit: 20,
  defeat: [21, 22, 23],
} as const

export function ensureCombatFxTextures(scene: Phaser.Scene): void {
  registerCanvas(scene, COMBAT_TEXTURE_KEYS.projectile, makeProjectileBolt())
  registerCanvas(scene, COMBAT_TEXTURE_KEYS.hitFlash, makeHitFlash())
  registerCanvas(scene, COMBAT_TEXTURE_KEYS.impact, makeImpactSpark())
  registerCanvas(scene, COMBAT_TEXTURE_KEYS.alert, makeAlertBang())
}

/**
 * Ensures the guard sheet texture exists after preload.
 * If the PNG failed to load, gameplay continues with a single-frame warning texture
 * (temporary — not claimed as finished artwork).
 */
export function ensureGuardSpritesheet(
  scene: Phaser.Scene,
  key: string = COMBAT_TEXTURE_KEYS.guardSheet,
  path: string = GUARD_SHEET_PATH,
): void {
  if (scene.textures.exists(key)) {
    scene.textures.get(key).setFilter(Phaser.Textures.FilterMode.NEAREST)
    return
  }
  console.warn(
    '[combat] Enemy spritesheet PNG missing at',
    path,
    '— using temporary single-frame placeholder',
  )
  const { frameWidth, frameHeight } = PATROL_GUARD_CONFIG
  const c = document.createElement('canvas')
  c.width = frameWidth * 4
  c.height = frameHeight * 6
  const ctx = c.getContext('2d')!
  ctx.imageSmoothingEnabled = false
  for (let row = 0; row < 6; row += 1) {
    for (let col = 0; col < 4; col += 1) {
      const x = col * frameWidth
      const y = row * frameHeight
      px(ctx, x + 8, y + 14, 16, 20, '#1c2c48')
      px(ctx, x + 10, y + 4, 12, 10, '#243858')
      px(ctx, x + 12, y + 8, 8, 3, '#5de8ff')
      px(ctx, x + 10, y + 16, 3, 3, '#8c2430')
    }
  }
  // Register as canvas then add frame grid for animations.
  scene.textures.addCanvas(key, c)
  const texture = scene.textures.get(key)
  texture.setFilter(Phaser.Textures.FilterMode.NEAREST)
  let index = 0
  for (let row = 0; row < 6; row += 1) {
    for (let col = 0; col < 4; col += 1) {
      texture.add(index, 0, col * frameWidth, row * frameHeight, frameWidth, frameHeight)
      index += 1
    }
  }
}

/** Guard-layout animations for one enemy / boss sheet, keyed `${prefix}-walk-left` etc. */
export function ensureGuardAnimations(
  scene: Phaser.Scene,
  key: string = COMBAT_TEXTURE_KEYS.guardSheet,
  prefix = 'guard',
): void {
  const mk = (animKey: string, frames: number[], frameRate: number, repeat: number) => {
    if (scene.anims.exists(animKey)) return
    scene.anims.create({
      key: animKey,
      frames: frames.map((frame) => ({ key, frame })),
      frameRate,
      repeat,
    })
  }
  mk(`${prefix}-idle-down`, [GUARD_FRAMES.idleDown], 1, -1)
  mk(`${prefix}-walk-down`, [...GUARD_FRAMES.walkDown], 8, -1)
  mk(`${prefix}-idle-left`, [GUARD_FRAMES.idleLeft], 1, -1)
  mk(`${prefix}-walk-left`, [...GUARD_FRAMES.walkLeft], 8, -1)
  mk(`${prefix}-idle-right`, [GUARD_FRAMES.idleRight], 1, -1)
  mk(`${prefix}-walk-right`, [...GUARD_FRAMES.walkRight], 8, -1)
  mk(`${prefix}-idle-up`, [GUARD_FRAMES.idleUp], 1, -1)
  mk(`${prefix}-walk-up`, [...GUARD_FRAMES.walkUp], 8, -1)
  mk(`${prefix}-attack-down`, [GUARD_FRAMES.attackDown], 8, 0)
  mk(`${prefix}-attack-left`, [GUARD_FRAMES.attackLeft], 8, 0)
  mk(`${prefix}-attack-right`, [GUARD_FRAMES.attackRight], 8, 0)
  mk(`${prefix}-attack-up`, [GUARD_FRAMES.attackUp], 8, 0)
  mk(`${prefix}-hit`, [GUARD_FRAMES.hit], 8, 0)
  mk(`${prefix}-defeat`, [...GUARD_FRAMES.defeat], 6, 0)
}

function makeBossOrb(): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = 10
  c.height = 10
  const ctx = c.getContext('2d')!
  ctx.imageSmoothingEnabled = false
  ctx.clearRect(0, 0, 10, 10)
  px(ctx, 3, 0, 4, 10, '#ff3d6e')
  px(ctx, 0, 3, 10, 4, '#ff3d6e')
  px(ctx, 1, 1, 8, 8, '#ff3d6e')
  px(ctx, 2, 2, 6, 6, '#ff9db5')
  px(ctx, 4, 3, 2, 3, '#fff0f4')
  return c
}

function makeShieldSpark(): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = 14
  c.height = 14
  const ctx = c.getContext('2d')!
  ctx.imageSmoothingEnabled = false
  ctx.clearRect(0, 0, 14, 14)
  px(ctx, 6, 0, 2, 14, '#7af5ff')
  px(ctx, 0, 6, 14, 2, '#7af5ff')
  px(ctx, 3, 3, 8, 8, '#2ad4ff')
  px(ctx, 5, 5, 4, 4, '#e8ffff')
  return c
}

function makeEnemyBullet(): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = 8
  c.height = 4
  const ctx = c.getContext('2d')!
  ctx.imageSmoothingEnabled = false
  ctx.clearRect(0, 0, 8, 4)
  px(ctx, 0, 1, 8, 2, '#ff9a3c')
  px(ctx, 2, 0, 5, 4, '#ffb05e')
  px(ctx, 4, 1, 3, 2, '#fff1c2')
  return c
}

/** Rifleman tracer round. */
export function ensureEnemyShotTexture(scene: Phaser.Scene): void {
  registerCanvas(scene, COMBAT_TEXTURE_KEYS.enemyBullet, makeEnemyBullet())
}

/** Level 7 boss textures: enemy orb projectile and shield-deflect spark. */
export function ensureBossTextures(scene: Phaser.Scene): void {
  registerCanvas(scene, COMBAT_TEXTURE_KEYS.bossOrb, makeBossOrb())
  registerCanvas(scene, COMBAT_TEXTURE_KEYS.shieldSpark, makeShieldSpark())
}
