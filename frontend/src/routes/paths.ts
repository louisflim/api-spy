/** Canonical SPA paths — single source for navigation and redirects. */

export type PracticeFrom = 'ops' | 'briefing' | 'training'

export const paths = {
  root: '/',
  login: '/login',
  register: '/register',
  forgotPassword: '/forgot-password',
  dashboard: '/dashboard',
  operations: '/operations',
  trainingAcademy: '/training-academy',
  /** Module 0 introductory Academy with optional return path (not mission level training). */
  trainingAcademyFrom: (returnPath: string) =>
    `/training-academy?returnTo=${encodeURIComponent(returnPath)}`,
  profile: '/profile',
  loadout: '/loadout',
  research: '/research',
  settings: '/settings',
  missionBriefing: (missionId: string) =>
    `/missions/${encodeURIComponent(missionId)}/briefing`,
  missionTraining: (missionId: string) =>
    `/missions/${encodeURIComponent(missionId)}/training`,
  missionPractice: (missionId: string, from: PracticeFrom = 'ops') =>
    `/missions/${encodeURIComponent(missionId)}/practice?from=${from}`,
  missionField: (missionId: string) =>
    `/missions/${encodeURIComponent(missionId)}/field`,
  operationsModule: (module: number) => `/operations?module=${module}`,
} as const

export function parsePracticeFrom(raw: string | null): PracticeFrom {
  if (raw === 'briefing' || raw === 'training' || raw === 'ops') return raw
  return 'ops'
}

/** Same-origin SPA path only — rejects protocol-relative and external URLs. */
export function safeInternalPath(raw: string | null | undefined): string | null {
  if (!raw) return null
  let decoded = raw
  try {
    decoded = decodeURIComponent(raw)
  } catch {
    return null
  }
  if (!decoded.startsWith('/') || decoded.startsWith('//') || decoded.includes('://')) {
    return null
  }
  return decoded
}

/** Map legacy `/app/...` bookmarks from the pre-router history sync. */
export function legacyAppRedirect(pathname: string): string | null {
  if (!pathname.startsWith('/app')) return null
  const rest = pathname.slice('/app'.length) || '/'
  if (rest === '/' || rest === '/dashboard') return paths.dashboard
  if (rest === '/operations') return paths.operations
  if (rest === '/profile') return paths.profile
  if (rest === '/loadout') return paths.loadout
  if (rest === '/settings') return paths.settings
  if (rest === '/training' || rest.startsWith('/training/')) {
    const id = rest.slice('/training/'.length)
    return id ? paths.missionTraining(decodeURIComponent(id)) : paths.operations
  }
  if (rest.startsWith('/briefing/')) {
    const id = rest.slice('/briefing/'.length)
    return id ? paths.missionBriefing(decodeURIComponent(id)) : paths.operations
  }
  if (rest.startsWith('/practice/')) {
    const id = rest.slice('/practice/'.length)
    return id ? paths.missionPractice(decodeURIComponent(id), 'ops') : paths.operations
  }
  if (rest.startsWith('/field/')) {
    const id = rest.slice('/field/'.length)
    return id ? paths.missionField(decodeURIComponent(id)) : paths.operations
  }
  return paths.dashboard
}
