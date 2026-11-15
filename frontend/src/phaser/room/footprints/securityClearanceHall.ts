import type { RoomFootprint } from '../types'
import { assertFootprintDims, parseFootprintMap, propZone } from './mapParse'

/**
 * Intelligence HQ — Clearance Hall (Module 1 Recruit L5)
 * Long east–west hall with staggered north/south alcoves for update terminals.
 *
 * Legend: # wall · . floor · ~ void
 */
const MAP = [
  //                 01234567890123456789
  '####################', // 0
  '#..#....#....#.....#', // 1 north alcoves
  '#..#....#....#.....#', // 2
  '#..................#', // 3
  '#..................#', // 4 main hall
  '#..................#', // 5
  '#..#....#....#.....#', // 6 south alcove roofs
  '#..#....#....#.....#', // 7
  '#..................#', // 8 spawn / cross
  '#..................#', // 9
  '#........####......#', // 10 mid divider with gaps
  '#........#..#......#', // 11 passage cols 9–10
  '#..................#', // 12 exit lane
  '##~~~~~~~~~~~~~~####', // 13 north void strip style — east courtyard cut
  '####################', // 14
] as const

export const SECURITY_CLEARANCE_HALL_FOOTPRINT: RoomFootprint = {
  id: 'security_clearance_hall',
  label: 'Clearance Hall',
  cols: 20,
  rows: 15,
  cells: parseFootprintMap(MAP, 20, 'Clearance Hall'),
  spawn: { col: 2, row: 8 },
  exit: { col: 17, row: 12 },
  mandatorySlots: [
    { col: 5, row: 4 },
    { col: 10, row: 4 },
    { col: 15, row: 4 },
    { col: 5, row: 9 },
    { col: 15, row: 9 },
  ],
  practiceSlots: [
    { col: 2, row: 2 },
    { col: 14, row: 7 },
  ],
  entranceCells: [
    { col: 1, row: 7 },
    { col: 2, row: 7 },
    { col: 3, row: 7 },
    { col: 1, row: 8 },
    { col: 2, row: 8 },
    { col: 3, row: 8 },
    { col: 1, row: 9 },
    { col: 2, row: 9 },
    { col: 3, row: 9 },
  ],
  checkpointCells: [
    { col: 15, row: 11 },
    { col: 16, row: 11 },
    { col: 17, row: 11 },
    { col: 15, row: 12 },
    { col: 16, row: 12 },
    { col: 17, row: 12 },
  ],
  propZones: [
    propZone('alcove-a', 'records', [{ col: 2, row: 1 }], ['filing_cabinet'], 0, 1),
    propZone('alcove-b', 'workstation', [{ col: 7, row: 1 }], ['desk', 'computer_console'], 0, 1),
    propZone('alcove-c', 'surveillance', [{ col: 12, row: 1 }], ['security_camera', 'monitor_bank'], 0, 1),
    propZone('south-supply', 'supply', [{ col: 7, row: 7 }, { col: 12, row: 7 }], ['crate_metal', 'storage_box'], 1, 2),
    propZone('divider', 'decor', [{ col: 11, row: 10 }], ['security_light'], 0, 1),
  ],
}

assertFootprintDims(SECURITY_CLEARANCE_HALL_FOOTPRINT)
