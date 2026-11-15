/**
 * Derive a stable numeric seed from mission identity + room instance.
 * Same inputs ⇒ same seed ⇒ same layout. Different roomInstance (API reset) ⇒ new layout.
 */
export function buildSeedKey(missionId: string, rank: string, roomInstance: number): string {
  return `${missionId}|${rank.trim().toUpperCase()}|${roomInstance}`
}

export function hashSeedKey(seedKey: string): number {
  let h = 2166136261
  for (let i = 0; i < seedKey.length; i += 1) {
    h ^= seedKey.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

export function deriveRoomSeed(missionId: string, rank: string, roomInstance: number): {
  seedKey: string
  seed: number
} {
  const seedKey = buildSeedKey(missionId, rank, roomInstance)
  return { seedKey, seed: hashSeedKey(seedKey) }
}
