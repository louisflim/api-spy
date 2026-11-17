/**
 * Maps thrown errors to safe, user-facing copy.
 * Never forward raw stack traces or unexpected backend internals.
 */

const SAFE_AUTH_FRAGMENTS: Array<{ match: RegExp; message: string }> = [
  {
    match: /already taken|username is already/i,
    message: 'That username is already taken. Try another.',
  },
  {
    match: /already registered|already exists|user already/i,
    message: 'An account with this email already exists. Try logging in instead.',
  },
  {
    match: /password must|confirm password|at least 8|special character|one letter|one number/i,
    message: 'Password does not meet the requirements.',
  },
  {
    match: /invalid request payload|malformed request/i,
    message: 'Some fields look invalid. Check your input and try again.',
  },
  {
    match: /too many|rate|limit/i,
    message: 'Too many attempts. Please wait a moment and try again.',
  },
  {
    match: /payload too large/i,
    message: 'That request was too large. Please try again.',
  },
]

export function publicErrorMessage(error: unknown, fallback: string): string {
  const raw = error instanceof Error ? error.message : String(error ?? '')
  if (!raw) {
    return fallback
  }
  for (const rule of SAFE_AUTH_FRAGMENTS) {
    if (rule.match.test(raw)) {
      return rule.message
    }
  }
  // Status-only messages from the client are fine to show.
  if (/^[A-Za-z0-9 .:_-]+:\s*\d{3}$/.test(raw.trim())) {
    return fallback
  }
  return fallback
}

export function loginErrorMessage(_error: unknown): string {
  return 'Invalid email or password.'
}

export function registerErrorMessage(error: unknown): string {
  return publicErrorMessage(error, 'Registration failed. Please try again.')
}

export function resetErrorMessage(_error: unknown): string {
  return 'If that email is registered, reset instructions will be available when mail is enabled.'
}
