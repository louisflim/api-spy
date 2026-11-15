/**
 * Renders wall/void/floor previews for L1–L3 footprints (no Phaser required).
 * Output: ../assets/playtest/footprint-l{1,2,3}.png and footprint-compare.png
 */
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

// Load compiled? Use inline map copies matching authored footprints.
const __dirname = dirname(fileURLToPath(import.meta.url))
const outDir = join(__dirname, '..', '..', 'assets', 'playtest')
mkdirSync(outDir, { recursive: true })

const MAPS = {
  l1: {
    label: 'L1 Security Office',
    exit: 'east',
    map: [
      '####################',
      '#..................#',
      '#..................#',
      '#..........#####...#',
      '#..........#...#...#',
      '#..........#...##..#',
      '#..........#.......#',
      '#..................#',
      '#..................#',
      '#..........###.....#',
      '#..........#..#....#',
      '#..........#.......#',
      '#..................#',
      '#...............##~~',
      '################~~~~',
    ],
  },
  l2: {
    label: 'L2 Security Annex',
    exit: 'south',
    map: [
      '####################',
      '#..................#',
      '#....###..###......#',
      '#....#......#......#',
      '#....#......#......#',
      '#....###..###......#',
      '#..................#',
      '#..................#',
      '#....###..###......#',
      '#....#......#......#',
      '#....#......#......#',
      '#....###..###......#',
      '#..................#',
      '#...............##~~',
      '################~~~~',
    ],
  },
  l3: {
    label: 'L3 Server Wing',
    exit: 'northeast',
    map: [
      '####################',
      '#...#..........##~~~',
      '#...#..........#~~~~',
      '#...####..######~~~~',
      '#......#..#........#',
      '#......#..#........#',
      '####...#..#...######',
      '#......#..#........#',
      '#......#..#........#',
      '####...#..#...######',
      '#......#..#........#',
      '#......#..#........#',
      '#..................#',
      '#...............##~~',
      '################~~~~',
    ],
  },
}

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

function renderMap(map, scale = 12) {
  const cols = map[0].length
  const rows = map.length
  const w = cols * scale
  const h = rows * scale
  const rgba = Buffer.alloc(w * h * 4, 0)
  const paint = (x, y, r, g, b) => {
    const i = (y * w + x) * 4
    rgba[i] = r
    rgba[i + 1] = g
    rgba[i + 2] = b
    rgba[i + 3] = 255
  }
  for (let row = 0; row < rows; row += 1) {
    for (let col = 0; col < cols; col += 1) {
      const ch = map[row][col]
      let r = 26,
        g = 39,
        b = 64 // floor
      if (ch === '#') {
        r = 74
        g = 85
        b = 104 // wall
      } else if (ch === '~') {
        r = 5
        g = 11
        b = 22 // void
      }
      for (let dy = 0; dy < scale; dy += 1) {
        for (let dx = 0; dx < scale; dx += 1) {
          paint(col * scale + dx, row * scale + dy, r, g, b)
        }
      }
    }
  }
  return { rgba, w, h }
}

const previews = []
for (const [key, def] of Object.entries(MAPS)) {
  const { rgba, w, h } = renderMap(def.map)
  const path = join(outDir, `footprint-${key}.png`)
  writeFileSync(path, encodePng(rgba, w, h))
  previews.push({ key, def, rgba, w, h, path })
  console.log('wrote', path, def.label, `exit=${def.exit}`)
}

// Side-by-side compare
const gap = 16
const scale = 12
const cellW = 20 * scale
const cellH = 15 * scale
const cw = cellW * 3 + gap * 4
const ch = cellH + gap * 2
const compare = Buffer.alloc(cw * ch * 4, 0)
// dark bg
for (let i = 0; i < compare.length; i += 4) {
  compare[i] = 5
  compare[i + 1] = 11
  compare[i + 2] = 22
  compare[i + 3] = 255
}
previews.forEach((p, idx) => {
  const ox = gap + idx * (cellW + gap)
  const oy = gap
  for (let y = 0; y < p.h; y += 1) {
    for (let x = 0; x < p.w; x += 1) {
      const si = (y * p.w + x) * 4
      const di = ((oy + y) * cw + (ox + x)) * 4
      compare[di] = p.rgba[si]
      compare[di + 1] = p.rgba[si + 1]
      compare[di + 2] = p.rgba[si + 2]
      compare[di + 3] = 255
    }
  }
})
const comparePath = join(outDir, 'footprint-l1-l2-l3-compare.png')
writeFileSync(comparePath, encodePng(compare, cw, ch))
console.log('wrote', comparePath)
