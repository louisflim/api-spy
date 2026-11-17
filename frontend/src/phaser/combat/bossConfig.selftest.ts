/**
 * Level 7 boss placement + tuning self-test — npx tsx src/phaser/combat/bossConfig.selftest.ts
 */
import { generateRoomLayout, ROOM_TILE_SIZE } from '../room/generateRoomLayout'
import { BOSS_CONFIGS, bossConfigFor, bossConfigForRank, planBossSpawn } from './bossConfig'
import { enemyConfigForRank, enemyRosterForRank } from './enemyTypes'
import { NavigationGrid } from './navigationGrid'
import { hasPath } from './pathfind'
import { buildCombatExclusionSet } from './patrolZones'

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg)
}

const recruit = bossConfigForRank('RECRUIT')
const field = bossConfigForRank('FIELD_AGENT')
const special = bossConfigForRank('SPECIAL_AGENT')
assert(recruit.maxHealth < field.maxHealth && field.maxHealth < special.maxHealth, 'durability rises with rank')
assert(recruit.volleySize <= field.volleySize && field.volleySize <= special.volleySize, 'aggression rises with rank')

for (const roomInstance of [0, 1, 2]) {
  for (const terminals of [5, 6]) {
    const layout = generateRoomLayout({
      missionId: 'm1-recruit-l7',
      rank: 'RECRUIT',
      roomInstance,
      mandatoryTerminalCount: terminals,
    })
    assert(layout.footprintId === 'security_command_nexus', `L7 uses Command Nexus (${layout.footprintId})`)
    const nav = NavigationGrid.fromLayout(layout)
    const a = planBossSpawn(layout, nav)
    const b = planBossSpawn(layout, nav)
    assert(a && b, 'boss post found')
    assert(a.col === b.col && a.row === b.row, 'deterministic post')
    assert(!layout.blocked[a.row]![a.col], 'post is walkable')
    assert(!buildCombatExclusionSet(layout).has(`${a.col},${a.row}`), 'post outside spawn/terminal/exit clearance')
    const start = nav.worldToCell(layout.playerStart.x, layout.playerStart.y)
    assert(hasPath(nav, start, a), 'agent can reach the boss')
    const tiles = Math.abs(a.col - start.col) + Math.abs(a.row - start.row)
    assert(tiles >= 6, `boss post is away from the entrance (${tiles} tiles)`)
    for (const t of layout.mandatoryTerminals) {
      const cell = { col: Math.floor(t.x / ROOM_TILE_SIZE), row: Math.floor(t.y / ROOM_TILE_SIZE) }
      assert(!(cell.col === a.col && cell.row === a.row), 'not on a terminal')
    }
    console.log(`instance=${roomInstance} terminals=${terminals} boss post=(${a.col},${a.row})`)
  }
}
// One distinct boss per module, each with its own sheet and attack pattern.
const bosses = [1, 2, 3].map((m) => bossConfigFor(m, 'RECRUIT'))
assert(new Set(bosses.map((b) => b.sheetKey)).size === 3, 'distinct boss sheets per module')
assert(new Set(bosses.map((b) => b.pattern)).size === 3, 'distinct boss patterns per module')
assert(BOSS_CONFIGS.length === 3, 'three bosses registered')
for (const m of [1, 2, 3]) {
  assert(bossConfigFor(m, 'SPECIAL_AGENT').maxHealth > bossConfigFor(m, 'RECRUIT').maxHealth, `M${m} boss tougher on Special Agent`)
}

// Enemy roster: new enemy types appear on higher ranks; durability scales with rank.
assert(enemyRosterForRank('RECRUIT').join() === 'PATROL_GUARD', 'recruit roster')
assert(enemyRosterForRank('FIELD_AGENT').includes('RIFLEMAN'), 'field agent meets riflemen')
assert(enemyRosterForRank('SPECIAL_AGENT').includes('SHOCK_ENFORCER'), 'special agent meets enforcers')
assert(enemyConfigForRank('RIFLEMAN', 'RIFLEMAN').attackKind === 'ranged', 'rifleman shoots')
for (const t of ['PATROL_GUARD', 'RIFLEMAN', 'SHOCK_ENFORCER'] as const) {
  assert(enemyConfigForRank(t, 'SPECIAL_AGENT').maxHealth > enemyConfigForRank(t, 'RECRUIT').maxHealth, `${t} scales`)
  assert(enemyConfigForRank(t, 'RECRUIT').maxHealth >= 3, `${t} survives several hits`)
}
console.log('bossConfig.selftest OK')
