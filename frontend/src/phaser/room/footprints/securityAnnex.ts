import type { RoomFootprint } from '../types'
import { assertFootprintDims, parseFootprintMap, propZone } from './mapParse'

/**
 * Intelligence HQ — Security Annex (Level 2)
 * Twin side chambers with C-openings facing the central double-door passage
 * (cols 8–9), west spawn, southern exit — visibly different from L1.
 *
 * Legend: # wall · . floor · ~ void
 */
const MAP = [
  //                 01234567890123456789
  '####################', // 0
  '#..................#', // 1 north gallery
  '#....###..###......#', // 2 chamber roofs (passages cols 8–9)
  '#....#......#......#', // 3 NW open east / NE open west
  '#....#......#......#', // 4
  '#....###..###......#', // 5
  '#..................#', // 6
  '#..................#', // 7 main cross-hall / spawn row
  '#....###..###......#', // 8
  '#....#......#......#', // 9 SW / SE chambers
  '#....#......#......#', // 10
  '#....###..###......#', // 11
  '#..................#', // 12 south exit approach
  '#...............##~~', // 13 compact SE courtyard
  '################~~~~', // 14
] as const

export const SECURITY_ANNEX_FOOTPRINT: RoomFootprint = {
  id: 'security_annex',
  label: 'Security Annex',
  cols: 20,
  rows: 15,
  cells: parseFootprintMap(MAP, 20, 'Security Annex'),
  spawn: { col: 2, row: 7 },
  exit: { col: 9, row: 12 },
  mandatorySlots: [
    { col: 5, row: 6 },
    { col: 10, row: 6 },
    { col: 14, row: 6 },
    { col: 5, row: 7 },
    { col: 14, row: 7 },
  ],
  practiceSlots: [
    { col: 6, row: 3 },
    { col: 11, row: 9 },
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
    { col: 8, row: 11 },
    { col: 9, row: 11 },
    { col: 10, row: 11 },
    { col: 8, row: 12 },
    { col: 9, row: 12 },
    { col: 10, row: 12 },
  ],
  propZones: [
    propZone(
      'nw-chamber',
      'records',
      [{ col: 6, row: 4 }],
      ['filing_cabinet', 'storage_box'],
      0,
      1,
    ),
    propZone(
      'ne-gallery',
      'workstation',
      [
        { col: 14, row: 3 },
        { col: 15, row: 4 },
      ],
      ['computer_console', 'desk'],
      1,
      2,
    ),
    propZone(
      'sw-chamber',
      'supply',
      [{ col: 6, row: 10 }],
      ['crate_wood', 'storage_box'],
      0,
      1,
    ),
    propZone(
      'se-gallery',
      'surveillance',
      [
        { col: 14, row: 10 },
        { col: 15, row: 10 },
      ],
      ['monitor_bank', 'security_camera'],
      1,
      2,
    ),
    propZone(
      'cross-hall-flank',
      'decor',
      [
        { col: 4, row: 1 },
        { col: 16, row: 1 },
      ],
      ['security_light', 'security_camera'],
      1,
      2,
    ),
  ],
}

assertFootprintDims(SECURITY_ANNEX_FOOTPRINT)
