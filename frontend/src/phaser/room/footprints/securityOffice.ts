import type { FootprintCell, GridPos, PropKind, RoomFootprint } from '../types'

/**
 * Intelligence HQ — Security Office
 * Authored irregular footprint: interior records office, surveillance bay,
 * entrance vestibule, exit checkpoint, and a compact SE exterior courtyard.
 *
 * Legend: # wall · . floor · ~ void/outside
 * Each row must be exactly 20 characters (indices 0..19).
 */
const MAP = [
  //                 01234567890123456789
  '####################', // 0
  '#..................#', // 1
  '#..................#', // 2
  '#..........#####...#', // 3 records office shell
  '#..........#...#...#', // 4
  '#..........#...##..#', // 5 doorway into office
  '#..........#.......#', // 6
  '#..................#', // 7 main hall / spawn → east exit
  '#..................#', // 8
  '#..........###.....#', // 9 surveillance partition
  '#..........#..#....#', // 10 two-tile passage (cols 12–13)
  '#..........#.......#', // 11
  '#..................#', // 12 south floor
  // Compact SE courtyard (no giant black cutout).
  '#...............##~~', // 13
  '################~~~~', // 14
] as const

function parseMap(lines: readonly string[]): FootprintCell[][] {
  return lines.map((line, row) => {
    if (line.length !== 20) {
      throw new Error(`Security Office map row ${row} length ${line.length} !== 20`)
    }
    return [...line].map((ch) => {
      if (ch === '#') return 'wall'
      if (ch === '~') return 'void'
      return 'floor'
    })
  })
}

function zone(
  id: string,
  area: RoomFootprint['propZones'][0]['area'],
  cells: GridPos[],
  preferredKinds: PropKind[],
  min: number,
  max: number,
) {
  return { id, area, cells, preferredKinds, min, max }
}

export const SECURITY_OFFICE_FOOTPRINT: RoomFootprint = {
  id: 'security_office',
  label: 'Security Office',
  cols: 20,
  rows: 15,
  cells: parseMap(MAP),
  spawn: { col: 2, row: 7 },
  /** Easternmost floor cell against the outer wall — not mid-room. */
  exit: { col: 18, row: 7 },
  mandatorySlots: [
    { col: 8, row: 6 },
    { col: 9, row: 6 },
    { col: 10, row: 6 },
    { col: 8, row: 8 },
    { col: 10, row: 8 },
  ],
  practiceSlots: [
    { col: 13, row: 4 },
    { col: 13, row: 11 },
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
    { col: 16, row: 6 },
    { col: 17, row: 6 },
    { col: 16, row: 7 },
    { col: 17, row: 7 },
    { col: 16, row: 8 },
    { col: 17, row: 8 },
  ],
  propZones: [
    zone(
      'records-north',
      'records',
      [
        { col: 4, row: 2 },
        { col: 5, row: 2 },
        { col: 6, row: 2 },
        { col: 7, row: 2 },
        { col: 4, row: 3 },
        { col: 5, row: 3 },
      ],
      ['filing_cabinet', 'storage_box', 'monitor_bank'],
      3,
      5,
    ),
    zone(
      'workstation-side',
      'workstation',
      [
        { col: 7, row: 5 },
        { col: 11, row: 5 },
        { col: 7, row: 9 },
        { col: 11, row: 9 },
        { col: 9, row: 5 },
        { col: 9, row: 9 },
      ],
      ['computer_console', 'desk', 'monitor_bank'],
      2,
      4,
    ),
    zone(
      'supply-south',
      'supply',
      [
        { col: 3, row: 11 },
        { col: 4, row: 11 },
        { col: 5, row: 11 },
        { col: 6, row: 11 },
        { col: 4, row: 12 },
        { col: 5, row: 12 },
        { col: 7, row: 11 },
      ],
      ['crate_wood', 'crate_metal', 'storage_box', 'supply_chest'],
      3,
      5,
    ),
    zone(
      'surveillance-bay',
      'surveillance',
      [
        { col: 15, row: 10 },
        { col: 16, row: 10 },
        { col: 15, row: 11 },
        { col: 14, row: 9 },
        { col: 16, row: 9 },
      ],
      ['monitor_bank', 'computer_console', 'server_rack', 'security_camera'],
      2,
      4,
    ),
    zone(
      'office-alcove',
      'records',
      [
        { col: 12, row: 3 },
        { col: 13, row: 3 },
        { col: 14, row: 3 },
        { col: 15, row: 4 },
      ],
      ['filing_cabinet', 'desk', 'computer_console'],
      1,
      3,
    ),
    zone(
      'checkpoint-flank',
      'checkpoint',
      [
        { col: 15, row: 5 },
        { col: 16, row: 5 },
        { col: 15, row: 9 },
        { col: 16, row: 9 },
      ],
      ['server_rack', 'security_light', 'computer_console'],
      1,
      2,
    ),
    zone(
      'decor-perimeter',
      'decor',
      [
        { col: 2, row: 2 },
        { col: 3, row: 2 },
        { col: 2, row: 11 },
        { col: 18, row: 2 },
        { col: 18, row: 4 },
        { col: 18, row: 10 },
        { col: 17, row: 2 },
      ],
      ['security_camera', 'security_light'],
      2,
      4,
    ),
  ],
}

if (
  SECURITY_OFFICE_FOOTPRINT.cells.length !== SECURITY_OFFICE_FOOTPRINT.rows ||
  SECURITY_OFFICE_FOOTPRINT.cells.some((row) => row.length !== SECURITY_OFFICE_FOOTPRINT.cols)
) {
  throw new Error('SECURITY_OFFICE_FOOTPRINT map dimensions mismatch')
}
