import Phaser from 'phaser'
import { ROOM_TILE_SIZE } from './generateRoomLayout'

type RGBA = [number, number, number, number]

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

function canvas(size: number): { canvas: HTMLCanvasElement; ctx: CanvasRenderingContext2D } {
  const c = document.createElement('canvas')
  c.width = size
  c.height = size
  const ctx = c.getContext('2d')!
  ctx.imageSmoothingEnabled = false
  return { canvas: c, ctx }
}

function register(scene: Phaser.Scene, key: string, c: HTMLCanvasElement): void {
  if (scene.textures.exists(key)) {
    scene.textures.remove(key)
  }
  scene.textures.addCanvas(key, c)
  scene.textures.get(key).setFilter(Phaser.Textures.FilterMode.NEAREST)
}

function makeFloorBase(
  variant: 'base' | 'alt' | 'worn' | 'mark' | 'entrance' | 'checkpoint',
): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  const fills: Record<typeof variant, [string, string, string]> = {
    base: ['#1a2740', '#223351', '#1e2d48'],
    alt: ['#182440', '#1f3150', '#243a58'],
    worn: ['#1c2a44', '#253654', '#1a2740'],
    mark: ['#1a2740', '#223351', '#1e2d48'],
    entrance: ['#1a2740', '#1c3550', '#224060'],
    checkpoint: ['#1a2030', '#2a3348', '#1e2838'],
  }
  const [a, b, d] = fills[variant]
  px(ctx, 0, 0, 40, 40, a)
  px(ctx, 1, 1, 38, 38, b)
  px(ctx, 2, 2, 36, 36, d)
  px(ctx, 0, 0, 40, 1, '#152238')
  px(ctx, 0, 0, 1, 40, '#152238')
  px(ctx, 39, 0, 1, 40, '#2a3f5c')
  px(ctx, 0, 39, 40, 1, '#2a3f5c')
  if (variant === 'alt') {
    px(ctx, 10, 8, 2, 2, '#2f4566')
    px(ctx, 26, 22, 2, 2, '#152238')
  } else if (variant === 'worn') {
    px(ctx, 12, 14, 8, 2, '#152238')
    px(ctx, 20, 24, 6, 2, '#2c4060')
  } else if (variant === 'mark') {
    px(ctx, 4, 18, 32, 4, '#0d4a58')
    px(ctx, 6, 19, 28, 2, '#3ecfe0')
  } else if (variant === 'entrance') {
    px(ctx, 8, 8, 24, 24, '#1a4060')
    px(ctx, 12, 12, 16, 16, '#224e70')
    px(ctx, 18, 4, 4, 32, '#5de8ff')
    px(ctx, 4, 18, 32, 4, '#5de8ff')
  } else if (variant === 'checkpoint') {
    px(ctx, 6, 6, 28, 28, '#243044')
    px(ctx, 10, 10, 20, 20, '#1a6f82')
    px(ctx, 14, 14, 12, 12, '#c4a017')
    px(ctx, 18, 18, 4, 4, '#5de8ff')
  } else {
    px(ctx, 8, 10, 2, 2, '#2c4060')
    px(ctx, 22, 18, 2, 2, '#182640')
    px(ctx, 34, 34, 3, 3, '#1a6f82')
    px(ctx, 35, 35, 1, 1, '#5de8ff')
  }
  return c
}

function makeSecurityLight(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 14, 4, 12, 6, '#4a5568')
  px(ctx, 16, 8, 8, 10, '#2a3344')
  px(ctx, 18, 10, 4, 6, '#5de8ff')
  px(ctx, 17, 18, 6, 2, '#1a6f82')
  return c
}

/** Night courtyard / exterior beyond the HQ footprint (void cells). */
function makeExterior(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  px(ctx, 0, 0, 40, 40, '#070d16')
  px(ctx, 1, 1, 38, 38, '#0a121c')
  px(ctx, 0, 0, 40, 1, '#050910')
  px(ctx, 0, 0, 1, 40, '#050910')
  px(ctx, 39, 0, 1, 40, '#121c2a')
  px(ctx, 0, 39, 40, 1, '#121c2a')
  // Sparse asphalt grit — reads as outside, not a missing tile.
  px(ctx, 8, 12, 2, 2, '#152030')
  px(ctx, 22, 20, 2, 2, '#101820')
  px(ctx, 30, 8, 2, 2, '#182638')
  px(ctx, 14, 28, 3, 1, '#1a2838')
  px(ctx, 26, 32, 2, 2, '#0e1824')
  return c
}

