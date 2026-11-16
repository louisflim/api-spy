import { paths, safeInternalPath } from './paths'

function assert(cond: unknown, msg: string): asserts cond {
  if (!cond) throw new Error(msg)
}

const briefing = paths.missionBriefing('m1-recruit-l7')
assert(briefing === '/missions/m1-recruit-l7/briefing', 'briefing path')

assert(
  paths.missionTraining('m1-recruit-l1') === '/missions/m1-recruit-l1/training',
  'L1 level training',
)
assert(
  paths.missionTraining('m1-recruit-l3') === '/missions/m1-recruit-l3/training',
  'L3 level training',
)
assert(
  paths.missionTraining('m1-recruit-l7') === '/missions/m1-recruit-l7/training',
  'L7 level training',
)

// Briefing review must use mission training — not Module 0.
for (const missionId of [
  'm1-recruit-l1',
  'm1-recruit-l2',
  'm1-recruit-l3',
  'm1-recruit-l4',
  'm1-recruit-l5',
  'm1-recruit-l6',
  'm1-recruit-l7',
  'm2-field-l1',
  'm3-special-l3',
]) {
  const training = paths.missionTraining(missionId)
  assert(training.startsWith(`/missions/${missionId}/training`), `training ${missionId}`)
  assert(!training.includes('training-academy'), `not module0 ${missionId}`)
  assert(
    paths.missionBriefing(missionId) === `/missions/${missionId}/briefing`,
    `briefing return ${missionId}`,
  )
}

// Module 0 academy stays on its own route (optional returnTo still safe).
assert(paths.trainingAcademy === '/training-academy', 'module0 academy route')
const academy = paths.trainingAcademyFrom(briefing)
assert(
  academy === `/training-academy?returnTo=${encodeURIComponent(briefing)}`,
  'academy optional returnTo',
)
assert(safeInternalPath(briefing) === briefing, 'safe briefing')
assert(safeInternalPath('https://evil.example') === null, 'reject absolute')
assert(safeInternalPath('//evil.example') === null, 'reject protocol-relative')

console.log('paths.selftest: OK')
