/**
 * Original Security Patrol Guard spritesheet — procedural pixel art (not a palette-swap of Rookie).
 * Writes: public/assets/enemies/security-patrol-guard.png
 *
 * Frame grid: 4 cols × 6 rows, 32×40 px each (total 128×240).
 * Rows: idle+walk down / left / right / up; attack; hit+defeat.
 *
 * Run: node scripts/generate-patrol-guard-sheet.mjs
 */
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FW = 32
const FH = 40
const COLS = 4
const ROWS = 6
const W = FW * COLS
const H = FH * ROWS

function crc32(buf) {
  let c = ~0
  for (let i = 0; i < buf.length; i += 1) {
    c ^= buf[i]
    for (let k = 0; k < 8; k += 1) c = c & 1 ? (0xedb88320 ^ (c >>> 1)) : c >>> 1
  }
  return ~c >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const typeBuf = Buffer.from(type)
  const crcBuf = Buffer.alloc(4)
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])))
  return Buffer.concat([len, typeBuf, data, crcBuf])
}

function encodePng(rgba, width, height) {
  const raw = Buffer.alloc((width * 4 + 1) * height)
  for (let y = 0; y < height; y += 1) {
    const rowStart = y * (width * 4 + 1)
    raw[rowStart] = 0
    rgba.copy(raw, rowStart + 1, y * width * 4, (y + 1) * width * 4)
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

function px(rgba, ox, oy, x, y, r, g, b, a = 255) {
  const sx = ox + x
  const sy = oy + y
  if (sx < 0 || sy < 0 || sx >= W || sy >= H) return
  const i = (sy * W + sx) * 4
  rgba[i] = r
  rgba[i + 1] = g
  rgba[i + 2] = b
  rgba[i + 3] = a
}

function fillRect(rgba, ox, oy, x, y, w, h, r, g, b, a = 255) {
  for (let dy = 0; dy < h; dy += 1) {
    for (let dx = 0; dx < w; dx += 1) px(rgba, ox, oy, x + dx, y + dy, r, g, b, a)
  }
}

/** Draw one guard pose into a frame cell. */
function drawGuard(rgba, col, row, opts) {
  const ox = col * FW
  const oy = row * FH
  const { facing = 'down', walk = 0, attack = false, hit = false, defeat = 0 } = opts

  if (defeat > 0) {
    const fade = Math.max(40, 220 - defeat * 70)
    fillRect(rgba, ox, oy, 10, 18 + defeat * 2, 12, 8, 30, 40, 60, fade)
    fillRect(rgba, ox, oy, 12, 10 + defeat, 8, 8, 40, 50, 70, fade)
    fillRect(rgba, ox, oy, 13, 12 + defeat, 6, 2, 180, 40, 40, fade)
    return
  }

  const bob = walk === 1 || walk === 3 ? 1 : 0
  const legSpread = walk === 0 ? 0 : walk === 2 ? 2 : 1
  const lean = facing === 'left' ? -1 : facing === 'right' ? 1 : 0

  // Boots
  fillRect(rgba, ox, oy, 9 - legSpread + lean, 34 + bob, 5, 4, 12, 16, 28)
  fillRect(rgba, ox, oy, 18 + legSpread + lean, 34 + (walk ? 1 - bob : bob), 5, 4, 12, 16, 28)
  // Legs — navy
  fillRect(rgba, ox, oy, 10 - Math.floor(legSpread / 2) + lean, 26 + bob, 5, 9, 22, 32, 58)
  fillRect(rgba, ox, oy, 17 + Math.floor(legSpread / 2) + lean, 26 + bob, 5, 9, 22, 32, 58)
  // Torso — dark navy uniform
  fillRect(rgba, ox, oy, 8 + lean, 14 + bob, 16, 13, 18, 28, 52)
  fillRect(rgba, ox, oy, 9 + lean, 15 + bob, 14, 10, 28, 42, 72)
  // Utility belt
  fillRect(rgba, ox, oy, 8 + lean, 24 + bob, 16, 3, 14, 18, 28)
  fillRect(rgba, ox, oy, 14 + lean, 24 + bob, 4, 3, 90, 100, 120)
  // Muted red insignia
  fillRect(rgba, ox, oy, 10 + lean, 16 + bob, 3, 3, 140, 36, 42)
  fillRect(rgba, ox, oy, 11 + lean, 17 + bob, 1, 1, 180, 60, 60)
  // Arms
  const armY = 15 + bob + (attack ? 2 : 0)
  if (facing === 'left') {
    fillRect(rgba, ox, oy, 3, armY, 5, 10, 18, 28, 52)
    fillRect(rgba, ox, oy, 24, armY, 4, 9, 18, 28, 52)
    if (attack) fillRect(rgba, ox, oy, 1, armY + 6, 6, 3, 40, 50, 70)
  } else if (facing === 'right') {
    fillRect(rgba, ox, oy, 24, armY, 5, 10, 18, 28, 52)
    fillRect(rgba, ox, oy, 4, armY, 4, 9, 18, 28, 52)
    if (attack) fillRect(rgba, ox, oy, 25, armY + 6, 6, 3, 40, 50, 70)
  } else if (facing === 'up') {
    fillRect(rgba, ox, oy, 4 + lean, armY, 4, 10, 18, 28, 52)
    fillRect(rgba, ox, oy, 24 + lean, armY, 4, 10, 18, 28, 52)
  } else {
    fillRect(rgba, ox, oy, 4 + lean, armY, 4, 10, 18, 28, 52)
    fillRect(rgba, ox, oy, 24 + lean, armY, 4, 10, 18, 28, 52)
    if (attack) fillRect(rgba, ox, oy, 14 + lean, 28 + bob, 4, 6, 40, 50, 70)
  }
  // Helmet / security cap
  fillRect(rgba, ox, oy, 10 + lean, 4 + bob, 12, 10, 16, 24, 40)
  fillRect(rgba, ox, oy, 11 + lean, 5 + bob, 10, 7, 28, 40, 58)
  fillRect(rgba, ox, oy, 9 + lean, 8 + bob, 14, 3, 12, 18, 32) // brim
  // Visor strip
  if (facing !== 'up') {
    fillRect(rgba, ox, oy, 12 + lean, 8 + bob, 8, 3, 40, 180, 200)
    fillRect(rgba, ox, oy, 13 + lean, 9 + bob, 6, 1, 180, 240, 255)
  } else {
    fillRect(rgba, ox, oy, 12 + lean, 6 + bob, 8, 2, 16, 24, 40)
  }
  if (hit) {
    fillRect(rgba, ox, oy, 6 + lean, 2 + bob, 4, 4, 255, 80, 80, 200)
    fillRect(rgba, ox, oy, 22 + lean, 4 + bob, 3, 3, 255, 80, 80, 180)
  }
}

const rgba = Buffer.alloc(W * H * 4, 0)
const facings = ['down', 'left', 'right', 'up']
for (let r = 0; r < 4; r += 1) {
  const facing = facings[r]
  drawGuard(rgba, 0, r, { facing, walk: 0 })
  drawGuard(rgba, 1, r, { facing, walk: 1 })
  drawGuard(rgba, 2, r, { facing, walk: 2 })
  drawGuard(rgba, 3, r, { facing, walk: 3 })
}
// Attack row
for (let c = 0; c < 4; c += 1) drawGuard(rgba, c, 4, { facing: facings[c], attack: true })
// Hit + defeat
drawGuard(rgba, 0, 5, { facing: 'down', hit: true })
drawGuard(rgba, 1, 5, { facing: 'down', defeat: 1 })
drawGuard(rgba, 2, 5, { facing: 'down', defeat: 2 })
drawGuard(rgba, 3, 5, { facing: 'down', defeat: 3 })

const outDir = join(__dirname, '..', 'public', 'assets', 'enemies')
mkdirSync(outDir, { recursive: true })
const outPath = join(outDir, 'security-patrol-guard.png')
writeFileSync(outPath, encodePng(rgba, W, H))
console.log(`Wrote ${outPath} (${W}x${H}, ${COLS}x${ROWS} frames of ${FW}x${FH})`)
