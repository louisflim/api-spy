import type { FootprintId } from './types'

/**
 * Deterministic floor-plan selection by mission identity.
 * Replays keep the same footprint because selection depends only on missionId
 * (not roomInstance / apiResetCount). Seeded props may still vary by roomInstance.
 *
 * Module-1 Recruit L1–L6 map to six authored HQ footprints; other missions
 * fall back by level number when present in the id, else Security Office.
 */
export function footprintIdForMission(missionId: string): FootprintId {
  const id = missionId.trim().toLowerCase()

  if (id === 'm1-recruit-l1') return 'security_office'
  if (id === 'm1-recruit-l2') return 'security_annex'
  if (id === 'm1-recruit-l3') return 'security_server_wing'
  if (id === 'm1-recruit-l4') return 'security_checkpoint'
  if (id === 'm1-recruit-l5') return 'security_clearance_hall'
  if (id === 'm1-recruit-l6') return 'security_purge_bay'

  const levelMatch = id.match(/-l(\d+)\b/)
  if (levelMatch) {
    const level = Number(levelMatch[1])
    if (level <= 1) return 'security_office'
    if (level === 2) return 'security_annex'
    if (level === 3) return 'security_server_wing'
    if (level === 4) return 'security_checkpoint'
    if (level === 5) return 'security_clearance_hall'
    if (level === 6) return 'security_purge_bay'
    return 'security_server_wing'
  }

  return 'security_office'
}
