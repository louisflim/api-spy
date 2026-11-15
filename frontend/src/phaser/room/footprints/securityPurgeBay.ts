import type { RoomFootprint } from '../types'
import { assertFootprintDims, parseFootprintMap, propZone } from './mapParse'

/**
 * Intelligence HQ — Purge Bay (Module 1 Recruit L6)
 * U-shaped decommission bay: west spawn arm, south cross-bar, east exit arm.
 *
 * Legend: # wall · . floor · ~ void
 */
const MAP = [
  //                 01234567890123456789
  '####################', // 0
  '#......######......#', // 1 U arms
  '#......#~~~~#......#', // 2
  '#......#~~~~#......#', // 3
  '#......#~~~~#......#', // 4
  '#......#~~~~#......#', // 5
  '#......######......#', // 6
  '#..................#', // 7 south cross-bar / spawn eastward
  '#..................#', // 8
  '#......######......#', // 9 lower U notch
  '#......#~~~~#......#', // 10
  '#......#~~~~#......#', // 11
  '#..................#', // 12 exit row
  '#...............##~~', // 13
  '################~~~~', // 14
] as const

export const SECURITY_PURGE_BAY_FOOTPRINT: RoomFootprint = {
  id: 'security_purge_bay',
  label: 'Purge Bay',
  cols: 20,
  rows: 15,
  cells: parseFootprintMap(MAP, 20, 'Purge Bay'),
  spawn: { col: 2, row: 3 },
  exit: { col: 17, row: 12 },
  mandatorySlots: [
    { col: 3, row: 7 },
    { col: 9, row: 7 },
    { col: 15, row: 7 },
    { col: 3, row: 12 },
    { col: 15, row: 3 },
  ],
  practiceSlots: [
    { col: 2, row: 5 },
    { col: 16, row: 5 },
  ],
  entranceCells: [
    { col: 1, row: 2 },
    { col: 2, row: 2 },
    { col: 3, row: 2 },
    { col: 1, row: 3 },
    { col: 2, row: 3 },
    { col: 3, row: 3 },
    { col: 1, row: 4 },
    { col: 2, row: 4 },
    { col: 3, row: 4 },
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
    propZone('west-arm', 'supply', [{ col: 2, row: 1 }, { col: 3, row: 5 }], ['crate_metal', 'storage_box'], 1, 2),
    propZone('east-arm', 'surveillance', [{ col: 16, row: 1 }, { col: 15, row: 5 }], ['security_camera', 'monitor_bank'], 1, 2),
    propZone('cross-bar', 'workstation', [{ col: 6, row: 8 }, { col: 12, row: 8 }], ['desk', 'computer_console'], 1, 2),
    propZone('lower-notch', 'records', [{ col: 3, row: 10 }], ['filing_cabinet'], 0, 1),
  ],
}

assertFootprintDims(SECURITY_PURGE_BAY_FOOTPRINT)
