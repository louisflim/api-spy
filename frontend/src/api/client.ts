import type { Module0Academy, Module0Progress } from '../content/module0Types'
import { stripGradedAnswerKeys } from '../security/stripAnswerKeys'
import type {
  CatalogResponse,
  Lesson,
  MissionDetail,
  MissionProgressState,
  SimulateRequestPayload,
  SimulateResponse,
  TerminalVerification,
} from '../types/api'

export interface AuthUser {
  userId: string
  username: string
  email: string
}

function readCookie(name: string): string | null {
  const encoded = `${encodeURIComponent(name)}=`
  const parts = document.cookie.split(';')
  for (const part of parts) {
    const trimmed = part.trim()
    if (trimmed.startsWith(encoded)) {
      return decodeURIComponent(trimmed.slice(encoded.length))
    }
  }
  return null
}

async function ensureCsrf(): Promise<string | null> {
  const existing = readCookie('XSRF-TOKEN')
  if (existing) {
    return existing
  }
  await fetch('/api/auth/csrf', { credentials: 'include' })
  return readCookie('XSRF-TOKEN')
}

async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json')
  }
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    const csrf = await ensureCsrf()
    if (csrf) {
      headers.set('X-XSRF-TOKEN', csrf)
    }
  }
  return fetch(input, {
    ...init,
    headers,
    credentials: 'include',
  })
}

async function readError(response: Response, fallback: string): Promise<string> {
  try {
    const data = (await response.json()) as { message?: string; error?: string }
    return data.message || data.error || `${fallback}: ${response.status}`
  } catch {
    return `${fallback}: ${response.status}`
  }
}

export async function fetchHealth(): Promise<{ status: string }> {
  const response = await fetch('/api/health')
  if (!response.ok) {
    throw new Error(`Health check failed: ${response.status}`)
  }
  return response.json()
}

export async function fetchCsrf(): Promise<void> {
  await ensureCsrf()
}

export async function registerAccount(input: {
  username: string
  email: string
  password: string
  confirmPassword: string
}): Promise<AuthUser> {
  const response = await apiFetch('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Registration failed'))
  }
  return response.json()
}

export async function loginAccount(input: {
  email: string
  password: string
}): Promise<AuthUser> {
  const response = await apiFetch('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Login failed'))
  }
  return response.json()
}

export async function logoutAccount(): Promise<void> {
  const response = await apiFetch('/api/auth/logout', { method: 'POST' })
  if (!response.ok) {
    throw new Error(await readError(response, 'Logout failed'))
  }
}

export async function fetchMe(): Promise<AuthUser> {
  const response = await apiFetch('/api/me')
  if (!response.ok) {
    throw new Error(await readError(response, 'Session restore failed'))
  }
  return response.json()
}

export async function fetchPasswordResetStatus(): Promise<{
  available: boolean
  message: string
}> {
  const response = await apiFetch('/api/auth/password-reset/status')
  if (!response.ok) {
    throw new Error(await readError(response, 'Reset status failed'))
  }
  return response.json()
}