function makeWall(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  // Thick metal plate with baseboard for visual wall depth.
  px(ctx, 0, 0, 40, 40, '#2a3344')
  px(ctx, 3, 2, 34, 30, '#4a5568')
  px(ctx, 5, 4, 30, 24, '#5a687c')
  for (const [x, y] of [
    [8, 6],
    [32, 6],
    [8, 24],
    [32, 24],
    [20, 6],
    [20, 24],
  ]) {
    px(ctx, x, y, 2, 2, '#2a3340')
    px(ctx, x, y, 1, 1, '#9aa6b8')
  }
  px(ctx, 6, 14, 28, 2, '#2f3848')
  px(ctx, 6, 16, 28, 1, '#7a8898')
  px(ctx, 8, 8, 24, 2, '#0d4a58')
  px(ctx, 10, 8, 20, 1, '#3ecfe0')
  // Base trim
  px(ctx, 0, 32, 40, 8, '#1a2230')
  px(ctx, 0, 33, 40, 2, '#c4a017')
  px(ctx, 0, 36, 40, 2, '#0e7a8c')
  return c
}

function makeDesk(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 4, 18, 32, 16, '#3a4558')
  px(ctx, 6, 12, 28, 10, '#5a6878')
  px(ctx, 6, 12, 28, 3, '#7a8898')
  px(ctx, 8, 22, 8, 6, '#2a3344')
  px(ctx, 24, 22, 8, 6, '#2a3344')
  px(ctx, 18, 14, 6, 4, '#1a6f82')
  return c
}

function makeServerRack(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 10, 2, 20, 36, '#2a3344')
  px(ctx, 12, 4, 16, 32, '#3a4558')
  for (const y of [8, 14, 20, 26]) {
    px(ctx, 14, y, 12, 4, '#1a2230')
    px(ctx, 16, y + 1, 2, 2, '#5de8ff')
    px(ctx, 20, y + 1, 2, 2, '#5dcca0')
    px(ctx, 24, y + 1, 2, 2, '#c4a017')
  }
  return c
}

function makeWallCorner(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  px(ctx, 0, 0, 40, 40, '#2e3748')
  px(ctx, 3, 3, 34, 34, '#505c70')
  px(ctx, 0, 0, 12, 12, '#3a4558')
  px(ctx, 28, 28, 12, 12, '#3a4558')
  px(ctx, 14, 14, 12, 12, '#1a6f82')
  px(ctx, 16, 16, 8, 8, '#2a3344')
  px(ctx, 18, 18, 4, 4, '#7de2ff')
  return c
}

function makeTrim(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  px(ctx, 0, 0, 40, 40, '#00000000')
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 0, 30, 40, 10, '#1a2230')
  px(ctx, 0, 32, 40, 4, '#0e7a8c')
  px(ctx, 0, 33, 40, 2, '#5de8ff')
  px(ctx, 0, 36, 40, 4, '#2a3548')
  return c
}

function makeShadow(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  ctx.fillStyle = 'rgba(0,0,0,0.35)'
  ctx.beginPath()
  ctx.ellipse(20, 28, 14, 6, 0, 0, Math.PI * 2)
  ctx.fill()
  return c
}

function makeCrateWood(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 6, 10, 28, 24, '#6b4423')
  px(ctx, 8, 12, 24, 20, '#8a5a30')
  px(ctx, 8, 16, 24, 2, '#5a3818')
  px(ctx, 8, 24, 24, 2, '#5a3818')
  px(ctx, 18, 12, 2, 20, '#5a3818')
  px(ctx, 10, 14, 4, 2, '#c4a574')
  px(ctx, 26, 28, 4, 2, '#3d2810')
  return c
}

