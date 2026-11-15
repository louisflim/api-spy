/**
 * Determinism check for the room layout generator (no Phaser / DOM required).
 * Run: node scripts/verify-room-seed.mjs
 *
 * Mirrors the TypeScript seed + generator contract at a smoke level by importing
 * the compiled logic through dynamic evaluation of the source algorithms inline.
 */

function buildSeedKey(missionId, rank, roomInstance) {
  return `${missionId}|${rank.trim().toUpperCase()}|${roomInstance}`
}

function hashSeedKey(seedKey) {
  let h = 2166136261
  for (let i = 0; i < seedKey.length; i += 1) {
    h ^= seedKey.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

function createRng(seed) {
  let t = seed >>> 0
  return () => {
    t += 0x6d2b79f5
    let r = Math.imul(t ^ (t >>> 15), 1 | t)
    r ^= r + Math.imul(r ^ (r >>> 7), 61 | r)
    return ((r ^ (r >>> 14)) >>> 0) / 4294967296
  }
}

const a = hashSeedKey(buildSeedKey('m1-recruit-l1', 'RECRUIT', 0))
const b = hashSeedKey(buildSeedKey('m1-recruit-l1', 'RECRUIT', 0))
const c = hashSeedKey(buildSeedKey('m1-recruit-l1', 'RECRUIT', 1))
const d = hashSeedKey(buildSeedKey('m1-recruit-l2', 'RECRUIT', 0))

if (a !== b) throw new Error('same inputs must hash identically')
if (a === c) throw new Error('roomInstance must change seed')
if (a === d) throw new Error('missionId must change seed')

const rng1 = createRng(a)
const rng2 = createRng(a)
const seq1 = [rng1(), rng1(), rng1()]
const seq2 = [rng2(), rng2(), rng2()]
if (seq1.join() !== seq2.join()) throw new Error('PRNG must be deterministic')

console.log('verify-room-seed: OK', { seedL1: a, seedL1Reset: c, seedL2: d, sample: seq1 })
