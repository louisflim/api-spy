import Phaser from 'phaser'
import { CombatSystem } from './combat'
import { COMBAT_TEXTURE_KEYS, GUARD_SHEET_PATH } from './combat/combatArt'
import { PATROL_GUARD_CONFIG } from './combat/enemyTypes'
import { generateRoomLayout, ROOM_COLS, ROOM_ROWS, ROOM_TILE_SIZE } from './room/generateRoomLayout'
import { ensureRoomTextures, FLOOR_TEXTURE_KEYS, PROP_TEXTURE_KEYS } from './room/pixelArt'
import type { RoomLayoutPlan, WorldPos } from './room/types'
import { ROOM_HEIGHT, ROOM_WIDTH } from './roomLayout'

export const GAME_EVENTS = {
  OPEN_TERMINAL: 'open-terminal',
  CLOSE_TERMINAL: 'close-terminal',
  DOOR_UNLOCKED: 'door-unlocked',
  EXIT_READY: 'exit-ready',
  EXIT_REACHED: 'exit-reached',
  INPUT_LOCK: 'input-lock',
  PLAYER_MOVED: 'player-moved',
  ROOM_LAYOUT_READY: 'room-layout-ready',
  HEARTS_CHANGED: 'hearts-changed',
  COMBAT_DEATH: 'combat-death',
} as const

const ROOKIE_KEY = 'rookie-detective'
const FRAME_SIZE = 272
const PLAYER_SCALE = 0.24
const INTERACT_RANGE = 58

type Facing = 'down' | 'left' | 'right' | 'up'

export class MissionScene extends Phaser.Scene {
  private player!: Phaser.Physics.Arcade.Sprite
  private exitSprite!: Phaser.Physics.Arcade.Sprite
  private cursors!: Phaser.Types.Input.Keyboard.CursorKeys
  private wasd!: {
    W: Phaser.Input.Keyboard.Key
    A: Phaser.Input.Keyboard.Key
    S: Phaser.Input.Keyboard.Key
    D: Phaser.Input.Keyboard.Key
  }
  private interactKey!: Phaser.Input.Keyboard.Key
  private nearTerminalIndex = -1
  private nearExit = false
  private exitReady = false
  private inputLocked = false
  private missionId = 'm1-recruit-l1'
  private deviceId = 'door-alpha'
  private documentation = ''
  /** Per-terminal bindings aligned with mandatorySprites index order. */
  private terminalBindings: Array<{
    deviceId: string
    challengeId: string
    documentation: string
    finalLock: boolean
  }> = []
  private rank = 'RECRUIT'
  private roomInstance = 0
  private mandatoryTerminalCount = 1
  private mandatorySprites: Phaser.Physics.Arcade.Sprite[] = []
  private promptText!: Phaser.GameObjects.Text
  private facing: Facing = 'down'
  private readonly speed = 180
  private lastEmitX = -1
  private lastEmitY = -1
  private lastEmitFacing: Facing | null = null
  private layout!: RoomLayoutPlan
  private solids!: Phaser.Physics.Arcade.StaticGroup
  private wallBlocked: boolean[][] = []
  private combatEnabled = false
  private combat: CombatSystem | null = null

  constructor() {
    super('MissionScene')
  }

  init(data: {
    exitReady?: boolean
    missionId?: string
    deviceId?: string
    documentation?: string
    inputLocked?: boolean
    rank?: string
    roomInstance?: number
    mandatoryTerminalCount?: number
    /** One binding per physical mandatory terminal (includes final lock when required). */
    terminalBindings?: Array<{
      deviceId: string
      challengeId: string
      documentation: string
      finalLock: boolean
    }>
    /** Mission field only — Practice Mode and Module 0 stay enemy-free. */
    combatEnabled?: boolean
  }): void {
    this.exitReady = Boolean(data?.exitReady)
    this.missionId = data?.missionId ?? this.missionId
    this.deviceId = data?.deviceId ?? this.deviceId
    this.documentation = data?.documentation ?? this.documentation
    this.terminalBindings = Array.isArray(data?.terminalBindings) ? data.terminalBindings : []
    this.inputLocked = Boolean(data?.inputLocked)
    this.rank = data?.rank ?? this.rank
    this.roomInstance = data?.roomInstance ?? this.roomInstance
    this.mandatoryTerminalCount = Math.max(
      1,
      data?.mandatoryTerminalCount ?? this.terminalBindings.length ?? 1,
    )
    this.combatEnabled = Boolean(data?.combatEnabled)
  }