function makeCrateMetal(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 6, 10, 28, 24, '#4a5568')
  px(ctx, 8, 12, 24, 20, '#6a788c')
  px(ctx, 8, 12, 24, 3, '#8a9aac')
  px(ctx, 10, 18, 20, 8, '#3a4558')
  px(ctx, 12, 20, 16, 4, '#1a6f82')
  px(ctx, 14, 21, 12, 2, '#5de8ff')
  return c
}

function makeStorageBox(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 8, 14, 24, 20, '#3d4a3a')
  px(ctx, 10, 16, 20, 16, '#5a6e52')
  px(ctx, 10, 16, 20, 4, '#7a9070')
  px(ctx, 16, 28, 8, 3, '#2a3328')
  px(ctx, 18, 29, 4, 1, '#c4a017')
  return c
}

function makeFilingCabinet(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 10, 4, 20, 32, '#3a4558')
  px(ctx, 12, 6, 16, 28, '#5a6878')
  for (const y of [10, 18, 26]) {
    px(ctx, 12, y, 16, 1, '#2a3344')
    px(ctx, 24, y + 2, 3, 2, '#c4a017')
  }
  px(ctx, 12, 6, 16, 2, '#7a8898')
  return c
}

function makeComputerConsole(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 6, 16, 28, 18, '#2a3344')
  px(ctx, 8, 8, 24, 16, '#1a2230')
  px(ctx, 10, 10, 20, 12, '#0a3040')
  px(ctx, 12, 12, 16, 8, '#1a6f82')
  px(ctx, 14, 14, 12, 4, '#5de8ff')
  px(ctx, 10, 28, 6, 3, '#4a5568')
  px(ctx, 24, 28, 6, 3, '#4a5568')
  return c
}

function makeSupplyChest(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  // Decorative only — no reward glow currency icons.
  px(ctx, 6, 14, 28, 20, '#4a3a18')
  px(ctx, 8, 16, 24, 16, '#7a5a20')
  px(ctx, 8, 16, 24, 6, '#9a7a30')
  px(ctx, 18, 22, 4, 4, '#c4a017')
  px(ctx, 8, 20, 24, 2, '#2a2208')
  return c
}

function makeSecurityCamera(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 16, 4, 8, 6, '#4a5568')
  px(ctx, 14, 8, 12, 10, '#2a3344')
  px(ctx, 16, 10, 8, 6, '#1a2230')
  px(ctx, 18, 12, 4, 3, '#5de8ff')
  px(ctx, 28, 10, 3, 3, '#e07070')
  return c
}

function makeMonitorBank(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 4, 8, 32, 26, '#2a3344')
  const screens: Array<[number, number]> = [
    [6, 10],
    [16, 10],
    [26, 10],
    [6, 20],
    [16, 20],
    [26, 20],
  ]
  for (const [x, y] of screens) {
    px(ctx, x, y, 8, 8, '#0a2030')
    px(ctx, x + 1, y + 1, 6, 5, '#1a6f82')
    px(ctx, x + 2, y + 2, 4, 2, '#5de8ff')
  }
  return c
}

function makeMandatoryTerminal(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 6, 8, 28, 28, '#2a3344')
  px(ctx, 8, 10, 24, 18, '#1a2230')
  px(ctx, 10, 12, 20, 14, '#0a2840')
  // Gold/cyan terminal face
  px(ctx, 12, 14, 16, 10, '#c4a017')
  px(ctx, 14, 16, 12, 6, '#5de8ff')
  px(ctx, 16, 18, 8, 2, '#e8f2fb')
  px(ctx, 10, 30, 8, 4, '#4a5568')
  px(ctx, 22, 30, 8, 4, '#4a5568')
  // Status LED
  px(ctx, 30, 12, 3, 3, '#5dcca0')
  return c
}

function makePracticeTerminal(): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  px(ctx, 6, 8, 28, 28, '#2a3a55')
  px(ctx, 8, 10, 24, 18, '#1a2840')
  px(ctx, 10, 12, 20, 14, '#143050')
  px(ctx, 12, 14, 16, 10, '#4a6fa5')
  px(ctx, 14, 16, 12, 6, '#7db0e8')
  px(ctx, 10, 30, 8, 4, '#3a4a68')
  px(ctx, 22, 30, 8, 4, '#3a4a68')
  px(ctx, 30, 12, 3, 3, '#7de2ff')
  return c
}

