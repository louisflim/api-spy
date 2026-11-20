/**
 * Gun catalog, fire modes, shotgun spray and EXP upgrades
 * — npx tsx src/phaser/combat/weapons.selftest.ts
 */
import {
  ASSAULT_RIFLE,
  buildLoadoutWeapon,
  COMBAT_SHOTGUN,
  shotDirections,
  STARTER_SIDEARM,
  WEAPON_REGISTRY,
} from './weapons'

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg)
}

const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b)
const near = (a: number, b: number) => Math.abs(a - b) < 1e-9

// Catalog: free pistol (tap), AK-47 (hold), shotgun (tap, spray).
assert(Object.keys(WEAPON_REGISTRY).join() === 'starter_sidearm,assault_rifle,combat_shotgun', 'three guns')
assert(STARTER_SIDEARM.fireMode === 'semi', 'pistol fires once per click')
assert(ASSAULT_RIFLE.fireMode === 'auto', 'AK-47 is fully automatic')
assert(COMBAT_SHOTGUN.fireMode === 'semi' && (COMBAT_SHOTGUN.pellets ?? 1) > 1, 'shotgun taps and sprays')

// No upgrades: the gun's own stats. Unknown or missing ids fall back to the pistol.
assert(same(buildLoadoutWeapon('starter_sidearm'), STARTER_SIDEARM), 'no upgrades keeps base stats')
assert(buildLoadoutWeapon('ray_gun').id === STARTER_SIDEARM.id, 'unknown gun falls back to pistol')
assert(buildLoadoutWeapon(null).id === STARTER_SIDEARM.id, 'missing gun falls back to pistol')

// Upgrades keep the fire mode and pellet count, and change only damage / cadence / speed.
const shotgunMaxed = buildLoadoutWeapon('combat_shotgun', { DAMAGE: 3, FIRE_RATE: 3, VELOCITY: 3 })
assert(shotgunMaxed.pellets === COMBAT_SHOTGUN.pellets && shotgunMaxed.fireMode === 'semi', 'upgrades keep behaviour')
assert(shotgunMaxed.damage === Math.round(0.7 * 1.75 * 100) / 100, `max damage ${shotgunMaxed.damage}`)
assert(shotgunMaxed.cooldownMs === Math.round(950 * 0.64), `max cooldown ${shotgunMaxed.cooldownMs}`)
const clamped = buildLoadoutWeapon('assault_rifle', { DAMAGE: 9, FIRE_RATE: -2 })
assert(clamped.cooldownMs === ASSAULT_RIFLE.cooldownMs, 'negative level clamps to 0')
buildLoadoutWeapon('combat_shotgun', { DAMAGE: 3 })
assert(WEAPON_REGISTRY.combat_shotgun!.damage === COMBAT_SHOTGUN.damage, 'catalog unchanged')

// Shot directions: one straight round, or pellets fanned evenly across the spread.
assert(same(shotDirections(STARTER_SIDEARM, 1, 0), [{ x: 1, y: 0 }]), 'single round goes straight')
const fan = shotDirections(COMBAT_SHOTGUN, 1, 0)
assert(fan.length === COMBAT_SHOTGUN.pellets, 'one direction per pellet')
const angles = fan.map((d) => (Math.atan2(d.y, d.x) * 180) / Math.PI)
assert(near(angles[0]!, -12) && near(angles[angles.length - 1]!, 12), `fan spans the spread: ${angles}`)
assert(fan.every((d) => near(Math.hypot(d.x, d.y), 1)), 'unit directions')

// Anti-spam: no gun fires faster than its cooldown; semi guns need a fresh click each shot and
// the AK-47 overheats after a long burst.
assert(STARTER_SIDEARM.cooldownMs >= 400, 'pistol cannot be spammed faster than ~2.4 shots/s')
assert(COMBAT_SHOTGUN.cooldownMs >= 900, 'shotgun has a heavy pump delay')
assert((ASSAULT_RIFLE.heatShots ?? 0) > 0 && (ASSAULT_RIFLE.overheatMs ?? 0) >= 1000, 'AK-47 overheats')

// Balance: sustained damage per second, including the AK-47's overheat pause.
const dps = (w: typeof STARTER_SIDEARM) => {
  const perShot = w.damage * (w.pellets ?? 1)
  if (!w.heatShots) return (perShot * 1000) / w.cooldownMs
  const burstMs = (w.heatShots - 1) * w.cooldownMs + (w.overheatMs ?? 0)
  return (perShot * w.heatShots * 1000) / burstMs
}
for (const gun of [ASSAULT_RIFLE, COMBAT_SHOTGUN]) {
  const ratio = dps(gun) / dps(STARTER_SIDEARM)
  assert(ratio > 1 && ratio < 1.8, `${gun.id} dps ratio ${ratio.toFixed(2)}`)
}

console.log('weapons self-test passed')
