import { useMemo, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { isValidEmail, registerErrorMessage } from '../auth/authErrors'
import {
  evaluatePasswordRules,
  passwordMeetsStrength,
  passwordsMatch,
} from '../auth/passwordRules'
import { SpyBrand } from '../ui/spy/SpyBrand'
import { SpyPanel } from '../ui/spy/SpyPanel'
import { SpyStage } from '../ui/spy/SpyStage'

interface RegisterScreenProps {
  onGoLogin: () => void
}

export function RegisterScreen({ onGoLogin }: RegisterScreenProps) {
  const { signUp } = useAuth()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)

  const ruleStatus = useMemo(
    () => evaluatePasswordRules(password, confirmPassword),
    [password, confirmPassword],
  )

  const passwordReady =
    passwordMeetsStrength(password) && passwordsMatch(password, confirmPassword)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setInfo(null)

    if (!username.trim() || !email.trim() || !password || !confirmPassword) {
      setError('All fields are required.')
      return
    }
    if (!isValidEmail(email)) {
      setError('Enter a valid email address.')
      return
    }
    if (!passwordMeetsStrength(password)) {
      setError('Password does not meet the requirements below.')
      return
    }
    if (!passwordsMatch(password, confirmPassword)) {
      setError('Confirm password must match password.')
      return
    }
    setBusy(true)
    try {
      await signUp({
        username: username.trim(),
        email: email.trim(),
        password,
        confirmPassword,
      })
      setInfo('Clearance granted. Welcome to headquarters.')
    } catch (err) {
      setError(registerErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <SpyStage variant="dossier" scene="auth">
      <SpyPanel variant="dossier" delayMs={200} stamp="INTAKE">
        <SpyBrand eyebrow="Recruitment // field office" subtitle="Request Clearance" />
        <p className="spy-lede">
          Open your agent dossier with a codename, email, and a strong passphrase to join
          headquarters.
        </p>

        <form className="auth-form" onSubmit={(e) => void handleSubmit(e)} noValidate>
          <label>
            Codename
            <input
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={busy}
              placeholder="agent codename"
            />
          </label>
          <label>
            Email
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={busy}
              placeholder="you@gmail.com"
            />
          </label>

          <label>
            Password
            <div className="spy-password-field">
              <input
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={busy}
                aria-describedby="password-requirements"
              />
              <button
                type="button"
                className="spy-password-toggle"
                onClick={() => setShowPassword((value) => !value)}
                disabled={busy}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </label>

          <label>
            Confirm password
            <div className="spy-password-field">
              <input
                type={showConfirmPassword ? 'text' : 'password'}
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                disabled={busy}
              />
              <button
                type="button"
                className="spy-password-toggle"
                onClick={() => setShowConfirmPassword((value) => !value)}
                disabled={busy}
                aria-label={showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'}
              >
                {showConfirmPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </label>

          <ul id="password-requirements" className="spy-password-rules" aria-live="polite">
            {ruleStatus.map((rule) => (
              <li
                key={rule.id}
                className={rule.passed ? 'spy-password-rules__item is-met' : 'spy-password-rules__item'}
              >
                <span aria-hidden="true">{rule.passed ? '✓' : '○'}</span>
                {rule.label}
              </li>
            ))}
          </ul>

          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          {info && (
            <p className="auth-info" role="status">
              {info}
            </p>
          )}

          <button
            type="submit"
            className={busy ? 'is-loading' : ''}
            disabled={busy || !passwordReady}
          >
            {busy ? 'Registering…' : 'Create Agent Profile'}
            {!busy && <span className="spy-btn__chevrons" aria-hidden="true">{'>>'}</span>}
          </button>
        </form>

        <p className="auth-footer">
          Already cleared?{' '}
          <button type="button" className="linkish" onClick={onGoLogin} disabled={busy}>
            Sign In
          </button>
        </p>

        <div className="spy-panel__seal" aria-hidden="true">
          <img src="/assets/brand/diamond-mark.svg" alt="" width={42} height={42} />
        </div>
      </SpyPanel>
    </SpyStage>
  )
}