  preload(): void {
    if (!this.textures.exists(ROOKIE_KEY)) {
      this.load.spritesheet(ROOKIE_KEY, '/assets/characters/rookie-detective.png', {
        frameWidth: FRAME_SIZE,
        frameHeight: FRAME_SIZE,
      })
    }
    if (this.combatEnabled && !this.textures.exists(COMBAT_TEXTURE_KEYS.guardSheet)) {
      this.load.spritesheet(COMBAT_TEXTURE_KEYS.guardSheet, GUARD_SHEET_PATH, {
        frameWidth: PATROL_GUARD_CONFIG.frameWidth,
        frameHeight: PATROL_GUARD_CONFIG.frameHeight,
      })
    }
  }

  create(): void {
    this.textures.get(ROOKIE_KEY).setFilter(Phaser.Textures.FilterMode.NEAREST)
    ensureRoomTextures(this)

    this.layout = generateRoomLayout({
      missionId: this.missionId,
      rank: this.rank,
      roomInstance: this.roomInstance,
      mandatoryTerminalCount: this.mandatoryTerminalCount,
    })
    this.wallBlocked = this.layout.blocked.map((row) => [...row])

    this.physics.world.setBounds(0, 0, ROOM_WIDTH, ROOM_HEIGHT)
    this.solids = this.physics.add.staticGroup()

    this.paintEnvironment()
    this.spawnProps()
    this.spawnInteractives()

    this.createRookieAnimations()
    const start = this.layout.playerStart
    this.player = this.physics.add.sprite(start.x, start.y, ROOKIE_KEY, 0)
    this.player.setScale(PLAYER_SCALE)
    this.player.setOrigin(0.5, 1)
    this.player.setCollideWorldBounds(true)
    this.player.setDepth(20)
    this.player.anims.play('rookie-idle-down')

    const bodyWidth = 56
    const bodyHeight = 28
    const body = this.player.body as Phaser.Physics.Arcade.Body
    body.setSize(bodyWidth, bodyHeight)
    body.setOffset((FRAME_SIZE - bodyWidth) / 2, FRAME_SIZE - bodyHeight - 4)

    this.physics.add.collider(this.player, this.solids)

    if (this.combatEnabled) {
      this.combat = new CombatSystem({
        scene: this,
        layout: this.layout,
        player: this.player,
        solids: this.solids,
        rank: this.rank,
        getFacing: () => this.facing,
        onHeartsChanged: (hearts, maxHearts) => {
          this.game.events.emit(GAME_EVENTS.HEARTS_CHANGED, { hearts, maxHearts })
        },
        onCombatDeath: () => {
          this.game.events.emit(GAME_EVENTS.COMBAT_DEATH, { missionId: this.missionId })
        },
      })
      if (this.inputLocked) this.combat.setPaused(true)
    }

    this.promptText = this.add
      .text(ROOM_WIDTH / 2, ROOM_HEIGHT - 18, 'Move with WASD or arrows. Press E near a terminal.', {
        fontFamily: 'Share Tech Mono, Consolas, monospace',
        fontSize: '13px',
        color: '#9bb4cc',
        backgroundColor: '#050b16cc',
        padding: { x: 8, y: 4 },
      })
      .setOrigin(0.5)
      .setDepth(40)
      .setScrollFactor(0)

    this.cursors = this.input.keyboard!.createCursorKeys()
    this.wasd = this.input.keyboard!.addKeys('W,A,S,D') as typeof this.wasd
    this.interactKey = this.input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.E)
    {
      const keyboard = this.input.keyboard
      if (keyboard) {
        if (this.inputLocked) {
          keyboard.enabled = false
          keyboard.disableGlobalCapture()
          keyboard.resetKeys()
        } else {
          keyboard.enabled = true
          keyboard.enableGlobalCapture()
        }
      }
    }

