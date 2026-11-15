import type { WeaponDefinition } from './types'

/**
 * Starter sidearm for Combat Foundation Phase 1.
 * Additional guns register here without rewriting WeaponController / ProjectileManager.
 */
export const STARTER_SIDEARM: WeaponDefinition = {
  id: 'starter_sidearm',
  displayName: 'Field Sidearm',
  cooldownMs: 380,
  projectileSpeed: 320,
  damage: 1,
  projectileKey: 'spy-projectile-bolt',
  projectileLifespanMs: 900,
  projectileScale: 1,
}

/** Registry keyed by weapon id — Phase 1 ships one entry; two more can append later. */
export const WEAPON_REGISTRY: Record<string, WeaponDefinition> = {
  [STARTER_SIDEARM.id]: STARTER_SIDEARM,
}

export function requireWeapon(id: string): WeaponDefinition {
  const def = WEAPON_REGISTRY[id]
  if (!def) {
    throw new Error(`Unknown weapon id: ${id}`)
  }
  return def
}
