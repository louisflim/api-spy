import Phaser from 'phaser'

/**
 * Body-sized hit area for damage, separate from the small feet-level physics body
 * that enemies and the agent use for walking (which must stay small to path between
 * props). Insets trim transparent sprite padding so hits match the visible character.
 */
export interface HurtboxInsets {
  /** Fraction of the displayed width trimmed from each side. */
  side: number
  /** Fraction of the displayed height trimmed from the top (hat / empty space). */
  top: number
  /** Fraction of the displayed height trimmed from the bottom (shadow under feet). */
  bottom: number
}

export function spriteHurtbox(
  sprite: Phaser.GameObjects.Sprite,
  insets: HurtboxInsets,
  out: Phaser.Geom.Rectangle = new Phaser.Geom.Rectangle(),
): Phaser.Geom.Rectangle {
  const w = sprite.displayWidth
  const h = sprite.displayHeight
  const left = sprite.x - w * sprite.originX
  const top = sprite.y - h * sprite.originY
  return out.setTo(
    left + w * insets.side,
    top + h * insets.top,
    w * (1 - 2 * insets.side),
    h * (1 - insets.top - insets.bottom),
  )
}

/** Patrol guard / boss art (32×40 frames): torso fills ~60% of the frame width. */
export const GUARD_HURTBOX: HurtboxInsets = { side: 0.2, top: 0.08, bottom: 0.04 }

/** Rookie Detective (272×272 frames): figure is narrow inside a square frame. */
export const AGENT_HURTBOX: HurtboxInsets = { side: 0.36, top: 0.18, bottom: 0.03 }
