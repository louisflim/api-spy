import { generateRoomLayout } from './generateRoomLayout'

export function projectLayoutToMinimap(plan: ReturnType<typeof generateRoomLayout>): {
  cells: Set<string>
  voids: number
  walls: number
  solids: number
  terminals: number
  exits: number
} {
  const cells = new Set<string>()
  for (let r = 0; r < plan.rows; r += 1) {
    for (let c = 0; c < plan.cols; c += 1) {
      if (plan.cellKinds[r]![c] === 'wall') cells.add(`wall:${c},${r}`)
      if (plan.cellKinds[r]![c] === 'void') cells.add(`void:${c},${r}`)
    }
  }
  for (const prop of plan.props) {
    if (!prop.solid) continue
    cells.add(`solid:${prop.col},${prop.row}`)
  }
  let terminals = 0
  let exits = 0
  for (const term of plan.interactives) {
    if (term.kind === 'mandatory_terminal' || term.kind === 'practice_terminal') {
      cells.add(`term:${term.col},${term.row}`)
      terminals += 1
    }
    if (term.kind === 'exit') {
      cells.add(`exit:${term.col},${term.row}`)
      exits += 1
    }
  }
  return {
    cells,
    voids: [...cells].filter((k) => k.startsWith('void:')).length,
    walls: [...cells].filter((k) => k.startsWith('wall:')).length,
    solids: [...cells].filter((k) => k.startsWith('solid:')).length,
    terminals,
    exits,
  }
}

function assert(condition: boolean, message: string): void {
  if (!condition) throw new Error(message)
}

const plan = generateRoomLayout({
  missionId: 'm1-recruit-l1',
  rank: 'RECRUIT',
  roomInstance: 0,
  mandatoryTerminalCount: 1,
})

const projected = projectLayoutToMinimap(plan)
assert(projected.cells.size > 40, 'projected cells should include irregular walls/voids')
assert(projected.voids > 0, 'minimap must encode void notch')
assert(projected.walls > 0, 'minimap must encode walls')
assert(projected.exits === 1, 'minimap must encode exit')

for (const prop of plan.props.filter((p) => p.solid)) {
  if (!projected.cells.has(`solid:${prop.col},${prop.row}`)) {
    throw new Error(`minimap missing solid ${prop.kind} at ${prop.col},${prop.row}`)
  }
  if (!plan.blocked[prop.row]![prop.col]) {
    throw new Error(`collision missing solid ${prop.kind}`)
  }
  if (plan.cellKinds[prop.row]![prop.col] !== 'floor') {
    throw new Error(`solid prop on non-floor cell ${prop.kind}`)
  }
}

console.log('minimapSync.selftest: OK', {
  seed: plan.seed,
  projected: projected.cells.size,
  voids: projected.voids,
  walls: projected.walls,
  solids: projected.solids,
})
