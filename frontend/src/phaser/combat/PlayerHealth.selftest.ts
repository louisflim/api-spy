/**
 * PlayerHealth rules self-test — run via: npx tsx src/phaser/combat/PlayerHealth.selftest.ts
 */
import { PlayerHealth } from './PlayerHealth'
import { COMBAT_TUNING } from './combatTuning'

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg)
}

const health = new PlayerHealth(COMBAT_TUNING)
assert(health.getHearts() === 3, 'starts with 3 hearts')

let changes = 0
let deaths = 0
health.setListeners(() => {
  changes += 1
}, () => {
  deaths += 1
})

assert(health.takeDamage(1, 1000, COMBAT_TUNING.invulnerabilityMs), 'first hit lands')
assert(health.getHearts() === 2, 'one heart removed')
assert(!health.takeDamage(1, 1100, COMBAT_TUNING.invulnerabilityMs), 'invuln blocks rapid hit')
assert(health.getHearts() === 2, 'hearts unchanged during invuln')
assert(health.takeDamage(1, 1000 + COMBAT_TUNING.invulnerabilityMs + 1, COMBAT_TUNING.invulnerabilityMs), 'hit after window')
assert(health.getHearts() === 1, 'second heart removed')
assert(
  health.takeDamage(1, 1000 + COMBAT_TUNING.invulnerabilityMs * 2 + 2, COMBAT_TUNING.invulnerabilityMs),
  'fatal hit',
)
assert(health.getHearts() === 0, 'zero hearts')
assert(health.isDead(), 'dead flag')
assert(deaths === 1, 'death listener once')

health.reset()
assert(health.getHearts() === 3, 'reset restores 3')
assert(!health.isDead(), 'reset clears death')

console.log(`PlayerHealth.selftest OK — changes=${changes} deaths=${deaths}`)
