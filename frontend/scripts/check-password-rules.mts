import {
  evaluatePasswordRules,
  passwordMeetsStrength,
  passwordsMatch,
} from '../src/auth/passwordRules.ts'

const cases = [
  { name: 'too short', password: 'Ab1!', confirm: 'Ab1!', expectStrength: false, expectMatch: true },
  {
    name: 'no letter',
    password: '12345678!',
    confirm: '12345678!',
    expectStrength: false,
    expectMatch: true,
  },
  {
    name: 'no number',
    password: 'Password!',
    confirm: 'Password!',
    expectStrength: false,
    expectMatch: true,
  },
  {
    name: 'no special',
    password: 'Password1',
    confirm: 'Password1',
    expectStrength: false,
    expectMatch: true,
  },
  {
    name: 'mismatch',
    password: 'Password1!',
    confirm: 'Password2!',
    expectStrength: true,
    expectMatch: false,
  },
  {
    name: 'valid 8+',
    password: 'Password1!',
    confirm: 'Password1!',
    expectStrength: true,
    expectMatch: true,
  },
  {
    name: 'valid longer',
    password: 'CorrectHorse9!',
    confirm: 'CorrectHorse9!',
    expectStrength: true,
    expectMatch: true,
  },
] as const

let failed = 0
for (const c of cases) {
  const strength = passwordMeetsStrength(c.password)
  const match = passwordsMatch(c.password, c.confirm)
  const ok = strength === c.expectStrength && match === c.expectMatch
  if (!ok) failed += 1
  const rules = evaluatePasswordRules(c.password, c.confirm)
    .map((r) => `${r.passed ? 'Y' : 'N'}:${r.id}`)
    .join(' ')
  console.log(`${ok ? 'PASS' : 'FAIL'} ${c.name} | strength=${strength} match=${match} | ${rules}`)
}

if (failed > 0) {
  console.error(`FAILURES: ${failed}`)
  process.exit(1)
}
console.log('ALL PASSWORD CASES OK')
