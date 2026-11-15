/**
 * Maps Supabase / validation errors to user-facing copy.
 * Login failures stay deliberately vague (no email enumeration).
 */

export function loginErrorMessage(_error: unknown): string {
  return 'Invalid email or password.'
}

export function registerErrorMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error)
  const lower = message.toLowerCase()

  if (lower.includes('already registered') || lower.includes('user already')) {
    return 'An account with this email already exists. Try logging in instead.'
  }
  if (lower.includes('password')) {
    return message
  }
  if (lower.includes('email')) {
    return message
  }
  if (lower.includes('rate') || lower.includes('limit') || lower.includes('too many')) {
    return 'Too many attempts. Please wait a moment and try again.'
  }
  return message || 'Registration failed. Please try again.'
}

export function resetErrorMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error)
  if (!message) {
    return 'Unable to send reset email. Please try again.'
  }
  return message
}

export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
}