function makeExitDoor(unlocked: boolean): HTMLCanvasElement {
  const { canvas: c, ctx } = canvas(ROOM_TILE_SIZE)
  ctx.clearRect(0, 0, 40, 40)
  // Door frame
  px(ctx, 8, 2, 24, 36, '#3a4558')
  px(ctx, 10, 4, 20, 32, '#2a3344')
  const panel = unlocked ? '#2f6b4a' : '#6b2a2a'
  const light = unlocked ? '#5dcca0' : '#e07070'
  const glow = unlocked ? '#8aefc0' : '#f0a0a0'
  px(ctx, 12, 6, 16, 28, panel)
  px(ctx, 14, 8, 12, 20, unlocked ? '#3d8b6e' : '#8b3a3a')
  // Window slit
  px(ctx, 16, 12, 8, 6, '#1a2230')
  px(ctx, 18, 14, 4, 2, light)
  // Indicator lamp
  px(ctx, 18, 28, 4, 4, light)
  px(ctx, 19, 29, 2, 2, glow)
  // Handle
  px(ctx, 26, 18, 2, 6, '#c4a017')
  return c
}

const TEXTURE_BUILDERS: Record<string, () => HTMLCanvasElement> = {
  'spy-floor': () => makeFloorBase('base'),
  'spy-floor-alt': () => makeFloorBase('alt'),
  'spy-floor-worn': () => makeFloorBase('worn'),
  'spy-floor-mark': () => makeFloorBase('mark'),
  'spy-floor-entrance': () => makeFloorBase('entrance'),
  'spy-floor-checkpoint': () => makeFloorBase('checkpoint'),
  'spy-exterior': makeExterior,
  'spy-wall': makeWall,
  'spy-wall-corner': makeWallCorner,
  'spy-trim': makeTrim,
  'spy-shadow': makeShadow,
  'spy-crate-wood': makeCrateWood,
  'spy-crate-metal': makeCrateMetal,
  'spy-storage-box': makeStorageBox,
  'spy-filing-cabinet': makeFilingCabinet,
  'spy-computer-console': makeComputerConsole,
  'spy-supply-chest': makeSupplyChest,
  'spy-security-camera': makeSecurityCamera,
  'spy-security-light': makeSecurityLight,
  'spy-monitor-bank': makeMonitorBank,
  'spy-desk': makeDesk,
  'spy-server-rack': makeServerRack,
  'spy-terminal-mandatory': makeMandatoryTerminal,
  'spy-terminal-practice': makePracticeTerminal,
  'spy-exit-locked': () => makeExitDoor(false),
  'spy-exit-unlocked': () => makeExitDoor(true),
}

export const PROP_TEXTURE_KEYS: Record<string, string> = {
  crate_wood: 'spy-crate-wood',
  crate_metal: 'spy-crate-metal',
  storage_box: 'spy-storage-box',
  filing_cabinet: 'spy-filing-cabinet',
  computer_console: 'spy-computer-console',
  supply_chest: 'spy-supply-chest',
  security_camera: 'spy-security-camera',
  security_light: 'spy-security-light',
  monitor_bank: 'spy-monitor-bank',
  desk: 'spy-desk',
  server_rack: 'spy-server-rack',
}

export const FLOOR_TEXTURE_KEYS: Record<string, string> = {
  base: 'spy-floor',
  alt: 'spy-floor-alt',
  worn: 'spy-floor-worn',
  mark: 'spy-floor-mark',
  entrance: 'spy-floor-entrance',
  checkpoint: 'spy-floor-checkpoint',
}

/** Build original procedural pixel-art textures once per scene boot. */
export function ensureRoomTextures(scene: Phaser.Scene): void {
  for (const [key, builder] of Object.entries(TEXTURE_BUILDERS)) {
    if (scene.textures.exists(key)) continue
    register(scene, key, builder())
  }
}

/** Silence unused RGBA helper warning path for future palette work. */
export type { RGBA }
