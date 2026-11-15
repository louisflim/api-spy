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
  profile: '/profile',
  loadout: '/loadout',
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
