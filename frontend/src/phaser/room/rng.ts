/** Deterministic 32-bit PRNG (Mulberry32). */
export type Rng = () => number

export function createRng(seed: number): Rng {
  let t = seed >>> 0
  return () => {
    t += 0x6d2b79f5
    let r = Math.imul(t ^ (t >>> 15), 1 | t)
    r ^= r + Math.imul(r ^ (r >>> 7), 61 | r)
    return ((r ^ (r >>> 14)) >>> 0) / 4294967296
  }
}

export function rngInt(rng: Rng, minInclusive: number, maxExclusive: number): number {
  return Math.floor(rng() * (maxExclusive - minInclusive)) + minInclusive
}

export function rngPick<T>(rng: Rng, items: readonly T[]): T {
  return items[rngInt(rng, 0, items.length)]!
}

export function rngShuffle<T>(rng: Rng, items: T[]): T[] {
  const copy = [...items]
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = rngInt(rng, 0, i + 1)
    const tmp = copy[i]!
    copy[i] = copy[j]!
    copy[j] = tmp
  }
  return copy
}