export async function requestPasswordReset(email: string): Promise<{ message: string }> {
  const response = await apiFetch('/api/auth/password-reset/request', {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Password reset failed'))
  }
  return response.json()
}

export async function fetchLessons(module = 1): Promise<Lesson[]> {
  const response = await apiFetch(`/api/lessons?module=${module}`)
  if (!response.ok) {
    throw new Error(`Lessons failed: ${response.status}`)
  }
  return response.json()
}

export async function fetchLesson(lessonId: string): Promise<Lesson> {
  const response = await apiFetch(`/api/lessons/${encodeURIComponent(lessonId)}`)
  if (!response.ok) {
    throw new Error(`Lesson failed: ${response.status}`)
  }
  return response.json()
}

export async function introduceLesson(lessonId: string): Promise<void> {
  const response = await apiFetch(`/api/lessons/${encodeURIComponent(lessonId)}/introduce`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw new Error(`Introduce lesson failed: ${response.status}`)
  }
}

export interface ConceptProgressRow {
  conceptId: string
  stage: 'INTRODUCED' | 'PRACTICING' | 'IMPROVING'
  updatedAt: string
}

export async function fetchMyConcepts(): Promise<ConceptProgressRow[]> {
  const response = await apiFetch('/api/me/concepts')
  if (!response.ok) {
    throw new Error(`Concept progress failed: ${response.status}`)
  }
  return response.json()
}

export async function fetchCatalog(): Promise<CatalogResponse> {
  const response = await apiFetch('/api/catalog')
  if (!response.ok) {
    throw new Error(`Catalog failed: ${response.status}`)
  }
  return response.json()
}

export async function fetchMissionDetail(
  module: number,
  rank: string,
  level: number,
): Promise<MissionDetail> {
  const response = await apiFetch(`/api/missions/${module}/${rank}/${level}`)
  if (!response.ok) {
    throw new Error(await readError(response, 'Mission detail failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

export async function simulateRequest(
  payload: SimulateRequestPayload,
): Promise<SimulateResponse> {
  const response = await apiFetch('/api/requests/simulate', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Simulate failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

export async function fetchAllMissionProgress(): Promise<MissionProgressState[]> {
  const response = await apiFetch('/api/missions/progress')
  if (!response.ok) {
    throw new Error(await readError(response, 'Mission progress list failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

export async function fetchTerminalVerification(missionId: string): Promise<TerminalVerification> {
  const response = await apiFetch(`/api/missions/${encodeURIComponent(missionId)}/terminal-verification`)
  if (!response.ok) {
    throw new Error(await readError(response, 'Terminal verification failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

export async function fetchMissionProgress(missionId: string): Promise<MissionProgressState> {
  const response = await apiFetch(`/api/missions/${missionId}/progress`)
  if (!response.ok) {
    throw new Error(await readError(response, 'Mission progress failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

export async function resetMissionRoom(
  missionId: string,
  reason: 'API_STALLED' | 'COMBAT' | 'PLAYER',
  roomGeneration?: string,
): Promise<MissionProgressState> {
  const response = await apiFetch(`/api/missions/${missionId}/reset`, {
    method: 'POST',
    body: JSON.stringify({
      reason,
      ...(roomGeneration ? { roomGeneration } : {}),
    }),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Mission room reset failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

/** Clears the SRS v1.3 forced ungraded-lesson gate after revisiting the required lesson. */
export async function acknowledgeLessonReview(
  missionId: string,
  lessonId: string,
): Promise<MissionProgressState> {
  const response = await apiFetch(`/api/missions/${missionId}/acknowledge-lesson-review`, {
    method: 'POST',
    body: JSON.stringify({ lessonId }),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Lesson review acknowledgement failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

/**
 * Report a combat death bound to the active roomGeneration.
 * Authorizes COMBAT reset; does not send client health as proof.
 */
/** Level 7: records the boss defeat for the active room generation (rejected while the shield is up). */
export async function reportBossDefeated(
  missionId: string,
  roomGeneration: string,
): Promise<MissionProgressState> {
  const response = await apiFetch(`/api/missions/${missionId}/boss-defeated`, {
    method: 'POST',
    body: JSON.stringify({ roomGeneration }),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Boss defeat report failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

export async function reportCombatDeath(
  missionId: string,
  roomGeneration: string,
): Promise<MissionProgressState> {
  const response = await apiFetch(`/api/missions/${missionId}/combat-death`, {
    method: 'POST',
    body: JSON.stringify({ roomGeneration }),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Combat death report failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

export async function completeMissionAtExit(
  missionId: string,
  roomGeneration: string,
): Promise<MissionProgressState> {
  const response = await apiFetch(`/api/missions/${missionId}/complete`, {
    method: 'POST',
    body: JSON.stringify({
      roomGeneration,
      // Client gameplay signal only — server does not treat this as position proof.
      exitInteracted: true,
    }),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Mission completion failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

/** Server-authorized temporary replay run for a career-cleared mission. */
export async function startMissionReplay(missionId: string): Promise<MissionProgressState> {
  const response = await apiFetch(`/api/missions/${missionId}/replay`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Mission replay failed'))
  }
  return stripGradedAnswerKeys(await response.json())
}

export async function fetchModule0Academy(): Promise<Module0Academy> {
  const response = await apiFetch('/api/module0')
  if (!response.ok) {
    throw new Error(`Module 0 academy failed: ${response.status}`)
  }
  return response.json()
}

export async function fetchModule0Progress(): Promise<Module0Progress> {
  const response = await apiFetch('/api/module0/progress')
  if (!response.ok) {
    throw new Error(`Module 0 progress failed: ${response.status}`)
  }
  return response.json()
}

export async function updateModule0Progress(input: {
  topicId: string
  stepId?: string
  markTopicComplete?: boolean
  lastTopicId?: string
}): Promise<Module0Progress> {
  const response = await apiFetch('/api/module0/progress', {
    method: 'PUT',
    body: JSON.stringify(input),
  })
  if (!response.ok) {
    throw new Error(await readError(response, 'Module 0 progress update failed'))
  }
  return response.json()
}
