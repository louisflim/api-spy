import { normalizeRank } from '../content/moduleMeta'
import type { MissionProgressState, MissionSummary } from '../types/api'

/**
 * Module 1 Recruit sequential unlock (mirrors backend MissionProgressService).
 * Field/Special Agent and other modules are not gated here.
 */
export function priorRecruitMissionId(mission: MissionSummary): string | null {
  if (mission.module !== 1 || normalizeRank(mission.rank) !== 'RECRUIT' || mission.level <= 1) {
    return null
  }
  return `m1-recruit-l${mission.level - 1}`
}

export function isCareerUnlocked(
  mission: MissionSummary,
  serverByMission: Map<string, MissionProgressState>,
): boolean {
  const priorId = priorRecruitMissionId(mission)
  if (!priorId) {
    return true
  }
  const prior = serverByMission.get(priorId)
  return Boolean(prior?.completed || prior?.status === 'COMPLETED')
}
