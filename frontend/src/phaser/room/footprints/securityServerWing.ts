import type { RoomFootprint } from '../types'
import { assertFootprintDims, parseFootprintMap, propZone } from './mapParse'

/**
 * Intelligence HQ — Server Wing (Level 3)
 * Vertical spine corridor (cols 8–9) with staggered side bays, open NE courtyard,
 * south-west spawn, and north-east exit — distinct from L1/L2.
 *
 * Legend: # wall · . floor · ~ void
 */
const MAP = [
  //                 01234567890123456789
  '####################', // 0
  '#...#..........##~~~', // 1 open NE courtyard + north exit bay
  '#...#..........#~~~~', // 2
  '#...####..######~~~~', // 3 spine choke keeps cols 8–9 open
  '#......#..#........#', // 4
  '#......#..#........#', // 5
  '####...#..#...######', // 6 west / east bays
  '#......#..#........#', // 7
  '#......#..#........#', // 8
  '####...#..#...######', // 9
  '#......#..#........#', // 10
  '#......#..#........#', // 11 spine remains open into south hall
  '#..................#', // 12 south hall / spawn
  '#...............##~~', // 13 compact SE courtyard
  '################~~~~', // 14
] as const

export const SECURITY_SERVER_WING_FOOTPRINT: RoomFootprint = {
  id: 'security_server_wing',
  label: 'Server Wing',
  cols: 20,
  rows: 15,
  cells: parseFootprintMap(MAP, 20, 'Server Wing'),
  spawn: { col: 2, row: 12 },
  exit: { col: 14, row: 1 },
  // Keep at least one spine tile free on every row so the vertical corridor never seals.
  mandatorySlots: [
    { col: 5, row: 5 },
    { col: 12, row: 5 },
    { col: 5, row: 8 },
    { col: 12, row: 8 },
    { col: 11, row: 10 },
  ],
  practiceSlots: [
    { col: 2, row: 4 },
    { col: 15, row: 5 },
  ],
  entranceCells: [
    { col: 1, row: 11 },
    { col: 2, row: 11 },
    { col: 3, row: 11 },
    { col: 1, row: 12 },
    { col: 2, row: 12 },
    { col: 3, row: 12 },
  ],
  checkpointCells: [
    { col: 12, row: 1 },
    { col: 13, row: 1 },
    { col: 14, row: 1 },
    { col: 12, row: 2 },
    { col: 13, row: 2 },
    { col: 14, row: 2 },
  ],
  propZones: [
    propZone(
      'west-bay-upper',
      'records',
      [
        { col: 1, row: 5 },
        { col: 2, row: 5 },
      ],
      ['filing_cabinet', 'storage_box'],
      1,
      2,
    ),
    propZone(
      'east-bay-upper',
      'surveillance',
      [
        { col: 15, row: 4 },
        { col: 16, row: 4 },
      ],
      ['monitor_bank', 'security_camera'],
      1,
      2,
    ),
    propZone(
      'west-bay-mid',
      'supply',
      [
        { col: 1, row: 8 },
        { col: 2, row: 8 },
      ],
      ['crate_wood', 'storage_box'],
      1,
      2,
    ),
    propZone(
      'east-bay-mid',
      'workstation',
      [
        { col: 15, row: 7 },
        { col: 16, row: 8 },
      ],
      ['computer_console', 'desk'],
      1,
      2,
    ),
    propZone(
      'south-decor',
      'decor',
      [
        { col: 16, row: 12 },
        { col: 5, row: 12 },
      ],
      ['security_light', 'security_camera'],
      1,
      2,
    ),
  ],
}

assertFootprintDims(SECURITY_SERVER_WING_FOOTPRINT)
