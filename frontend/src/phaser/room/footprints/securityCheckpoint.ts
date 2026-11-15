import type { RoomFootprint } from '../types'
import { assertFootprintDims, parseFootprintMap, propZone } from './mapParse'

/**
 * Intelligence HQ — Security Checkpoint (Module 1 Recruit L4)
 * Central registration island with open approach lanes and a north desk bay.
 * Visibly different from office / annex / server wing.
 *
 * Legend: # wall · . floor · ~ void
 */
const MAP = [
  //                 01234567890123456789
  '####################', // 0
  '#..................#', // 1 north desk bay
  '#....###########...#', // 2 registration island roof
  '#....#.........#...#', // 3
  '#....#.........#...#', // 4
  '#....#####.#####...#', // 5 south opening cols 9–10
  '#..................#', // 6 approach
  '#..................#', // 7 spawn row
  '#..................#', // 8
  '#....###....###....#', // 9 side barriers
  '#....#........#....#', // 10
  '#....#........#....#', // 11
  '#..................#', // 12 exit approach
  '#...............##~~', // 13
  '################~~~~', // 14
] as const

export const SECURITY_CHECKPOINT_FOOTPRINT: RoomFootprint = {
  id: 'security_checkpoint',
  label: 'Security Checkpoint',
  cols: 20,
  rows: 15,
  cells: parseFootprintMap(MAP, 20, 'Security Checkpoint'),
  spawn: { col: 2, row: 7 },
  exit: { col: 17, row: 12 },
  mandatorySlots: [
    { col: 9, row: 6 },
    { col: 11, row: 6 },
    { col: 7, row: 8 },
    { col: 13, row: 8 },
    { col: 10, row: 8 },
  ],
  practiceSlots: [
    { col: 6, row: 3 },
    { col: 12, row: 10 },
  ],
  entranceCells: [
    { col: 1, row: 6 },
    { col: 2, row: 6 },
    { col: 3, row: 6 },
    { col: 1, row: 7 },
    { col: 2, row: 7 },
    { col: 3, row: 7 },
    { col: 1, row: 8 },
    { col: 2, row: 8 },
    { col: 3, row: 8 },
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
    propZone('desk-bay', 'workstation', [{ col: 6, row: 1 }, { col: 14, row: 1 }], ['desk', 'computer_console'], 1, 2),
    propZone('island-inner', 'checkpoint', [{ col: 8, row: 3 }, { col: 11, row: 3 }], ['monitor_bank', 'security_camera'], 1, 2),
    propZone('west-barrier', 'supply', [{ col: 5, row: 10 }], ['crate_wood', 'storage_box'], 0, 1),
    propZone('east-barrier', 'records', [{ col: 14, row: 10 }], ['filing_cabinet'], 0, 1),
  ],
}

assertFootprintDims(SECURITY_CHECKPOINT_FOOTPRINT)
