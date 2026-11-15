import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { isValidEmail, loginErrorMessage } from '../auth/authErrors'
import { SpyBrand } from '../ui/spy/SpyBrand'
import { SpyPanel } from '../ui/spy/SpyPanel'
import { SpyStage } from '../ui/spy/SpyStage'

interface LoginScreenProps {
  onGoRegister: () => void
  onForgotPassword: () => void
}

export function LoginScreen({ onGoRegister, onForgotPassword }: LoginScreenProps) {
  const { signIn } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (!email.trim() || !password) {
      setError('Email and password are required.')
      return
    }
    if (!isValidEmail(email)) {
      setError('Enter a valid email address.')
      return
    }

    setBusy(true)
    try {
      await signIn(email.trim(), password)
    } catch (err) {
      setError(loginErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <SpyStage variant="hq" scene="auth">
      <SpyPanel variant="console" delayMs={180}>
        <SpyBrand eyebrow="Secure uplink // clearance channel" subtitle="Agent Sign-In" />
        <p className="spy-lede">
          Welcome back, Agent. Authenticate to enter intelligence headquarters.
        </p>

        <form className="auth-form" onSubmit={(e) => void handleSubmit(e)} noValidate>
          <label>
            Email
            <div className="spy-field">
              <svg className="spy-field__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" strokeWidth="1.6" />
                <path d="M4 7l8 6 8-6" stroke="currentColor" strokeWidth="1.6" />
              </svg>
              <input
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={busy}
                placeholder="you@gmail.com"
              />
            </div>
          </label>
          <label>
            Password
            <div className="spy-password-field">
              <div className="spy-field">
                <svg className="spy-field__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <rect x="5" y="10" width="14" height="10" rx="2" stroke="currentColor" strokeWidth="1.6" />
                  <path d="M8 10V8a4 4 0 018 0v2" stroke="currentColor" strokeWidth="1.6" />
                </svg>
                <input
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={busy}
                />
              </div>
              <button
                type="button"
                className="spy-password-toggle"
                onClick={() => setShowPassword((value) => !value)}
                disabled={busy}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                aria-pressed={showPassword}
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
          </label>

          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}

          <button type="submit" className={busy ? 'is-loading' : ''} disabled={busy}>
            {busy ? 'Authenticating…' : 'Enter Headquarters'}
            {!busy && <span className="spy-btn__chevrons" aria-hidden="true">{'>>'}</span>}
          </button>
        </form>

        <div className="auth-footer-row">
          <button type="button" className="linkish" onClick={onForgotPassword} disabled={busy}>
            Forgot Password
          </button>
          <span className="sep" aria-hidden="true">
            |
          </span>
          <button type="button" className="linkish" onClick={onGoRegister} disabled={busy}>
            Request Clearance
          </button>
        </div>

        <div className="spy-panel__seal" aria-hidden="true">
          <img src="/assets/brand/diamond-mark.svg" alt="" width={42} height={42} />
        </div>
      </SpyPanel>
    </SpyStage>
  )
}
