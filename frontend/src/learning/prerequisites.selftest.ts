import type { ConceptProgressRow } from '../api/client'
import type { Lesson, MissionSummary } from '../types/api'
import { lessonIntroductionKey, missingRequiredLessons } from './prerequisites'

function assert(condition: boolean, message: string): void {
  if (!condition) throw new Error(message)
}

const lessons: Lesson[] = [
  {
    id: 'rest-fundamentals',
    module: 1,
    order: 1,
    title: 'REST API Fundamentals',
    conceptTags: ['rest.fundamentals'],
    explanation: 'x',
    workedExample: {
      title: 't',
      notes: 'n',
      method: 'GET',
      path: '/api/training/x',
      responseStatus: 200,
      responseBody: {},
    },
    practiceScenario: { summary: 's', linkedMissionId: 'm1-recruit-l1', ungraded: true },
    graded: false,
  },
  {
    id: 'http-get',
    module: 1,
    order: 2,
    title: 'HTTP GET',
    conceptTags: ['http.method.get'],
    explanation: 'x',
    workedExample: {
      title: 't',
      notes: 'n',
      method: 'GET',
      path: '/api/training/x',
      responseStatus: 200,
      responseBody: {},
    },
    practiceScenario: { summary: 's', linkedMissionId: 'm1-recruit-l1', ungraded: true },
    graded: false,
  },
]

const mission: MissionSummary = {
  missionId: 'm1-recruit-l1',
  module: 1,
  rank: 'RECRUIT',
  level: 1,
  title: 'Retrieve a door resource',
  status: 'IMPLEMENTED',
  playable: true,
  primaryChallengeId: 'chal-m1-get-door-v1',
  requiredLessonIds: ['rest-fundamentals', 'http-get'],
  conceptTags: ['http.method.get', 'http.endpoint'],
  scenarioFocus: 'test',
}

const practiceOnly: ConceptProgressRow[] = [
  { conceptId: 'http.method.get', stage: 'PRACTICING', updatedAt: '2026-01-01T00:00:00Z' },
  { conceptId: 'http.endpoint', stage: 'PRACTICING', updatedAt: '2026-01-01T00:00:00Z' },
  { conceptId: 'http.response', stage: 'PRACTICING', updatedAt: '2026-01-01T00:00:00Z' },
]

assert(
  missingRequiredLessons(mission, lessons, practiceOnly).length === 2,
  'Module 0 / practice PRACTICING must not clear required lessons',
)

const afterOneLesson: ConceptProgressRow[] = [
  ...practiceOnly,
  {
    conceptId: lessonIntroductionKey('rest-fundamentals'),
    stage: 'INTRODUCED',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

assert(
  missingRequiredLessons(mission, lessons, afterOneLesson).join(',') === 'http-get',
  'Only unopened lessons remain missing',
)

const afterAll: ConceptProgressRow[] = [
  ...afterOneLesson,
  {
    conceptId: lessonIntroductionKey('http-get'),
    stage: 'INTRODUCED',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

assert(
  missingRequiredLessons(mission, lessons, afterAll).length === 0,
  'Lesson-scoped markers clear the gate',
)

console.log('prerequisites.selftest: OK')
