import { normalizeRank, type RankKey } from '../content/moduleMeta'
import type { MissionProgressState, MissionSummary } from '../types/api'

const RANK_SLUG: Record<RankKey, string> = {
  RECRUIT: 'recruit',
  FIELD_AGENT: 'field',
  SPECIAL_AGENT: 'special',
}

/**
 * Mission that must be career-cleared first (mirrors backend
 * MissionProgressService.priorCareerMissionId):
 * - Level N needs Level N-1 in the same module and rank.
 * - Module 1 Field Agent L1 needs Recruit M1 L7; Special Agent L1 needs Field Agent M1 L7.
 * - Module 2/3 Level 1 needs the previous module's L7 in the same rank.
 */
export function priorCareerMissionId(mission: MissionSummary): string | null {
  const rank = normalizeRank(mission.rank)
  if (mission.level > 1) {
    return `m${mission.module}-${RANK_SLUG[rank]}-l${mission.level - 1}`
  }
  if (mission.module > 1) {
    return `m${mission.module - 1}-${RANK_SLUG[rank]}-l7`
  }
  if (rank === 'FIELD_AGENT') return 'm1-recruit-l7'
  if (rank === 'SPECIAL_AGENT') return 'm1-field-l7'
  return null
}

/** @deprecated use {@link priorCareerMissionId}; kept for existing imports. */
export const priorRecruitMissionId = priorCareerMissionId

export function isCareerUnlocked(
  mission: MissionSummary,
  serverByMission: Map<string, MissionProgressState>,
): boolean {
  const priorId = priorCareerMissionId(mission)
  if (!priorId) {
    return true
  }
  const prior = serverByMission.get(priorId)
  return Boolean(prior?.completed || prior?.status === 'COMPLETED')
}
