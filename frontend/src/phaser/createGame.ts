import Phaser from 'phaser'
import { ROOM_HEIGHT, ROOM_WIDTH } from './roomLayout'
import { MissionScene } from './MissionScene'

export function createGame(
  parent: HTMLElement,
  options: { doorUnlocked: boolean },
): Phaser.Game {
  return new Phaser.Game({
    type: Phaser.AUTO,
    width: ROOM_WIDTH,
    height: ROOM_HEIGHT,
    parent,
    backgroundColor: '#101820',
    pixelArt: true,
    antialias: false,
    roundPixels: true,
    scale: {
      mode: Phaser.Scale.FIT,
      autoCenter: Phaser.Scale.CENTER_BOTH,
      width: ROOM_WIDTH,
      height: ROOM_HEIGHT,
    },
    physics: {
      default: 'arcade',
      arcade: {
        debug: false,
      },
    },
    scene: [],
    callbacks: {
      preBoot: (game) => {
        game.registry.set('doorUnlocked', options.doorUnlocked)
      },
    },
  })
}

// MissionScene is started explicitly from GameCanvas after listeners are attached.
export { MissionScene }
