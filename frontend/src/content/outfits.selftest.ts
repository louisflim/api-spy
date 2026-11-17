/**
 * Outfit unlock rules (proposal: rank skin after all 21 missions of that rank, legendary after
 * all 63) — npx tsx src/content/outfits.selftest.ts
 */
import { MISSIONS_PER_RANK, MISSIONS_TOTAL, OUTFITS, findOutfit, outfitStatus } from './outfits'

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg)
}

const ids = (slug: string) =>
  [1, 2, 3].flatMap((m) => [1, 2, 3, 4, 5, 6, 7].map((l) => `m${m}-${slug}-l${l}`))

const none = new Set<string>()
for (const o of OUTFITS) {
  const s = outfitStatus(o, none)
  assert(s.unlocked === (o.unlock.kind === 'free'), `${o.id}: only starters unlocked for a new agent`)
}

const recruitModule1 = new Set(ids('recruit').slice(0, 7))
const recruitSkin = findOutfit('skin-recruit')
assert(!outfitStatus(recruitSkin, recruitModule1).unlocked, 'Module 1 alone does not award the Recruit skin')
assert(outfitStatus(recruitSkin, recruitModule1).completed === 7, 'progress counts cleared Recruit missions')

const allRecruit = new Set(ids('recruit'))
assert(allRecruit.size === MISSIONS_PER_RANK, '21 recruit missions')
assert(outfitStatus(recruitSkin, allRecruit).unlocked, 'all 21 Recruit missions award the Recruit skin')
assert(!outfitStatus(findOutfit('skin-field'), allRecruit).unlocked, 'Recruit clears do not count for Field Agent')
assert(!outfitStatus(findOutfit('skin-baba-yaga'), allRecruit).unlocked, 'legendary needs every rank')

const everything = new Set([...ids('recruit'), ...ids('field'), ...ids('special')])
assert(everything.size === MISSIONS_TOTAL, '63 missions')
for (const o of OUTFITS) assert(outfitStatus(o, everything).unlocked, `${o.id} unlocked after all 63`)
assert(findOutfit('nope').id === 'rookie', 'unknown outfit falls back to the Rookie')

console.log(`outfits.selftest OK — ${OUTFITS.length} outfits`)
