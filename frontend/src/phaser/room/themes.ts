import type { RoomThemeId } from './types'

export interface RoomThemeConfig {
  id: RoomThemeId
  label: string
  /** Texture key prefixes registered by pixelArt.ensureRoomTextures */
  floorKey: string
  wallKey: string
  wallCornerKey: string
  trimKey: string
  shadowKey: string
}

/**
 * Configuration hooks for future themes. Only Security Room is fully arted.
 */
export const ROOM_THEME_REGISTRY: Record<RoomThemeId, RoomThemeConfig> = {
  INTELLIGENCE_HQ_SECURITY: {
    id: 'INTELLIGENCE_HQ_SECURITY',
    label: 'Intelligence Headquarters — Security Room',
    floorKey: 'spy-floor',
    wallKey: 'spy-wall',
    wallCornerKey: 'spy-wall-corner',
    trimKey: 'spy-trim',
    shadowKey: 'spy-shadow',
  },
}

export const DEFAULT_ROOM_THEME: RoomThemeId = 'INTELLIGENCE_HQ_SECURITY'

/** Reserved for later milestones — not implemented. */
export const FUTURE_THEME_HOOKS = [
  'FIELD_SAFEHOUSE',
  'DATA_ARCHIVE',
  'CYBER_OPS_DECK',
] as const
