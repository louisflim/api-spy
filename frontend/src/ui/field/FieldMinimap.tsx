import { ROOM_LAYOUT } from '../../phaser/roomLayout'
import type { PlacedProp, RoomLayoutPlan } from '../../phaser/room/types'
import type { PlayerPosition } from '../GameCanvas'

interface FieldMinimapProps {
  player: PlayerPosition
  exitReady: boolean
  devicesComplete: boolean
  layout?: RoomLayoutPlan | null
}

type CellKind =
  | 'floor'
  | 'wall'
  | 'void'
  | 'obstacle'
  | 'chest'
  | 'terminal'
  | 'exit'
  | 'exit-open'

function propCellKind(prop: PlacedProp): CellKind {
  if (prop.kind === 'supply_chest') return 'chest'
  if (!prop.solid) return 'floor'
  return 'obstacle'
}

/**
 * Tactical map projected from RoomLayoutPlan cellKinds + props (single source of truth).
 */
export function FieldMinimap({
  player,
  exitReady,
  devicesComplete,
  layout = null,
}: FieldMinimapProps) {
  const cols = layout?.cols ?? 20
  const rows = layout?.rows ?? 15
  const tileSize = layout?.tileSize ?? 40
  const worldW = cols * tileSize
  const worldH = rows * tileSize

  const cells: CellKind[][] = Array.from({ length: rows }, (_, row) =>
    Array.from({ length: cols }, (_, col) => {
      if (!layout) {
        const isBorder = row === 0 || col === 0 || row === rows - 1 || col === cols - 1
        return isBorder ? 'wall' : 'floor'
      }
      const kind = layout.cellKinds[row]?.[col] ?? 'void'
      if (kind === 'wall') return 'wall'
      if (kind === 'void') return 'void'
      return 'floor'
    }),
  )

  if (layout) {
    for (const prop of layout.props) {
      if (prop.row < 0 || prop.col < 0 || prop.row >= rows || prop.col >= cols) continue
      if (cells[prop.row]![prop.col] === 'wall' || cells[prop.row]![prop.col] === 'void') continue
      const kind = propCellKind(prop)
      if (kind === 'floor') continue
      cells[prop.row]![prop.col] = kind
    }

    for (const term of layout.interactives.filter((i) => i.kind === 'mandatory_terminal')) {
      cells[term.row]![term.col] = 'terminal'
    }

    const exit = layout.interactives.find((item) => item.kind === 'exit')
    if (exit) cells[exit.row]![exit.col] = exitReady ? 'exit-open' : 'exit'
  } else {
    const termCol = Math.floor(ROOM_LAYOUT.terminal.x / tileSize)
    const termRow = Math.floor(ROOM_LAYOUT.terminal.y / tileSize)
    const exitCol = Math.floor(ROOM_LAYOUT.exit.x / tileSize)
    const exitRow = Math.floor(ROOM_LAYOUT.exit.y / tileSize)
    if (cells[termRow]) cells[termRow]![termCol] = 'terminal'
    if (cells[exitRow]) cells[exitRow]![exitCol] = exitReady ? 'exit-open' : 'exit'
  }

  const playerCol = Math.min(cols - 1, Math.max(0, Math.floor(player.x / tileSize)))
  const playerRow = Math.min(rows - 1, Math.max(0, Math.floor(player.y / tileSize)))
  const facing = player.facing ?? 'down'

  const hasChest = Boolean(layout?.props.some((p) => p.kind === 'supply_chest'))
  const hasObstacle = Boolean(layout?.props.some((p) => p.solid && p.kind !== 'supply_chest'))

  return (
    <section className="field-panel field-minimap" aria-labelledby="field-minimap-title">
      <h2 id="field-minimap-title" className="field-panel__title">
        Tactical map
      </h2>
      <div
        className="field-minimap__stage"
        role="img"
        aria-label="Live tactical map of the field room"
        style={{
          aspectRatio: `${worldW} / ${worldH}`,
          gridTemplateColumns: `repeat(${cols}, 1fr)`,
          gridTemplateRows: `repeat(${rows}, 1fr)`,
        }}
      >
        {cells.flatMap((row, r) =>
          row.map((kind, c) => (
            <span
              key={`${c}-${r}`}
              className={`field-minimap__cell field-minimap__cell--${kind}${
                kind === 'terminal' && devicesComplete ? ' is-done' : ''
              }`}
              data-col={c}
              data-row={r}
            />
          )),
        )}
        <span
          className={`field-minimap__actor field-minimap__actor--player is-facing-${facing}`}
          style={{
            left: `${((playerCol + 0.5) / cols) * 100}%`,
            top: `${((playerRow + 0.5) / rows) * 100}%`,
          }}
          title="Rookie Detective"
        />
      </div>
      <ul className="field-minimap__legend">
        <li>
          <i className="field-minimap__swatch field-minimap__swatch--player" /> Agent
        </li>
        <li>
          <i className="field-minimap__swatch field-minimap__swatch--wall" /> Wall
        </li>
        <li>
          <i className="field-minimap__swatch field-minimap__swatch--terminal" /> Terminal
        </li>
        <li>
          <i
            className={`field-minimap__swatch field-minimap__swatch--exit${
              exitReady ? ' is-open' : ''
            }`}
          />{' '}
          Exit
        </li>
        {hasObstacle ? (
          <li>
            <i className="field-minimap__swatch field-minimap__swatch--obstacle" /> Obstacle
          </li>
        ) : null}
        {hasChest ? (
          <li>
            <i className="field-minimap__swatch field-minimap__swatch--chest" /> Chest
          </li>
        ) : null}
      </ul>
    </section>
  )
}
