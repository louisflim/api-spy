import type { FootprintId, RoomFootprint } from '../types'
import { SECURITY_ANNEX_FOOTPRINT } from './securityAnnex'
import { SECURITY_CHECKPOINT_FOOTPRINT } from './securityCheckpoint'
import { SECURITY_CLEARANCE_HALL_FOOTPRINT } from './securityClearanceHall'
import { SECURITY_OFFICE_FOOTPRINT } from './securityOffice'
import { SECURITY_PURGE_BAY_FOOTPRINT } from './securityPurgeBay'
import { SECURITY_SERVER_WING_FOOTPRINT } from './securityServerWing'

const REGISTRY: Record<FootprintId, RoomFootprint> = {
  security_office: SECURITY_OFFICE_FOOTPRINT,
  security_annex: SECURITY_ANNEX_FOOTPRINT,
  security_server_wing: SECURITY_SERVER_WING_FOOTPRINT,
  security_checkpoint: SECURITY_CHECKPOINT_FOOTPRINT,
  security_clearance_hall: SECURITY_CLEARANCE_HALL_FOOTPRINT,
  security_purge_bay: SECURITY_PURGE_BAY_FOOTPRINT,
}

export function getFootprint(id: FootprintId = 'security_office'): RoomFootprint {
  return REGISTRY[id] ?? SECURITY_OFFICE_FOOTPRINT
}

export function listFootprintIds(): FootprintId[] {
  return Object.keys(REGISTRY) as FootprintId[]
}

export {
  SECURITY_OFFICE_FOOTPRINT,
  SECURITY_ANNEX_FOOTPRINT,
  SECURITY_SERVER_WING_FOOTPRINT,
  SECURITY_CHECKPOINT_FOOTPRINT,
  SECURITY_CLEARANCE_HALL_FOOTPRINT,
  SECURITY_PURGE_BAY_FOOTPRINT,
}
