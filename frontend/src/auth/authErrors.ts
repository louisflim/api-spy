/**
 * Auth-facing error copy. Login stays deliberately vague (no account enumeration).
 * Raw backend messages are never shown — see {@link publicErrorMessage}.
 */

export {
  loginErrorMessage,
  registerErrorMessage,
  resetErrorMessage,
  publicErrorMessage,
} from '../security/publicErrors'

export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
}
