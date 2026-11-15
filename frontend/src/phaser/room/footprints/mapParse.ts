import type { FootprintCell, GridPos, PropKind, RoomFootprint } from '../types'

/** Legend: # wall · . floor · ~ void/outside */
export function parseFootprintMap(
  lines: readonly string[],
  cols: number,
  label: string,
): FootprintCell[][] {
  return lines.map((line, row) => {
    if (line.length !== cols) {
      throw new Error(`${label} map row ${row} length ${line.length} !== ${cols}`)
    }
    return [...line].map((ch) => {
      if (ch === '#') return 'wall'
      if (ch === '~') return 'void'
      return 'floor'
    })
  })
}

export function assertFootprintDims(fp: RoomFootprint): void {
  if (
    fp.cells.length !== fp.rows ||
    fp.cells.some((row) => row.length !== fp.cols)
  ) {
    throw new Error(`${fp.id} footprint map dimensions mismatch`)
  }
}

export function propZone(
  id: string,
  area: RoomFootprint['propZones'][0]['area'],
  cells: GridPos[],
  preferredKinds: PropKind[],
  min: number,
  max: number,
) {
  return { id, area, cells, preferredKinds, min, max }
}
