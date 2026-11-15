export interface PasswordRule {
  id: string
  label: string
  test: (password: string, confirmPassword?: string) => boolean
}

/** Special characters accepted for registration strength checks. */
const SPECIAL_CHAR = /[^A-Za-z0-9]/

export const PASSWORD_RULES: PasswordRule[] = [
  {
    id: 'min-length',
    label: 'At least 8 characters',
    test: (password) => password.length >= 8,
  },
  {
    id: 'letter',
    label: 'At least one letter (A–Z or a–z)',
    test: (password) => /[A-Za-z]/.test(password),
  },
  {
    id: 'number',
    label: 'At least one number (0–9)',
    test: (password) => /[0-9]/.test(password),
  },
  {
    id: 'special',
    label: 'At least one special character (e.g. !@#$%)',
    test: (password) => SPECIAL_CHAR.test(password),
  },
]

export function passwordMeetsStrength(password: string): boolean {
  return PASSWORD_RULES.every((rule) => rule.test(password))
}

export function passwordsMatch(password: string, confirmPassword: string): boolean {
  return confirmPassword.length > 0 && password === confirmPassword
}

export function evaluatePasswordRules(
  password: string,
  confirmPassword = '',
): { id: string; label: string; passed: boolean }[] {
  const strength = PASSWORD_RULES.map((rule) => ({
    id: rule.id,
    label: rule.label,
    passed: rule.test(password),
  }))
  return [
    ...strength,
    {
      id: 'match',
      label: 'Confirm password matches',
      passed: passwordsMatch(password, confirmPassword),
    },
  ]
}
