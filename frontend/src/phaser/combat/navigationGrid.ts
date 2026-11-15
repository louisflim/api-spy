import { ROOM_TILE_SIZE } from '../room/generateRoomLayout'
import type { RoomLayoutPlan } from '../room/types'
import type { GridCell } from './types'

/**
 * Walkable navigation grid derived from the same {@link RoomLayoutPlan.blocked}
 * mask used by Phaser solids placement and the minimap — not a separate map.
 *
 * Cells marked blocked (walls, voids, solid props, partitions, occupied
 * interactives) are non-walkable. Guard bodies (~14×10) fit inside a 40px tile,
 * so per-cell walkability without inflation is sufficient; 4-neighbor paths
 * avoid cutting diagonally through wall corners.
 */
export class NavigationGrid {
  readonly cols: number
  readonly rows: number
  readonly tileSize: number
  /** walkable[row][col] === true means a guard may stand / path through. */
  readonly walkable: boolean[][]

  constructor(blocked: boolean[][], tileSize: number = ROOM_TILE_SIZE) {
    this.rows = blocked.length
    this.cols = blocked[0]?.length ?? 0
    this.tileSize = tileSize
    this.walkable = blocked.map((row) => row.map((isBlocked) => !isBlocked))
  }

  static fromLayout(layout: RoomLayoutPlan): NavigationGrid {
    return new NavigationGrid(layout.blocked, layout.tileSize ?? ROOM_TILE_SIZE)
  }

  inBounds(col: number, row: number): boolean {
    return col >= 0 && row >= 0 && col < this.cols && row < this.rows
  }

  isWalkable(col: number, row: number): boolean {
    return this.inBounds(col, row) && Boolean(this.walkable[row]![col])
  }

  worldToCell(x: number, y: number): GridCell {
    return {
      col: Math.floor(x / this.tileSize),
      row: Math.floor(y / this.tileSize),
    }
  }

  cellToWorld(cell: GridCell): { x: number; y: number } {
    return {
      x: cell.col * this.tileSize + this.tileSize / 2,
      y: cell.row * this.tileSize + this.tileSize / 2,
    }
  }

  /**
   * If the exact cell is blocked, search an expanding ring for the nearest
   * walkable cell (used when the player stands on a solid interactive tile).
   */
  nearestWalkable(preferred: GridCell, maxRadius = 4): GridCell | null {
    if (this.isWalkable(preferred.col, preferred.row)) return preferred
    for (let radius = 1; radius <= maxRadius; radius += 1) {
      for (let dr = -radius; dr <= radius; dr += 1) {
        for (let dc = -radius; dc <= radius; dc += 1) {
          if (Math.abs(dr) !== radius && Math.abs(dc) !== radius) continue
          const col = preferred.col + dc
          const row = preferred.row + dr
          if (this.isWalkable(col, row)) return { col, row }
        }
      }
    }
    return null
  }

  sameCell(a: GridCell, b: GridCell): boolean {
    return a.col === b.col && a.row === b.row
  }
}
