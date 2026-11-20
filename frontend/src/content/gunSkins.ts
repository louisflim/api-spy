import type { RankKey } from './moduleMeta'
import { GUN_SPRITE_META } from './gunSpriteMeta'
import { rankUnlockStatus, type OutfitStatus } from './outfits'

/**
 * Gun skins (proposal): the standard finish for everyone, plus a Recruit, Field Agent and
 * Special Agent gun skin awarded with each rank medal (all 21 missions of that rank) and a
 * Legendary gun skin with the Master Spy Award (all 63 missions). A skin recolours whichever
 * gun is equipped. Cosmetic only — never changes damage, API requests or research scores.
 */
export type GunSkinId = 'standard' | 'recruit' | 'field' | 'special' | 'legendary'

export interface GunSkin {
  id: GunSkinId
  name: string
  description: string
  unlock: { kind: 'free' } | { kind: 'rank'; rank: RankKey } | { kind: 'all' }
}

export const GUN_SKINS: GunSkin[] = [
  { id: 'standard', name: 'Standard Issue', description: 'Gunmetal and walnut.', unlock: { kind: 'free' } },
  {
    id: 'recruit',
    name: 'Bronze Recruit',
    description: 'Bronze plating awarded with the Recruit Medal.',
    unlock: { kind: 'rank', rank: 'RECRUIT' },
  },
  {
    id: 'field',
    name: 'Silver Field',
    description: 'Polished silver awarded with the Field Agent Medal.',
    unlock: { kind: 'rank', rank: 'FIELD_AGENT' },
  },
  {
    id: 'special',
    name: 'Gold Special',
    description: 'Gold finish awarded with the Special Agent Medal.',
    unlock: { kind: 'rank', rank: 'SPECIAL_AGENT' },
  },
  {
    id: 'legendary',
    name: 'Legendary Engraved',
    description: 'Blacked-out with gold engraving, awarded with the Master Spy Award.',
    unlock: { kind: 'all' },
  },
]

export function findGunSkin(id: string | null | undefined): GunSkin {
  return GUN_SKINS.find((s) => s.id === id) ?? GUN_SKINS[0]!
}

export function gunSkinStatus(skin: GunSkin, cleared: Set<string>): OutfitStatus {
  if (skin.unlock.kind === 'free') {
    return { unlocked: true, completed: 0, required: 0, requirement: 'Available from the start' }
  }
  return rankUnlockStatus(skin.unlock, cleared)
}

/** Texture for a gun in a skin (falls back to the pistol for unknown guns). */
export function gunSprite(weaponId: string | null | undefined, skinId: GunSkinId) {
  const gun = weaponId && GUN_SPRITE_META[weaponId] ? weaponId : 'starter_sidearm'
  const meta = GUN_SPRITE_META[gun]!
  return {
    key: `gun-${gun}-${skinId}`,
    path: `/assets/weapons/${gun}-${skinId}.png`,
    gripX: meta.gripX,
    gripY: meta.gripY,
    width: meta.width,
    height: meta.height,
  }
}

const storageKey = (userId: string) => `apispy.gunskin.${userId}`

export function loadSelectedGunSkinId(userId: string): GunSkinId {
  try {
    return findGunSkin(localStorage.getItem(storageKey(userId))).id
  } catch {
    return 'standard'
  }
}

export function saveSelectedGunSkinId(userId: string, id: GunSkinId): void {
  try {
    localStorage.setItem(storageKey(userId), id)
  } catch {
    /* storage unavailable: selection lasts for this page only */
  }
}

/** Selected gun skin if this agent has unlocked it, otherwise the standard finish. */
export function effectiveGunSkin(userId: string, cleared: Set<string>): GunSkin {
  const selected = findGunSkin(loadSelectedGunSkinId(userId))
  return gunSkinStatus(selected, cleared).unlocked ? selected : GUN_SKINS[0]!
}
