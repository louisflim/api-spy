import type { SavedAttempt } from '../types/api'

const ATTEMPT_PREFIX = 'api-spy:attempt'
const PROGRESS_PREFIX = 'api-spy:progress'
const CONCEPT_PREFIX = 'api-spy:concept'

/** Fired after mission progress is written so the Operations Board can refresh without remounting. */
export const MISSION_PROGRESS_EVENT = 'apispy:mission-progress'

export function attemptStorageKey(participantCode: string, missionId: string): string {
  return `${ATTEMPT_PREFIX}:${participantCode}:${missionId}`
}

export function progressStorageKey(participantCode: string, missionId: string): string {
  return `${PROGRESS_PREFIX}:${participantCode}:${missionId}`
}

export interface MissionProgress {
  participantCode: string
  missionId: string
  /** True when at least one mandatory device/door has been unlocked. */
  doorUnlocked: boolean
  /** Devices unlocked so far for this mission (mandatory objectives). */
  unlockedDeviceIds: string[]
  /**
   * True only when every required device for the mission has been unlocked.
   * A single door unlock must not set this if other required devices remain.
   */
  completed: boolean
  updatedAt: string
}

export type LocalConceptStage = 'INTRODUCED' | 'PRACTICING' | 'IMPROVING'

export interface MissionProgressUpdatedDetail {
  participantCode: string
  missionId: string
  completed: boolean
}

function notifyMissionProgress(detail: MissionProgressUpdatedDetail): void {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent(MISSION_PROGRESS_EVENT, { detail }))
}

/**
 * Persists only the first submitted request for a participant/mission.
 * Later corrections never rewrite this record (RCA first-submission rule).
 */
export function saveFirstAttempt(attempt: SavedAttempt): SavedAttempt {
  const key = attemptStorageKey(attempt.participantCode, attempt.missionId)
  const existing = localStorage.getItem(key)
  if (existing) {
    return JSON.parse(existing) as SavedAttempt
  }
  localStorage.setItem(key, JSON.stringify(attempt))
  return attempt
}

export function loadAttempt(
  participantCode: string,
  missionId: string,
): SavedAttempt | null {
  const raw = localStorage.getItem(attemptStorageKey(participantCode, missionId))
  if (!raw) {
    return null
  }
  return JSON.parse(raw) as SavedAttempt
}

function normalizeProgress(raw: MissionProgress): MissionProgress {
  const unlockedDeviceIds = Array.isArray(raw.unlockedDeviceIds)
    ? [...new Set(raw.unlockedDeviceIds.filter(Boolean))]
    : []
  const doorUnlocked = Boolean(raw.doorUnlocked || unlockedDeviceIds.length > 0)
  const completed =
    typeof raw.completed === 'boolean'
      ? raw.completed
      : /* Legacy single-door rows written before `completed` existed */
        doorUnlocked && unlockedDeviceIds.length <= 1

  return {
    participantCode: raw.participantCode,
    missionId: raw.missionId,
    doorUnlocked,
    unlockedDeviceIds,
    completed,
    updatedAt: raw.updatedAt,
  }
}

export function loadMissionProgress(
  participantCode: string,
  missionId: string,
): MissionProgress | null {
  const raw = localStorage.getItem(progressStorageKey(participantCode, missionId))
  if (!raw) {
    return null
  }
  return normalizeProgress(JSON.parse(raw) as MissionProgress)
}

/**
 * Records a device unlock and sets `completed` only when every required device is unlocked.
 */
export function recordDeviceUnlock(input: {
  participantCode: string
  missionId: string
  deviceId: string
  requiredDeviceIds: string[]
}): MissionProgress {
  const existing = loadMissionProgress(input.participantCode, input.missionId)
  const unlocked = new Set(existing?.unlockedDeviceIds ?? [])
  unlocked.add(input.deviceId)

  const required = [...new Set(input.requiredDeviceIds.filter(Boolean))]
  const mandatory = required.length > 0 ? required : [input.deviceId]
  const completed = mandatory.every((id) => unlocked.has(id))

  const progress: MissionProgress = {
    participantCode: input.participantCode,
    missionId: input.missionId,
    doorUnlocked: unlocked.size > 0,
    unlockedDeviceIds: [...unlocked],
    completed,
    updatedAt: new Date().toISOString(),
  }

  localStorage.setItem(
    progressStorageKey(input.participantCode, input.missionId),
    JSON.stringify(progress),
  )
  notifyMissionProgress({
    participantCode: input.participantCode,
    missionId: input.missionId,
    completed: progress.completed,
  })
  return progress
}

/** @deprecated Prefer recordDeviceUnlock so multi-device missions cannot complete early. */
export function saveMissionProgress(progress: MissionProgress): void {
  const normalized = normalizeProgress(progress)
  localStorage.setItem(
    progressStorageKey(normalized.participantCode, normalized.missionId),
    JSON.stringify(normalized),
  )
  notifyMissionProgress({
    participantCode: normalized.participantCode,
    missionId: normalized.missionId,
    completed: normalized.completed,
  })
}

export function isMissionCompleted(participantCode: string, missionId: string): boolean {
  const progress = loadMissionProgress(participantCode, missionId)
  return progress?.completed === true
}

export function isMissionInProgress(participantCode: string, missionId: string): boolean {
  if (isMissionCompleted(participantCode, missionId)) {
    return false
  }
  const progress = loadMissionProgress(participantCode, missionId)
  const attempt = loadAttempt(participantCode, missionId)
  return Boolean(progress || attempt)
}

export function saveConceptStage(
  participantCode: string,
  conceptId: string,
  stage: LocalConceptStage,
): void {
  const key = `${CONCEPT_PREFIX}:${participantCode}:${conceptId}`
  const existingRaw = localStorage.getItem(key)
  if (existingRaw) {
    const existing = JSON.parse(existingRaw) as { stage: LocalConceptStage }
    if (stageRank(existing.stage) > stageRank(stage)) {
      return
    }
  }
  localStorage.setItem(
    key,
    JSON.stringify({
      participantCode,
      conceptId,
      stage,
      updatedAt: new Date().toISOString(),
    }),
  )
}

function stageRank(stage: LocalConceptStage): number {
  if (stage === 'INTRODUCED') return 1
  if (stage === 'PRACTICING') return 2
  return 3
}
