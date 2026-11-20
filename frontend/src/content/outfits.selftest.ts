/**
 * Outfit and gun-skin unlock rules — npx tsx src/content/outfits.selftest.ts
 * Starter characters: owned via the free pick or bought with coins (server ownership).
 * Rank skins / rank gun skins: all 21 missions of that rank. Legendary: all 63 missions.
 */
import { MISSIONS_PER_RANK, MISSIONS_TOTAL, OUTFITS, findOutfit, outfitStatus } from './outfits'
import { GUN_SKINS, findGunSkin, gunSkinStatus, gunSprite } from './gunSkins'

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg)
}

const ids = (slug: string) =>
  [1, 2, 3].flatMap((m) => [1, 2, 3, 4, 5, 6, 7].map((l) => `m${m}-${slug}-l${l}`))

const none = new Set<string>()
// A brand-new agent (no free pick yet) owns nothing.
for (const o of OUTFITS) assert(!outfitStatus(o, none, new Set()).unlocked, `${o.id}: locked before the free pick`)

// After picking Agent Mira for free, only Mira is unlocked; the other starters must be bought.
const mira = new Set(['agent-girl'])
assert(outfitStatus(findOutfit('agent-girl'), none, mira).unlocked, 'free pick unlocks that character')
assert(!outfitStatus(findOutfit('rookie'), none, mira).unlocked, 'other starters stay locked')
assert(outfitStatus(findOutfit('rookie'), none, mira).requirement === 'Buy with coins', 'starters are bought with coins')
// Unknown ownership (rewards not loaded) never unlocks a starter.
assert(!outfitStatus(findOutfit('agent-boy'), none, null).unlocked, 'unknown ownership stays locked')

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
for (const o of OUTFITS.filter((x) => x.unlock.kind !== 'character')) {
  assert(outfitStatus(o, everything).unlocked, `${o.id} unlocked after all 63`)
}
assert(findOutfit('nope').id === 'rookie', 'unknown outfit falls back to the Rookie')

// Gun skins follow the same rank rules; the standard finish is always available.
assert(gunSkinStatus(findGunSkin('standard'), none).unlocked, 'standard gun skin is free')
assert(!gunSkinStatus(findGunSkin('recruit'), recruitModule1).unlocked, 'Recruit gun skin needs all 21')
assert(gunSkinStatus(findGunSkin('recruit'), allRecruit).unlocked, 'Recruit gun skin after all 21 Recruit')
assert(!gunSkinStatus(findGunSkin('special'), allRecruit).unlocked, 'Special gun skin needs Special Agent clears')
assert(!gunSkinStatus(findGunSkin('legendary'), allRecruit).unlocked, 'Legendary gun skin needs all 63')
for (const s of GUN_SKINS) assert(gunSkinStatus(s, everything).unlocked, `${s.id} gun skin unlocked after all 63`)

// Every gun has a sprite in every skin; unknown guns fall back to the pistol.
for (const gun of ['starter_sidearm', 'assault_rifle', 'combat_shotgun']) {
  for (const s of GUN_SKINS) assert(gunSprite(gun, s.id).path === `/assets/weapons/${gun}-${s.id}.png`, `${gun}/${s.id}`)
}
assert(gunSprite('ray_gun', 'standard').key === 'gun-starter_sidearm-standard', 'unknown gun uses the pistol sprite')

console.log(`outfits.selftest OK — ${OUTFITS.length} outfits, ${GUN_SKINS.length} gun skins`)
