import Phaser from 'phaser'

type Facing = 'down' | 'left' | 'right' | 'up'

/**
 * Hand position relative to the agent's feet (sprite origin), in screen px, measured on the
 * agent sheets (272px frames at 0.24 scale). Facing up, the gun is raised at shoulder height.
 */
const HAND_OFFSET: Record<Facing, { x: number; y: number }> = {
  down: { x: 11, y: -20 },
  up: { x: -13, y: -24 },
  left: { x: -3, y: -16 },
  right: { x: 3, y: -16 },
}

const FACING_VECTOR: Record<Facing, { x: number; y: number }> = {
  down: { x: 0, y: 1 },
  up: { x: 0, y: -1 },
  left: { x: -1, y: 0 },
  right: { x: 1, y: 0 },
}

/** Gun art pixels are 4 texture px; this scale matches the agent's chunky pixels on screen. */
export const HELD_GUN_SCALE = 0.28

/**
 * The equipped gun drawn in the agent's hand. Points along the latest shot for a moment, then
 * along the facing direction; flips so it is never upside down. The agent turns to face the
 * aim while shooting, so body and gun always agree. Purely visual — shots come from the weapon
 * controller.
 */
export class HeldGun {
  private readonly image: Phaser.GameObjects.Image

  constructor(scene: Phaser.Scene, textureKey: string, gripX: number, gripY: number) {
    this.image = scene.add.image(0, 0, textureKey).setOrigin(gripX, gripY).setScale(HELD_GUN_SCALE)
    scene.textures.get(textureKey).setFilter(Phaser.Textures.FilterMode.NEAREST)
  }

  update(
    player: Phaser.GameObjects.Sprite,
    facing: Facing,
    aim: { x: number; y: number } | null,
    overheated = false,
  ): void {
    const dir = aim ?? FACING_VECTOR[facing]
    const hand = HAND_OFFSET[facing]
    // Facing up/down, hold the gun in the hand on the side it points to, so a diagonal shot
    // never crosses the body.
    const handX =
      (facing === 'up' || facing === 'down') && Math.abs(dir.x) > 0.05
        ? Math.sign(dir.x) * Math.abs(hand.x)
        : hand.x
    this.image.setPosition(Math.round(player.x + handX), Math.round(player.y + hand.y))
    this.image.setRotation(Math.atan2(dir.y, dir.x))
    // Keep the gun upright when it points left.
    this.image.setFlipY(dir.x < -0.001)
    this.image.setDepth(player.depth + 0.5)
    this.image.setAlpha(player.alpha)
    // Overheated automatic gun glows red until it can fire again.
    if (overheated) this.image.setTint(0xff6a4d)
    else this.image.clearTint()
  }

  setVisible(visible: boolean): void {
    this.image.setVisible(visible)
  }

  destroy(): void {
    this.image.destroy()
  }
}
