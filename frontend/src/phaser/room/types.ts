/** Theme hooks — only Security Room is implemented this milestone. */
export type RoomThemeId = 'INTELLIGENCE_HQ_SECURITY'

export type FootprintId =
  | 'security_office'
  | 'security_annex'
  | 'security_server_wing'
  | 'security_checkpoint'
  | 'security_clearance_hall'
  | 'security_purge_bay'
  | 'security_command_nexus'

export type FootprintCell = 'wall' | 'floor' | 'void'

export type PropKind =
  | 'crate_wood'
  | 'crate_metal'
  | 'storage_box'
  | 'filing_cabinet'
  | 'computer_console'
  | 'supply_chest'
  | 'security_camera'
  | 'monitor_bank'
  | 'security_light'
  | 'desk'
  | 'server_rack'

export type FloorVariant = 'base' | 'alt' | 'worn' | 'mark' | 'entrance' | 'checkpoint'

export type InteractiveKind = 'mandatory_terminal' | 'practice_terminal' | 'exit'

export interface GridPos {
  col: number
  row: number
}

export interface WorldPos {
  x: number
  y: number
}

export interface PropZone {
  id: string
  /** Functional-area storytelling tag. */
  area:
    | 'workstation'
    | 'records'
    | 'supply'
    | 'surveillance'
    | 'checkpoint'
    | 'decor'
  cells: GridPos[]
  preferredKinds: PropKind[]
  min: number
  max: number
}

export interface RoomFootprint {
  id: FootprintId
  label: string
  cols: number
  rows: number
  /** [row][col] authored geometry — walls are never randomly generated. */
  cells: FootprintCell[][]
  spawn: GridPos
  exit: GridPos
  /** Prefixed slots supporting 1→3→5 mandatory devices. */
  mandatorySlots: GridPos[]
  practiceSlots: GridPos[]
  propZones: PropZone[]
  entranceCells: GridPos[]
  checkpointCells: GridPos[]
}

export interface PlacedProp {
  kind: PropKind
  col: number
  row: number
  x: number
  y: number
  solid: boolean
  footprintCols: number
  footprintRows: number
  /** Y-sort depth bias for walk-behind furniture. */
  depthBias: number
}

export interface PlacedInteractive {
  kind: InteractiveKind
  index: number
  col: number
  row: number
  x: number
  y: number
}

export interface RoomLayoutPlan {
  themeId: RoomThemeId
  footprintId: FootprintId
  seed: number
  seedKey: string
  missionId: string
  rank: string
  roomInstance: number
  mandatoryTerminalCount: number
  cols: number
  rows: number
  tileSize: number
  /** Collision + LoS: wall, void, and solid props. */
  blocked: boolean[][]
  /** Authored cell kinds for rendering / minimap. */
  cellKinds: FootprintCell[][]
  floorVariants: FloorVariant[][]
  playerStart: WorldPos
  exit: WorldPos
  mandatoryTerminals: WorldPos[]
  practiceSlots: WorldPos[]
  props: PlacedProp[]
  interactives: PlacedInteractive[]
  usedFallback: boolean
}

export interface RoomGenerateInput {
  missionId: string
  rank: string
  roomInstance: number
  mandatoryTerminalCount: number
  themeId?: RoomThemeId
  footprintId?: FootprintId
}
