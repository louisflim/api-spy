export type SimulationMode = 'MISSION' | 'PRACTICE' | 'TRAINING'

export interface SimulateRequestPayload {
  participantCode: string
  missionId: string
  deviceId: string
  challengeId?: string
  method: string
  path: string
  headers: Record<string, string>
  body: string
  /** Defaults to MISSION on the server when omitted. */
  mode?: SimulationMode
}

export interface SimulatedHttpResponse {
  status: number
  headers: Record<string, string>
  body: unknown
}

export interface DeviceStateChange {
  deviceId: string
  state: string
  unlocked: boolean
}

export interface FirstAttemptRecord {
  participantCode: string
  missionId: string
  deviceId: string
  challengeId: string
  variantId: string
  conceptTags: string[]
  method: string
  path: string
  headers: Record<string, string>
  body: string
  errorCodes: string[]
  firstAttemptCorrect: boolean
  conceptFirstAttemptCorrect: Record<string, boolean>
  submittedAt: string
}

export interface ControlFeedback {
  tone: string
  messages: string[]
  conceptTags: string[]
}

export interface VerifyAnotherScenario {
  challengeId: string
  variantId: string
  deviceId: string
  documentation: string
}

export interface TerminalVerification {
  percent: number
  earnedSlots: number
  requiredSlots: number
  terminalId: string
  complete: boolean
  verifyAnotherScenario: VerifyAnotherScenario | null
}

export interface AdaptivePracticeDoor {
  conceptTag: string
  challengeId: string
  deviceId: string
  label: string
  documentation: string
  /** Retired physical practice doors — answer keys must never appear on graded APIs. */
  expectedMethod?: string
  pathTemplate?: string
  practiceMissionId: string
  /** Authored mini-lesson for targeted re-teaching (optional support). */
  reviewLessonId?: string | null
  /** Remaining authored ungraded variants for voluntary further rounds. */
  alternatePracticeChallengeIds?: string[]
  /** True when this response newly unlocked targeted support. */
  newlyTriggered?: boolean
}

export interface ConceptReviewItem {
  conceptTag: string
  sourceMissionId: string
  status: string
  reviewChallengeId: string | null
}

export interface LearningSupportOffer {
  conceptTag: string
  reviewLessonId: string | null
  lessonOpened: boolean
  recurringDifficultyActive: boolean
  activePracticeChallengeId: string
  remainingPracticeChallengeIds: string[]
  roomMissionId: string
}

export interface LearningSupportRecovery {
  roomMissionId: string
  supportOffers: LearningSupportOffer[]
  adaptivePracticeDoors: AdaptivePracticeDoor[]
  pendingLaterReview: ConceptReviewItem[]
  practiceOutcomes: Array<{
    conceptTag: string
    challengeId: string
    roomMissionId: string | null
    correct: boolean
    recordedAt: string
  }>
}

export interface SimulateResponse {
  correct: boolean
  conceptTag: string
  conceptTags: string[]
  challengeId: string
  variantId: string
  errorCodes: string[]
  simulatedResponse: SimulatedHttpResponse
  deviceChanges: DeviceStateChange[]
  heartsChanged: boolean
  firstAttemptRecord: FirstAttemptRecord
  firstAttemptRecorded: boolean
  controlFeedback?: ControlFeedback | null
  terminalVerification?: TerminalVerification | null
  /** Always empty — physical adaptive practice doors are retired. */
  adaptivePracticeDoors?: AdaptivePracticeDoor[]
  /** Always null — automatic practice offers are retired. */
  autoPracticeOffer?: AdaptivePracticeDoor | null
  optionalVerification?: boolean
  conceptReviewQueue?: ConceptReviewItem[]
  /** Open consistent-improvement observation cycles (not TVP / mastery claims). */
  conceptConsistency?: ConceptConsistencyObservation[]
  missionProgress?: MissionProgressState | null
}

