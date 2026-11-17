/**
 * Defense-in-depth: remove graded answer-key fields if a backend regression
 * ever includes them in mission/progress/simulate payloads.
 * Ungraded practice/training briefs are built locally and never pass through here.
 */

const ANSWER_KEY_FIELDS = [
  'expectedMethod',
  'pathTemplate',
  'pathParameters',
  'queryParameters',
] as const

function stripObjectKeys(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(stripObjectKeys)
  }
  if (value === null || typeof value !== 'object') {
    return value
  }
  const out: Record<string, unknown> = {}
  for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
    if ((ANSWER_KEY_FIELDS as readonly string[]).includes(key)) {
      continue
    }
    out[key] = stripObjectKeys(child)
  }
  return out
}

/** Strip answer-key fields from any JSON-shaped API payload. */
export function stripGradedAnswerKeys<T>(payload: T): T {
  return stripObjectKeys(payload) as T
}