    this.game.events.on(GAME_EVENTS.EXIT_READY, this.onExitReady, this)
    this.game.events.on(GAME_EVENTS.INPUT_LOCK, this.onInputLock, this)
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.game.events.off(GAME_EVENTS.EXIT_READY, this.onExitReady, this)
      this.game.events.off(GAME_EVENTS.INPUT_LOCK, this.onInputLock, this)
      this.combat?.destroy()
      this.combat = null
    })

    this.game.events.emit(GAME_EVENTS.ROOM_LAYOUT_READY, this.layout)
    this.emitPlayerPosition(true)
  }

  update(time: number): void {
    const body = this.player.body as Phaser.Physics.Arcade.Body

    if (this.inputLocked) {
      body.setVelocity(0, 0)
      this.playIdle(this.facing)
      this.promptText.setText(
        this.combat
          ? 'Terminal open — combat paused. Close the editor to resume.'
          : 'Terminal open — close the editor to resume movement.',
      )
      this.combat?.update(time)
      this.emitPlayerPosition(false)
      return
    }

    let vx = 0
    let vy = 0

    if (this.cursors.left?.isDown || this.wasd.A.isDown) vx -= 1
    if (this.cursors.right?.isDown || this.wasd.D.isDown) vx += 1
    if (this.cursors.up?.isDown || this.wasd.W.isDown) vy -= 1
    if (this.cursors.down?.isDown || this.wasd.S.isDown) vy += 1

    const length = Math.hypot(vx, vy)
    if (length > 0) {
      vx = (vx / length) * this.speed
      vy = (vy / length) * this.speed
      this.facing = this.resolveFacing(vx, vy)
      this.playWalk(this.facing)
      body.setVelocity(vx, vy)
    } else {
      body.setVelocity(0, 0)
      this.playIdle(this.facing)
    }

    this.player.setDepth(10 + Math.floor(this.player.y))
    this.combat?.update(time)
    this.updateProximityPrompt()
    this.handleInteract()
    this.emitPlayerPosition(false)
  }

  private paintEnvironment(): void {
    // Exterior / void backdrop
    this.add.rectangle(ROOM_WIDTH / 2, ROOM_HEIGHT / 2, ROOM_WIDTH, ROOM_HEIGHT, 0x050b16)

    const kinds = this.layout.cellKinds
    for (let row = 0; row < this.layout.rows; row += 1) {
      for (let col = 0; col < this.layout.cols; col += 1) {
        const x = col * ROOM_TILE_SIZE + ROOM_TILE_SIZE / 2
        const y = row * ROOM_TILE_SIZE + ROOM_TILE_SIZE / 2
        const kind = kinds[row]?.[col] ?? 'void'

        if (kind === 'void') {
          // Courtyard / outside — never leave a raw black hole in the stage.
          this.add.image(x, y, 'spy-exterior').setDepth(0)
          continue
        }

        if (kind === 'wall') {
          const n = kinds[row - 1]?.[col]
          const s = kinds[row + 1]?.[col]
          const e = kinds[row]?.[col + 1]
          const w = kinds[row]?.[col - 1]
          const isCorner =
            [n, s, e, w].filter((c) => c === 'wall' || c === 'void' || c == null).length >= 2 &&
            ((n !== 'floor' && w !== 'floor') ||
              (n !== 'floor' && e !== 'floor') ||
              (s !== 'floor' && w !== 'floor') ||
              (s !== 'floor' && e !== 'floor'))
          const key = isCorner ? 'spy-wall-corner' : 'spy-wall'
          const wall = this.solids.create(x, y, key) as Phaser.Physics.Arcade.Sprite
          wall.setImmovable(true)
          wall.refreshBody()
          wall.setDepth(2 + row)
          // Inner-edge cyan trim when this wall faces floor
          if (n === 'floor' || s === 'floor' || e === 'floor' || w === 'floor') {
            this.add.image(x, y + 10, 'spy-trim').setDepth(3).setAlpha(0.7)
          }
          continue
        }

        const variant = this.layout.floorVariants[row]?.[col] ?? 'base'
        const floorKey = FLOOR_TEXTURE_KEYS[variant] ?? 'spy-floor'
        this.add.image(x, y, floorKey).setDepth(0)
        if ((col + row) % 7 === 0 && variant === 'base') {
          this.add.image(x, y + 4, 'spy-shadow').setAlpha(0.2).setDepth(1)
        }
      }
    }

    // Entrance door frame markers (west vestibule)
    const start = this.layout.playerStart
    this.add
      .rectangle(start.x - 36, start.y - 40, 8, 56, 0x4a5568)
      .setDepth(4)
    this.add
      .rectangle(start.x - 36, start.y + 16, 8, 56, 0x4a5568)
      .setDepth(4)
    this.add.rectangle(start.x - 36, start.y - 12, 6, 10, 0x5de8ff).setDepth(5)
  }

  private spawnProps(): void {
    for (const prop of this.layout.props) {
      const key = PROP_TEXTURE_KEYS[prop.kind]
      if (!key) continue
      if (prop.solid) {
        const sprite = this.solids.create(prop.x, prop.y, key) as Phaser.Physics.Arcade.Sprite
        sprite.setOrigin(0.5, 0.75)
        sprite.setImmovable(true)
        // Keep collision footprint centered on the tile despite visual origin shift.
        const body = sprite.body as Phaser.Physics.Arcade.StaticBody
        body.setSize(ROOM_TILE_SIZE - 8, ROOM_TILE_SIZE - 10)
        body.setOffset(4, 8)
        sprite.refreshBody()
        sprite.setDepth(8 + prop.y + prop.depthBias)
        this.add
          .image(prop.x, prop.y + 12, 'spy-shadow')
          .setAlpha(0.35)
          .setDepth(4)
      } else {
        this.add
          .image(prop.x, prop.y - 8, key)
          .setDepth(6 + prop.y)
      }
    }
  }

  private spawnInteractives(): void {
    this.mandatorySprites = []
    for (let i = 0; i < this.layout.mandatoryTerminals.length; i += 1) {
      const pos = this.layout.mandatoryTerminals[i]!
      const sprite = this.solids.create(
        pos.x,
        pos.y,
        'spy-terminal-mandatory',
      ) as Phaser.Physics.Arcade.Sprite
      sprite.setOrigin(0.5, 0.75)
      sprite.setImmovable(true)
      const body = sprite.body as Phaser.Physics.Arcade.StaticBody
      body.setSize(ROOM_TILE_SIZE - 8, ROOM_TILE_SIZE - 10)
      body.setOffset(4, 8)
      sprite.refreshBody()
      sprite.setDepth(8 + pos.y)
      sprite.setData('kind', 'mandatory')
      sprite.setData('index', i)
      const binding = this.terminalBindings[i]
      if (binding) {
        sprite.setData('deviceId', binding.deviceId)
        sprite.setData('challengeId', binding.challengeId)
        sprite.setData('documentation', binding.documentation)
        sprite.setData('finalLock', binding.finalLock)
      }
      this.mandatorySprites.push(sprite)
      this.add.image(pos.x, pos.y + 12, 'spy-shadow').setAlpha(0.3).setDepth(4)
    }

    this.exitSprite = this.solids.create(
      this.layout.exit.x,
      this.layout.exit.y,
      this.exitReady ? 'spy-exit-unlocked' : 'spy-exit-locked',
    ) as Phaser.Physics.Arcade.Sprite
    this.exitSprite.setOrigin(0.5, 0.75)
    this.exitSprite.setImmovable(true)
    {
      const body = this.exitSprite.body as Phaser.Physics.Arcade.StaticBody
      body.setSize(ROOM_TILE_SIZE - 8, ROOM_TILE_SIZE - 10)
      body.setOffset(4, 8)
    }
    this.exitSprite.refreshBody()
    this.exitSprite.setDepth(8 + this.layout.exit.y)
  }

  private emitPlayerPosition(force: boolean): void {
    const x = Math.round(this.player.x)
    const y = Math.round(this.player.y)
    if (
      !force &&
      x === this.lastEmitX &&
      y === this.lastEmitY &&
      this.facing === this.lastEmitFacing
    ) {
      return
    }
    this.lastEmitX = x
    this.lastEmitY = y
    this.lastEmitFacing = this.facing
    this.game.events.emit(GAME_EVENTS.PLAYER_MOVED, { x, y, facing: this.facing })
  }

  private createRookieAnimations(): void {
    const mk = (key: string, frames: number[], frameRate: number, repeat: number) => {
      if (this.anims.exists(key)) return
      this.anims.create({
        key,
        frames: frames.map((frame) => ({ key: ROOKIE_KEY, frame })),
        frameRate,
        repeat,
      })
    }
    mk('rookie-idle-down', [0], 1, -1)
    mk('rookie-walk-down', [1, 2, 3, 1], 8, -1)
    mk('rookie-idle-left', [4], 1, -1)
    mk('rookie-walk-left', [5, 6, 7, 5], 8, -1)
    mk('rookie-idle-right', [8], 1, -1)
    mk('rookie-walk-right', [9, 10, 11, 9], 8, -1)
    mk('rookie-idle-up', [12], 1, -1)
    mk('rookie-walk-up', [13, 14, 15, 13], 8, -1)
  }

  private resolveFacing(vx: number, vy: number): Facing {
    if (Math.abs(vx) > Math.abs(vy)) return vx < 0 ? 'left' : 'right'
    if (Math.abs(vy) > 0) return vy < 0 ? 'up' : 'down'
    return this.facing
  }

  private playWalk(facing: Facing): void {
    const key = `rookie-walk-${facing}`
    if (this.player.anims.currentAnim?.key !== key) this.player.anims.play(key, true)
  }

  private playIdle(facing: Facing): void {
    const key = `rookie-idle-${facing}`
    if (this.player.anims.currentAnim?.key !== key) this.player.anims.play(key, true)
  }

  private hasLineOfSight(target: WorldPos): boolean {
    // Sample grid cells between player feet and target; reject if a wall cell intervenes.
    const x0 = this.player.x
    const y0 = this.player.y - 8
    const steps = 8
    for (let i = 1; i < steps; i += 1) {
      const t = i / steps
      const x = x0 + (target.x - x0) * t
      const y = y0 + (target.y - y0) * t
      const col = Math.floor(x / ROOM_TILE_SIZE)
      const row = Math.floor(y / ROOM_TILE_SIZE)
      if (row < 0 || col < 0 || row >= ROOM_ROWS || col >= ROOM_COLS) return false
      // Allow the destination cell (terminal/exit footprint).
      if (i === steps - 1) continue
      if (this.wallBlocked[row]![col]) {
        // Props and terminals are solid; walls definitely block. Skip if this cell is the target footprint.
        const onTarget =
          Math.abs(col * ROOM_TILE_SIZE + ROOM_TILE_SIZE / 2 - target.x) < 4 &&
          Math.abs(row * ROOM_TILE_SIZE + ROOM_TILE_SIZE / 2 - target.y) < 4
        if (!onTarget) return false
      }
    }
    return true
  }

  private updateProximityPrompt(): void {
    this.nearTerminalIndex = -1
    this.nearExit = false

    let bestTerminal = Infinity
    for (let i = 0; i < this.mandatorySprites.length; i += 1) {
      const sprite = this.mandatorySprites[i]!
      const dist = Phaser.Math.Distance.Between(this.player.x, this.player.y, sprite.x, sprite.y)
      if (dist < INTERACT_RANGE && dist < bestTerminal && this.hasLineOfSight(sprite)) {
        bestTerminal = dist
        this.nearTerminalIndex = i
      }
    }

    const exitDist = Phaser.Math.Distance.Between(
      this.player.x,
      this.player.y,
      this.exitSprite.x,
      this.exitSprite.y,
    )
    this.nearExit =
      this.exitReady && exitDist < INTERACT_RANGE + 12 && this.hasLineOfSight(this.exitSprite)

    if (this.nearExit) {
      this.promptText.setText('Press E to leave through the exit and complete the mission.')
    } else if (this.nearTerminalIndex >= 0) {
      this.promptText.setText('Press E to open the API request editor.')
    } else if (this.exitReady) {
      this.promptText.setText('Exit unlocked. Reach the green door and press E to complete.')
    } else if (this.combat) {
      this.promptText.setText('WASD / arrows · E interact · Aim with mouse · Click / Space fire')
    } else {
      this.promptText.setText('Move with WASD or arrows. Press E near a terminal.')
    }
  }

  private handleInteract(): void {
    if (!Phaser.Input.Keyboard.JustDown(this.interactKey)) return
    if (this.inputLocked) return

    if (this.nearExit) {
      this.game.events.emit(GAME_EVENTS.EXIT_REACHED, { missionId: this.missionId })
      return
    }
    if (this.nearTerminalIndex >= 0) {
      const sprite = this.mandatorySprites[this.nearTerminalIndex]
      const binding = this.terminalBindings[this.nearTerminalIndex]
      this.game.events.emit(GAME_EVENTS.OPEN_TERMINAL, {
        deviceId: (sprite?.getData('deviceId') as string) || binding?.deviceId || this.deviceId,
        challengeId:
          (sprite?.getData('challengeId') as string) || binding?.challengeId || undefined,
        documentation:
          (sprite?.getData('documentation') as string) ||
          binding?.documentation ||
          this.documentation,
        finalLock: Boolean(sprite?.getData('finalLock') ?? binding?.finalLock),
        missionId: this.missionId,
        terminalIndex: this.nearTerminalIndex,
      })
    }
  }

  private onExitReady = (payload?: { ready?: boolean }): void => {
    this.exitReady = Boolean(payload?.ready ?? true)
    this.exitSprite.setTexture(this.exitReady ? 'spy-exit-unlocked' : 'spy-exit-locked')
  }

  private onInputLock = (payload?: { locked?: boolean }): void => {
    this.inputLocked = Boolean(payload?.locked)
    // Phaser KeyboardManager preventDefaults captured keys globally — hand them
    // back to the DOM while the terminal/pause UI owns focus.
    const keyboard = this.input.keyboard
    if (keyboard) {
      if (this.inputLocked) {
        keyboard.enabled = false
        keyboard.disableGlobalCapture()
        keyboard.resetKeys()
      } else {
        keyboard.enabled = true
        keyboard.enableGlobalCapture()
      }
    }
    if (this.inputLocked && this.player?.body) {
      const body = this.player.body as Phaser.Physics.Arcade.Body
      body.setVelocity(0, 0)
    }
    this.combat?.setPaused(this.inputLocked)
  }
}
