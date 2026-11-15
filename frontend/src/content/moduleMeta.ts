/** Display-only module/rank labels matching backend catalog focus. */

export const MODULE_TITLES: Record<number, string> = {
  0: 'Agent Training Academy (ungraded orientation)',
  1: 'HTTP methods, endpoints, and parameters',
  2: 'JSON, headers, and response interpretation',
  3: 'HTTPS, simulated authorization, and linked workflows',
}

export const RANK_ORDER = ['RECRUIT', 'FIELD_AGENT', 'SPECIAL_AGENT'] as const

export type RankKey = (typeof RANK_ORDER)[number]

export const RANK_LABELS: Record<RankKey, string> = {
  RECRUIT: 'Recruit',
  FIELD_AGENT: 'Field Agent',
  SPECIAL_AGENT: 'Special Agent',
}

export function moduleLabel(module: number): string {
  return module === 0 ? 'Module 0' : `Module ${String(module).padStart(2, '0')}`
}

export function moduleHeading(module: number): string {
  const title = MODULE_TITLES[module] ?? `Module ${module}`
  return `${moduleLabel(module)} — ${title}`
}

export function normalizeRank(rank: string): RankKey {
  const key = rank.trim().toUpperCase().replace(/[\s-]+/g, '_') as RankKey
  if (RANK_ORDER.includes(key)) return key
  return 'RECRUIT'
}
