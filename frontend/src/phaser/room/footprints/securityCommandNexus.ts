import type { RoomFootprint } from '../types'
import { assertFootprintDims, parseFootprintMap, propZone } from './mapParse'

/**
 * Intelligence HQ — Command Nexus (Module 1 Recruit L7 boss)
 * Central void ring with four approach corridors: larger unique finale layout.
 *
 * Legend: # wall · . floor · ~ void
 */
const MAP = [
  //                 01234567890123456789
  '####################', // 0
  '#..................#', // 1
  '#..................#', // 2
  '#....##########....#', // 3
  '#....#~~~~~~~~#....#', // 4
  '#....#~~....~~#....#', // 5
  '#........##........#', // 6
  '#........##........#', // 7
  '#....#~~....~~#....#', // 8
  '#....#~~~~~~~~#....#', // 9
  '#....##########....#', // 10
  '#..................#', // 11
  '#..................#', // 12
  '##..............##~~', // 13
  '################~~~~', // 14
] as const

export const SECURITY_COMMAND_NEXUS_FOOTPRINT: RoomFootprint = {
  id: 'security_command_nexus',
  label: 'Command Nexus',
  cols: 20,
  rows: 15,
  cells: parseFootprintMap(MAP, 20, 'Command Nexus'),
  spawn: { col: 2, row: 2 },
  exit: { col: 17, row: 12 },
  mandatorySlots: [
    { col: 2, row: 6 },
    { col: 9, row: 2 },
    { col: 17, row: 6 },
    { col: 9, row: 11 },
    { col: 6, row: 7 },
  ],
  practiceSlots: [
    { col: 3, row: 11 },
    { col: 16, row: 2 },
  ],
  entranceCells: [
    { col: 1, row: 1 },
    { col: 2, row: 1 },
    { col: 3, row: 1 },
    { col: 1, row: 2 },
    { col: 2, row: 2 },
    { col: 3, row: 2 },
    { col: 1, row: 3 },
    { col: 2, row: 3 },
    { col: 3, row: 3 },
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
    propZone('west-approach', 'workstation', [{ col: 2, row: 5 }, { col: 3, row: 8 }], ['computer_console', 'desk'], 1, 2),
    propZone('east-approach', 'surveillance', [{ col: 16, row: 5 }, { col: 15, row: 8 }], ['security_camera', 'monitor_bank'], 1, 2),
    propZone('north-ring', 'records', [{ col: 7, row: 1 }, { col: 11, row: 1 }], ['filing_cabinet', 'server_rack'], 1, 2),
    propZone('south-ring', 'supply', [{ col: 7, row: 12 }, { col: 12, row: 12 }], ['crate_metal', 'storage_box'], 1, 2),
  ],
}

assertFootprintDims(SECURITY_COMMAND_NEXUS_FOOTPRINT)
