import { RANK_LABELS, type RankKey } from './moduleMeta'
import type { MissionProgressState } from '../types/api'

/**
 * Playable agent outfits. All sheets share the Rookie Detective layout
 * (4x4 frames of 272px: rows down/left/right/up; columns idle + 3 walk frames) and are
 * derived from the Rookie art by scripts/derive-sprites.py.
 *
 * Starter characters (Rookie Detective, Agent Leo, Agent Mira): a new agent picks one for free;
 * the other two are bought with in-game coins (ownership lives on the server, /api/rewards).
 * Rank and legendary skins follow the approved proposal: completing all 21 missions of a rank
 * awards that rank's skin; completing all 63 missions awards the legendary skin. Outfits are
 * cosmetic only: they never change combat, API validation or research records.
 */
export type OutfitId =
  | 'rookie'
  | 'agent-boy'
  | 'agent-girl'
  | 'skin-recruit'
  | 'skin-field'
  | 'skin-special'
  | 'skin-baba-yaga'

export type OutfitTier = 'starter' | 'rank' | 'legendary'

/** character = starter agent owned via the free pick or bought with coins. */
export type OutfitUnlock = { kind: 'character' } | { kind: 'rank'; rank: RankKey } | { kind: 'all' }

export interface Outfit {
  id: OutfitId
  name: string
  tier: OutfitTier
  sheet: string
  description: string
  unlock: OutfitUnlock
}

export const MISSIONS_PER_RANK = 21
export const MISSIONS_TOTAL = 63

export const OUTFITS: Outfit[] = [
  {
    id: 'rookie',
    name: 'Rookie Detective',
    tier: 'starter',
    sheet: '/assets/characters/rookie-detective.png',
    description: 'The classic trench coat and fedora.',
    unlock: { kind: 'character' },
  },
  {
    id: 'agent-boy',
    name: 'Agent Leo',
    tier: 'starter',
    sheet: '/assets/characters/agent-boy.png',
    description: 'Navy fedora, harbour-blue coat and a gold tie.',
    unlock: { kind: 'character' },
  },
  {
    id: 'agent-girl',
    name: 'Agent Mira',
    tier: 'starter',
    sheet: '/assets/characters/agent-girl.png',
    description: 'Burgundy coat, long auburn hair and a ribboned hat.',
    unlock: { kind: 'character' },
  },
  {
    id: 'skin-recruit',
    name: 'Bronze Cadet',
    tier: 'rank',
    sheet: '/assets/characters/skin-recruit.png',
    description: 'Bronze trench awarded with the Recruit Medal.',
    unlock: { kind: 'rank', rank: 'RECRUIT' },
  },
  {
    id: 'skin-field',
    name: 'Silver Fox',
    tier: 'rank',
    sheet: '/assets/characters/skin-field.png',
    description: 'Silver field coat awarded with the Field Agent Medal.',
    unlock: { kind: 'rank', rank: 'FIELD_AGENT' },
  },
  {
    id: 'skin-special',
    name: 'Gold Phantom',
    tier: 'rank',
    sheet: '/assets/characters/skin-special.png',
    description: 'Midnight coat with a gold tie, awarded with the Special Agent Medal.',
    unlock: { kind: 'rank', rank: 'SPECIAL_AGENT' },
  },
  {
    id: 'skin-baba-yaga',
    name: 'Baba Yaga',
    tier: 'legendary',
    sheet: '/assets/characters/skin-baba-yaga.png',
    description: 'Legendary. The black suit worn by the agent who cleared every mission.',
    unlock: { kind: 'all' },
  },
]

const RANK_SLUG: Record<RankKey, string> = { RECRUIT: 'recruit', FIELD_AGENT: 'field', SPECIAL_AGENT: 'special' }

export interface OutfitStatus {
  unlocked: boolean
  completed: number
  required: number
  requirement: string
}

function isCleared(row: MissionProgressState): boolean {
  return Boolean(row.completed || row.status === 'COMPLETED')
}

/** Career-cleared mission ids (replays never remove a clear). */
export function clearedMissionIds(rows: MissionProgressState[]): Set<string> {
  return new Set(rows.filter(isCleared).map((row) => row.missionId))
}

/** Starter characters this agent owns (free pick + purchases), from /api/rewards. */
export type OwnedCharacters = ReadonlySet<string>

/**
 * Unlock state for an outfit. Starter characters need ownership (null = ownership unknown yet,
 * treated as locked); rank / legendary skins need cleared missions.
 */
export function outfitStatus(
  outfit: Outfit,
  cleared: Set<string>,
  ownedCharacters: OwnedCharacters | null = null,
): OutfitStatus {
  if (outfit.unlock.kind === 'character') {
    const owned = Boolean(ownedCharacters?.has(outfit.id))
    return { unlocked: owned, completed: owned ? 1 : 0, required: 1, requirement: 'Buy with coins' }
  }
  return rankUnlockStatus(outfit.unlock, cleared)
}

/** Shared rank / all-missions unlock rule (outfits and gun skins). */
export function rankUnlockStatus(
  unlock: { kind: 'rank'; rank: RankKey } | { kind: 'all' },
  cleared: Set<string>,
): OutfitStatus {
  if (unlock.kind === 'rank') {
    const slug = RANK_SLUG[unlock.rank]
    const completed = [...cleared].filter((id) => new RegExp(`^m[1-3]-${slug}-l[1-7]$`).test(id)).length
    return {
      unlocked: completed >= MISSIONS_PER_RANK,
      completed,
      required: MISSIONS_PER_RANK,
      requirement: `Complete all ${MISSIONS_PER_RANK} ${RANK_LABELS[unlock.rank]} missions`,
    }
  }
  const completed = [...cleared].filter((id) => /^m[1-3]-(recruit|field|special)-l[1-7]$/.test(id)).length
  return {
    unlocked: completed >= MISSIONS_TOTAL,
    completed,
    required: MISSIONS_TOTAL,
    requirement: `Complete all ${MISSIONS_TOTAL} missions`,
  }
}

export function findOutfit(id: string | null | undefined): Outfit {
  return OUTFITS.find((o) => o.id === id) ?? OUTFITS[0]!
}

const storageKey = (userId: string) => `apispy.outfit.${userId}`

/** Selected outfit id for this agent (per account, per browser). Cosmetic only. */
export function loadSelectedOutfitId(userId: string): OutfitId {
  try {
    const raw = localStorage.getItem(storageKey(userId))
    return findOutfit(raw).id
  } catch {
    return 'rookie'
  }
}

export function saveSelectedOutfitId(userId: string, id: OutfitId): void {
  try {
    localStorage.setItem(storageKey(userId), id)
  } catch {
    /* storage unavailable: selection lasts for this page only */
  }
}

/**
 * Selected outfit if this agent still has it; otherwise their first owned starter character,
 * otherwise the Rookie Detective (e.g. before the free pick or if rewards could not load).
 */
export function effectiveOutfit(
  userId: string,
  cleared: Set<string>,
  ownedCharacters: OwnedCharacters | null = null,
): Outfit {
  const selected = findOutfit(loadSelectedOutfitId(userId))
  if (outfitStatus(selected, cleared, ownedCharacters).unlocked) return selected
  const firstOwned = OUTFITS.find((o) => o.unlock.kind === 'character' && ownedCharacters?.has(o.id))
  return firstOwned ?? OUTFITS[0]!
}
