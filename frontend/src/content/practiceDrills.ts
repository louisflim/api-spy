import type { MissionDetail } from '../types/api'

/** Authored ungraded practice drills keyed by graded mission id. */
export const PRACTICE_BY_MISSION: Record<string, MissionDetail> = {
  'm1-recruit-l1': {
    mission: {
      missionId: 'practice-m1-recruit-l1',
      module: 1,
      rank: 'RECRUIT',
      level: 1,
      title: 'Practice: door drill',
      status: 'PRACTICE',
      playable: true,
      primaryChallengeId: 'chal-practice-m1-l1',
      requiredLessonIds: [],
      conceptTags: ['http.method.get', 'http.endpoint'],
      scenarioFocus: 'Ungraded practice drill',
    },
    challenge: {
      challengeId: 'chal-practice-m1-l1',
      variantId: 'practice-door-drill-v1',
      deviceId: 'practice-panel',
      conceptTags: ['http.method.get', 'http.endpoint'],
      documentation:
        'PRACTICE MODE — educational review only.\n\nMethod: GET\nPath: /api/practice/doors/drill',
      expectedMethod: 'GET',
      pathTemplate: '/api/practice/doors/drill',
      pathParameters: {},
      queryParameters: {},
    },
    conceptVariants: [],
  },
  'm1-recruit-l2': {
    mission: {
      missionId: 'practice-m1-recruit-l2',
      module: 1,
      rank: 'RECRUIT',
      level: 2,
      title: 'Practice: lock drill',
      status: 'PRACTICE',
      playable: true,
      primaryChallengeId: 'chal-practice-m1-l2',
      requiredLessonIds: [],
      conceptTags: ['http.endpoint', 'http.path_parameter'],
      scenarioFocus: 'Ungraded practice drill',
    },
    challenge: {
      challengeId: 'chal-practice-m1-l2',
      variantId: 'practice-lock-drill-v1',
      deviceId: 'practice-lock',
      conceptTags: ['http.endpoint', 'http.path_parameter'],
      documentation:
        'PRACTICE MODE — educational review only.\n\nMethod: GET\nPath: /api/practice/locks/drill-bay',
      expectedMethod: 'GET',
      pathTemplate: '/api/practice/locks/{lockId}',
      pathParameters: { lockId: 'drill-bay' },
      queryParameters: {},
    },
    conceptVariants: [],
  },
  'm1-recruit-l3': {
    mission: {
      missionId: 'practice-m1-recruit-l3',
      module: 1,
      rank: 'RECRUIT',
      level: 3,
      title: 'Practice: sensor drill',
      status: 'PRACTICE',
      playable: true,
      primaryChallengeId: 'chal-practice-m1-l3',
      requiredLessonIds: [],
      conceptTags: ['http.endpoint', 'http.query_parameter'],
      scenarioFocus: 'Ungraded practice drill',
    },
    challenge: {
      challengeId: 'chal-practice-m1-l3',
      variantId: 'practice-sensor-drill-v1',
      deviceId: 'practice-sensor',
      conceptTags: ['http.endpoint', 'http.query_parameter'],
      documentation:
        'PRACTICE MODE — educational review only.\n\nMethod: GET\nPath: /api/practice/sensors?bay=training',
      expectedMethod: 'GET',
      pathTemplate: '/api/practice/sensors',
      pathParameters: {},
      queryParameters: { bay: 'training' },
    },
    conceptVariants: [],
  },
  'm1-recruit-l4': {
    mission: {
      missionId: 'practice-m1-recruit-l4',
      module: 1,
      rank: 'RECRUIT',
      level: 4,
      title: 'Practice: registration drill',
      status: 'PRACTICE',
      playable: true,
      primaryChallengeId: 'chal-practice-m1-l4',
      requiredLessonIds: [],
      conceptTags: ['http.method.post', 'http.endpoint'],
      scenarioFocus: 'Ungraded practice drill',
    },
    challenge: {
      challengeId: 'chal-practice-m1-l4',
      variantId: 'practice-registration-drill-v1',
      deviceId: 'practice-registration',
      conceptTags: ['http.method.post', 'http.endpoint'],
      documentation:
        'PRACTICE MODE — educational review only.\n\nMethod: POST\nPath: /api/practice/registrations/drill',
      expectedMethod: 'POST',
      pathTemplate: '/api/practice/registrations/drill',
      pathParameters: {},
      queryParameters: {},
    },
    conceptVariants: [],
  },
  'm1-recruit-l5': {
    mission: {
      missionId: 'practice-m1-recruit-l5',
      module: 1,
      rank: 'RECRUIT',
      level: 5,
      title: 'Practice: clearance update drill',
      status: 'PRACTICE',
      playable: true,
      primaryChallengeId: 'chal-practice-m1-l5',
      requiredLessonIds: [],
      conceptTags: ['http.method.put', 'http.endpoint', 'http.path_parameter'],
      scenarioFocus: 'Ungraded practice drill',
    },
    challenge: {
      challengeId: 'chal-practice-m1-l5',
      variantId: 'practice-clearance-drill-v1',
      deviceId: 'practice-clearance',
      conceptTags: ['http.method.put', 'http.endpoint', 'http.path_parameter'],
      documentation:
        'PRACTICE MODE — educational review only.\n\nMethod: PUT\nPath: /api/practice/clearances/drill-desk',
      expectedMethod: 'PUT',
      pathTemplate: '/api/practice/clearances/{clearanceId}',
      pathParameters: { clearanceId: 'drill-desk' },
      queryParameters: {},
    },
    conceptVariants: [],
  },
  'm1-recruit-l6': {
    mission: {
      missionId: 'practice-m1-recruit-l6',
      module: 1,
      rank: 'RECRUIT',
      level: 6,
      title: 'Practice: badge deactivation drill',
      status: 'PRACTICE',
      playable: true,
      primaryChallengeId: 'chal-practice-m1-l6',
      requiredLessonIds: [],
      conceptTags: ['http.method.delete', 'http.endpoint', 'http.path_parameter'],
      scenarioFocus: 'Ungraded practice drill',
    },
    challenge: {
      challengeId: 'chal-practice-m1-l6',
      variantId: 'practice-badge-drill-v1',
      deviceId: 'practice-badge',
      conceptTags: ['http.method.delete', 'http.endpoint', 'http.path_parameter'],
      documentation:
        'PRACTICE MODE — educational review only.\n\nMethod: DELETE\nPath: /api/practice/access-badges/drill-pass',
      expectedMethod: 'DELETE',
      pathTemplate: '/api/practice/access-badges/{badgeId}',
      pathParameters: { badgeId: 'drill-pass' },
      queryParameters: {},
    },
    conceptVariants: [],
  },
  'm1-recruit-l7': {
    mission: {
      missionId: 'practice-m1-recruit-l7',
      module: 1,
      rank: 'RECRUIT',
      level: 7,
      title: 'Practice: nexus status drill',
      status: 'PRACTICE',
      playable: true,
      primaryChallengeId: 'chal-practice-m1-l7',
      requiredLessonIds: [],
      conceptTags: ['http.method.get', 'http.endpoint'],
      scenarioFocus: 'Ungraded practice drill',
    },
    challenge: {
      challengeId: 'chal-practice-m1-l7',
      variantId: 'practice-nexus-drill-v1',
      deviceId: 'practice-nexus',
      conceptTags: ['http.method.get', 'http.endpoint'],
      documentation:
        'PRACTICE MODE — educational review only.\n\nMethod: GET\nPath: /api/practice/nexus/status-drill',
      expectedMethod: 'GET',
      pathTemplate: '/api/practice/nexus/status-drill',
      pathParameters: {},
      queryParameters: {},
    },
    conceptVariants: [],
  },
}

export const DRILL_LABELS: Record<string, string> = {
  'm1-recruit-l1': 'Door resource drill',
  'm1-recruit-l2': 'Path parameter lock drill',
  'm1-recruit-l3': 'Query parameter sensor drill',
  'm1-recruit-l4': 'POST registration drill',
  'm1-recruit-l7': 'Command nexus status drill',
  'm1-recruit-l5': 'PUT clearance drill',
  'm1-recruit-l6': 'DELETE badge drill',
}

export function hasPracticeDrill(missionId: string): boolean {
  return Object.prototype.hasOwnProperty.call(PRACTICE_BY_MISSION, missionId)
}

export function getPracticeDetail(missionId: string): MissionDetail | null {
  return PRACTICE_BY_MISSION[missionId] ?? null
}
