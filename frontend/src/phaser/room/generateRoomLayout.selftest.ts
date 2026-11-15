import { generateRoomLayout, layoutsEqualForSeed, PRACTICE_SLOT_COUNT } from './generateRoomLayout'
import { footprintIdForMission } from './missionFootprint'
import { canAccessExit, canAccessTarget } from './reachability'
import { projectLayoutToMinimap } from './minimapSync.selftest'

function assert(condition: boolean, message: string): void {
  if (!condition) throw new Error(message)
}

function gridOf(plan: { tileSize: number }, pos: { x: number; y: number }) {
  return {
    col: Math.floor(pos.x / plan.tileSize),
    row: Math.floor(pos.y / plan.tileSize),
  }
}

function wallSignature(plan: ReturnType<typeof generateRoomLayout>): string {
  return plan.cellKinds.map((row) => row.map((c) => (c === 'wall' ? '#' : c === 'void' ? '~' : '.')).join('')).join('|')
}

function assertPlayable(plan: ReturnType<typeof generateRoomLayout>, label: string): void {
  assert(!plan.usedFallback, `${label}: must not use fallback layout`)
  const spawn = gridOf(plan, plan.playerStart)
  assert(plan.cellKinds[spawn.row]![spawn.col] === 'floor', `${label}: spawn floor`)
  assert(!plan.blocked[spawn.row]![spawn.col], `${label}: spawn unblocked`)

  for (const terminal of plan.mandatoryTerminals) {
    const target = gridOf(plan, terminal)
    assert(
      canAccessTarget(plan.blocked, spawn, target),
      `${label}: mandatory terminal reachable ${target.col},${target.row}`,
    )
  }
  for (const slot of plan.practiceSlots) {
    const target = gridOf(plan, slot)
    assert(
      canAccessTarget(plan.blocked, spawn, target),
      `${label}: practice slot reachable ${target.col},${target.row}`,
    )
  }
  assert(
    canAccessExit(plan.blocked, spawn, gridOf(plan, plan.exit)),
    `${label}: exit reachable`,
  )
  assert(
    plan.props.every((p) => !p.solid || plan.blocked[p.row]![p.col]),
    `${label}: solid props blocked`,
  )
  assert(
    plan.props.every((p) => plan.cellKinds[p.row]![p.col] === 'floor'),
    `${label}: props on floor`,
  )
  assert(
    !plan.props.some((p) => p.solid && p.col === spawn.col && p.row === spawn.row),
    `${label}: no solid on spawn`,
  )
}

assert(footprintIdForMission('m1-recruit-l1') === 'security_office', 'L1 → office')
assert(footprintIdForMission('m1-recruit-l2') === 'security_annex', 'L2 → annex')
assert(footprintIdForMission('m1-recruit-l3') === 'security_server_wing', 'L3 → server wing')
assert(footprintIdForMission('m1-recruit-l4') === 'security_checkpoint', 'L4 → checkpoint')
assert(footprintIdForMission('m1-recruit-l5') === 'security_clearance_hall', 'L5 → clearance hall')
assert(footprintIdForMission('m1-recruit-l6') === 'security_purge_bay', 'L6 → purge bay')

const missions = [
  { missionId: 'm1-recruit-l1', footprintId: 'security_office' as const },
  { missionId: 'm1-recruit-l2', footprintId: 'security_annex' as const },
  { missionId: 'm1-recruit-l3', footprintId: 'security_server_wing' as const },
  { missionId: 'm1-recruit-l4', footprintId: 'security_checkpoint' as const },
  { missionId: 'm1-recruit-l5', footprintId: 'security_clearance_hall' as const },
  { missionId: 'm1-recruit-l6', footprintId: 'security_purge_bay' as const },
]

const signatures = new Map<string, string>()

for (const { missionId, footprintId } of missions) {
  const base = {
    missionId,
    rank: 'RECRUIT',
    roomInstance: 0,
    mandatoryTerminalCount: 1,
  }
  const a = generateRoomLayout(base)
  const b = generateRoomLayout(base)
  assert(layoutsEqualForSeed(a, b), `${missionId}: deterministic`)
  assert(a.footprintId === footprintId, `${missionId}: footprint id`)
  assertPlayable(a, missionId)

  // Replay / same mission keeps footprint when roomInstance changes (props may differ).
  const replayish = generateRoomLayout({ ...base, roomInstance: 2 })
  assert(replayish.footprintId === footprintId, `${missionId}: footprint stable across roomInstance`)
  assertPlayable(replayish, `${missionId}@reset`)

  signatures.set(missionId, wallSignature(a))

  const tiers = [1, 3, 5] as const
  for (const mandatoryTerminalCount of tiers) {
    for (const roomInstance of [0, 1, 3, 7]) {
      const plan = generateRoomLayout({
        ...base,
        roomInstance,
        mandatoryTerminalCount,
      })
      assert(
        plan.mandatoryTerminals.length === mandatoryTerminalCount,
        `${missionId} tier ${mandatoryTerminalCount}: count`,
      )
      assert(plan.practiceSlots.length === PRACTICE_SLOT_COUNT, `${missionId}: practice slots`)
      assert(plan.footprintId === footprintId, `${missionId} tier: footprint`)
      assertPlayable(plan, `${missionId}/m${mandatoryTerminalCount}/i${roomInstance}`)
      const projected = projectLayoutToMinimap(plan)
      assert(projected.voids > 0, `${missionId}: minimap voids`)
      assert(projected.walls > 0, `${missionId}: minimap walls`)
      assert(
        projected.terminals === plan.mandatoryTerminals.length + plan.practiceSlots.length,
        `${missionId}: minimap terminals`,
      )
    }
  }
}

assert(
  signatures.get('m1-recruit-l1') !== signatures.get('m1-recruit-l2'),
  'L1 and L2 wall geometry must differ',
)
assert(
  signatures.get('m1-recruit-l2') !== signatures.get('m1-recruit-l3'),
  'L2 and L3 wall geometry must differ',
)
assert(
  signatures.get('m1-recruit-l1') !== signatures.get('m1-recruit-l3'),
  'L1 and L3 wall geometry must differ',
)

console.log('generateRoomLayout.selftest: OK', {
  footprints: Object.fromEntries(
    missions.map(({ missionId, footprintId }) => [missionId, footprintId]),
  ),
  wallSignatureLens: Object.fromEntries(
    [...signatures.entries()].map(([k, v]) => [k, v.length]),
  ),
})
