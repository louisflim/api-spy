import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { isValidEmail, resetErrorMessage } from '../auth/authErrors'
import { SpyBrand } from '../ui/spy/SpyBrand'
import { SpyPanel } from '../ui/spy/SpyPanel'
import { SpyStage } from '../ui/spy/SpyStage'

interface ForgotPasswordScreenProps {
  onBack: () => void
}

export function ForgotPasswordScreen({ onBack }: ForgotPasswordScreenProps) {
  const { requestPasswordReset, passwordResetAvailable, passwordResetMessage } = useAuth()
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setInfo(null)

    if (!passwordResetAvailable) {
      setInfo(passwordResetMessage)
      return
    }

    if (!email.trim()) {
      setError('Email is required.')
      return
    }
    if (!isValidEmail(email)) {
      setError('Enter a valid email address.')
      return
    }

    setBusy(true)
    try {
      await requestPasswordReset(email.trim())
      setInfo(
        'If an account exists for that email and recovery is available, further instructions would be sent.',
      )
    } catch (err) {
      setError(resetErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <SpyStage variant="terminal" scene="auth">
      <SpyPanel variant="crt" delayMs={160} stamp="RECOVERY">
        <SpyBrand eyebrow="Recovery desk // encrypted" subtitle="Restore Access" />
        <p className="spy-lede spy-crt-cursor">
          {passwordResetAvailable
            ? 'Secure terminal online. Enter your email to begin credential recovery.'
            : passwordResetMessage}
        </p>

        <form className="auth-form" onSubmit={(e) => void handleSubmit(e)} noValidate>
          <label>
            Email
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={busy || !passwordResetAvailable}
            />
          </label>
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
            disabled={busy || !passwordResetAvailable}
          >
            {busy ? 'Sending…' : 'Send Reset Link'}
            {!busy && <span className="spy-btn__chevrons" aria-hidden="true">{'>>'}</span>}
          </button>
        </form>

        <p className="auth-footer">
          <button type="button" className="linkish" onClick={onBack} disabled={busy}>
            Back to Sign-In
          </button>
        </p>
      </SpyPanel>
    </SpyStage>
  )
}
