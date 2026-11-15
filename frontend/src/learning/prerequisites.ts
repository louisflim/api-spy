import type { ConceptProgressRow } from '../api/client'
import type { Lesson, MissionSummary } from '../types/api'

const INTRODUCED_STAGES = new Set(['INTRODUCED', 'PRACTICING', 'IMPROVING'])

/** Server concept-progress id proving a Module-1 lesson was opened via /introduce. */
export function lessonIntroductionKey(lessonId: string): string {
  return `lesson:${lessonId}`
}

export function isLessonIntroduced(lessonId: string, concepts: ConceptProgressRow[]): boolean {
  return concepts.some(
    (row) =>
      row.conceptId === lessonIntroductionKey(lessonId) && INTRODUCED_STAGES.has(row.stage),
  )
}

/**
 * Lesson IDs still required before a graded mission attempt.
 * Uses lesson-scoped introduction markers — Module 0 / practice / graded
 * PRACTICING on shared concept tags must not skip pre-mission lessons.
 */
export function missingRequiredLessons(
  mission: MissionSummary,
  lessons: Lesson[],
  concepts: ConceptProgressRow[],
): string[] {
  return (mission.requiredLessonIds ?? []).filter((lessonId) => {
    const lesson = lessons.find((item) => item.id === lessonId)
    if (!lesson) return true
    return !isLessonIntroduced(lessonId, concepts)
  })
}

/** Player-facing concept labels (hide raw taxonomy IDs). */
export function conceptLabel(tag: string): string {
  const known: Record<string, string> = {
    'http.method.get': 'GET requests',
    'http.method.post': 'POST requests',
    'http.method.put': 'PUT requests',
    'http.method.patch': 'PATCH requests',
    'http.method.delete': 'DELETE requests',
    'http.endpoint': 'API endpoints',
    'http.path_parameter': 'Path parameters',
    'http.query_parameter': 'Query parameters',
    'http.status': 'Status codes',
    'http.headers': 'Headers',
    'http.response': 'HTTP responses',
    'json.body': 'JSON bodies',
    'auth.bearer': 'Authorization tokens',
  }
  if (known[tag]) return known[tag]
  return tag
    .replace(/^http\./, '')
    .replace(/[._]/g, ' ')
    .replace(/\b\w/g, (ch) => ch.toUpperCase())
}

export function lessonTitle(lessonId: string, lessons: Lesson[]): string {
  return lessons.find((lesson) => lesson.id === lessonId)?.title ?? lessonId
}