export interface ConceptConsistencyObservation {
  conceptTag: string
  status: string
  consecutiveSuccesses: number
  requiredConsecutiveSuccesses: number
  scenariosUsed: number
  maxAdditionalScenarios: number
  sourceChallengeId: string
  plannedChallengeIds: string[]
}

export interface MandatoryDeviceState {
  deviceId: string
  challengeId: string
  documentation: string
  unlocked: boolean
  finalLock: boolean
}

export interface MissionProgressState {
  missionId: string
  module: number
  rank: string
  status: string
  apiResetCount: number
  /** Opaque id for the active room attempt; required on complete. */
  roomGeneration: string
  requiredDeviceCount: number
  finalLockRequired: boolean
  requiredDeviceIds: string[]
  unlockedDeviceIds: string[]
  exitReady: boolean
  /** Permanent career clear — preserved across replays. */
  completed: boolean
  completedAt: string | null
  updatedAt: string
  gradedAttemptCountSinceReset: number
  combatRestartAuthorized: boolean
  activeRunKind: 'PRIMARY' | 'REPLAY' | 'NONE' | string
  replayActive: boolean
  replayable: boolean
  replayCount: number
  /** Consecutive unresolved API-driven room resets (not wrong submissions). */
  consecutiveUnresolvedApiResets: number
  /** SRS v1.3 gate after 15 consecutive unresolved API-driven room attempts. */
  lessonReviewRequired: boolean
  requiredReviewLessonId: string | null
  /** Server hint: manual Restart Mission would escalate the API device tier. */
  manualRestartWouldEscalate: boolean
  /** Mandatory device count after the next escalating restart (capped at 5). */
  nextRequiredDeviceCount: number
  mandatoryDevices: MandatoryDeviceState[]
  /** Always empty — physical adaptive practice doors are retired. */
  adaptivePracticeDoors?: AdaptivePracticeDoor[]
  /** Server-selected challenge currently active in the terminal verification sequence. */
  activeTerminalChallenge?: ActiveTerminalChallenge | null
}

export interface ActiveTerminalChallenge {
  challengeId: string
  variantId: string
  deviceId: string
  documentation: string
  optionalVerification: boolean
  sequenceComplete: boolean
}

export interface SavedAttempt {
  participantCode: string
  missionId: string
  deviceId: string
  challengeId?: string
  variantId?: string
  conceptTags?: string[]
  method: string
  path: string
  headers?: Record<string, string>
  body?: string
  errorCodes?: string[]
  firstAttemptCorrect?: boolean
  /** Day 1 compatibility field */
  correct?: boolean
  conceptFirstAttemptCorrect?: Record<string, boolean>
  submittedAt: string
  simulatedStatus: number
  doorUnlocked: boolean
}

export interface Lesson {
  id: string
  module: number
  order: number
  title: string
  conceptTags: string[]
  explanation: string
  workedExample: {
    title: string
    notes: string
    method: string
    path: string
    responseStatus: number
    responseBody: unknown
  }
  practiceScenario: {
    summary: string
    linkedMissionId: string
    ungraded: boolean
  }
  graded: boolean
}

export interface MissionSummary {
  missionId: string
  module: number
  rank: string
  level: number
  title: string
  status: string
  playable: boolean
  primaryChallengeId: string | null
  requiredLessonIds: string[]
  conceptTags: string[]
  scenarioFocus: string
}

export interface CatalogResponse {
  totalMissions: number
  implementedMissions: number
  plannedMissions: number
  missions: MissionSummary[]
}

export interface MissionDetail {
  mission: MissionSummary
  challenge: {
    challengeId: string
    variantId: string
    deviceId: string
    conceptTags: string[]
    documentation: string
    /** Present only for local ungraded practice/training briefs — never from graded APIs. */
    expectedMethod?: string
    pathTemplate?: string
    pathParameters?: Record<string, string>
    queryParameters?: Record<string, string>
  }
  conceptVariants: Array<{
    challengeId: string
    variantId: string
    deviceId: string
    conceptTags: string[]
    documentation: string
  }>
  conceptReviewQueue?: ConceptReviewItem[]
}
