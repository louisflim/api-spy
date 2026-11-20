import type { WeaponDefinition } from './types'

/**
 * Limited gun catalog (proposal). Every gun has unlimited ammunition. The server owns prices,
 * ownership and upgrade levels (/api/rewards); combat stats live here. Guns and upgrades only
 * change combat — never API validation, devices or research scores.
 */
/** Free semi-automatic pistol: one shot per click. */
export const STARTER_SIDEARM: WeaponDefinition = {
  id: 'starter_sidearm',
  displayName: 'Field Pistol',
  cooldownMs: 420,
  projectileSpeed: 340,
  damage: 1,
  projectileKey: 'spy-projectile-bolt',
  projectileLifespanMs: 900,
  projectileScale: 1,
  fireMode: 'semi',
}

/** AK-47: fully automatic — hold to keep firing; overheats after a long burst (ammo unlimited). */
export const ASSAULT_RIFLE: WeaponDefinition = {
  id: 'assault_rifle',
  displayName: 'AK-47',
  cooldownMs: 140,
  projectileSpeed: 430,
  damage: 0.6,
  projectileKey: 'spy-projectile-bolt',
  projectileLifespanMs: 800,
  projectileScale: 0.75,
  projectileTint: 0xffe27a,
  fireMode: 'auto',
  heatShots: 12,
  overheatMs: 1200,
}

/** Shotgun: one blast per click that sprays pellets at short range. */
export const COMBAT_SHOTGUN: WeaponDefinition = {
  id: 'combat_shotgun',
  displayName: 'Shotgun',
  cooldownMs: 950,
  projectileSpeed: 380,
  damage: 0.7,
  projectileKey: 'spy-projectile-bolt',
  projectileLifespanMs: 340,
  projectileScale: 0.55,
  projectileTint: 0xffb070,
  fireMode: 'semi',
  pellets: 5,
  spreadDeg: 24,
}

export const WEAPON_REGISTRY: Record<string, WeaponDefinition> = {
  [STARTER_SIDEARM.id]: STARTER_SIDEARM,
  [ASSAULT_RIFLE.id]: ASSAULT_RIFLE,
  [COMBAT_SHOTGUN.id]: COMBAT_SHOTGUN,
}

/**
 * Pellet directions for one shot: a single round goes straight; a spray is spread evenly across
 * `spreadDeg` (with optional jitter so blasts do not look identical).
 */
export function shotDirections(
  weapon: Pick<WeaponDefinition, 'pellets' | 'spreadDeg'>,
  dirX: number,
  dirY: number,
  jitter: () => number = () => 0,
): { x: number; y: number }[] {
  const count = Math.max(1, Math.floor(weapon.pellets ?? 1))
  if (count === 1) return [{ x: dirX, y: dirY }]
  const spread = ((weapon.spreadDeg ?? 0) * Math.PI) / 180
  const base = Math.atan2(dirY, dirX)
  const out: { x: number; y: number }[] = []
  for (let i = 0; i < count; i++) {
    const t = count === 1 ? 0 : i / (count - 1) - 0.5
    const a = base + t * spread + jitter() * (spread / count) * 0.5
    out.push({ x: Math.cos(a), y: Math.sin(a) })
  }
  return out
}

export function requireWeapon(id: string): WeaponDefinition {
  const def = WEAPON_REGISTRY[id]
  if (!def) {
    throw new Error(`Unknown weapon id: ${id}`)
  }
  return def
}

/** EXP upgrade levels (0–3) bought on the server. */
export interface GunUpgrades {
  DAMAGE: number
  FIRE_RATE: number
  VELOCITY: number
}

export const NO_UPGRADES: GunUpgrades = { DAMAGE: 0, FIRE_RATE: 0, VELOCITY: 0 }

/** Per-level effect of each upgrade (shown in the Armory). */
export const UPGRADE_EFFECT = {
  DAMAGE: 0.25,
  FIRE_RATE: 0.12,
  VELOCITY: 0.15,
} as const

const clampLevel = (n: unknown) => Math.max(0, Math.min(3, Math.floor(Number(n) || 0)))

/**
 * The equipped gun with upgrades applied: +25% damage, −12% time between shots and +15% round
 * speed per level. Unknown ids fall back to the sidearm so a bad save never blocks play.
 */
export function buildLoadoutWeapon(
  weaponId: string | null | undefined,
  upgrades: Partial<GunUpgrades> = {},
): WeaponDefinition {
  const base = (weaponId && WEAPON_REGISTRY[weaponId]) || STARTER_SIDEARM
  const dmg = clampLevel(upgrades.DAMAGE)
  const rate = clampLevel(upgrades.FIRE_RATE)
  const vel = clampLevel(upgrades.VELOCITY)
  return {
    ...base,
    damage: Math.round(base.damage * (1 + UPGRADE_EFFECT.DAMAGE * dmg) * 100) / 100,
    cooldownMs: Math.round(base.cooldownMs * (1 - UPGRADE_EFFECT.FIRE_RATE * rate)),
    projectileSpeed: Math.round(base.projectileSpeed * (1 + UPGRADE_EFFECT.VELOCITY * vel)),
  }
}
