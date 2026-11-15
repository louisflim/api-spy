import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { paths } from './paths'

function SessionRestoring() {
  return (
    <div className="spy-stage spy-stage--hq">
      <div className="spy-stage__scenery" aria-hidden="true">
        <img className="spy-stage__plate" src="/assets/scenes/hq-auth-night.jpg?v=3" alt="" />
        <div className="spy-stage__vignette" />
      </div>
      <div className="spy-stage__content">
        <div className="auth-panel">
          <p className="auth-eyebrow">RESTORING SESSION</p>
          <h1>API Spy</h1>
          <p className="auth-sub">Checking secure session…</p>
        </div>
      </div>
    </div>
  )
}

/** Public auth screens — redirect signed-in agents to HQ or their intended destination. */
export function GuestOnly() {
  const { loading, profile } = useAuth()
  const location = useLocation()
  const from =
    (location.state as { from?: string } | null)?.from &&
    typeof (location.state as { from?: string }).from === 'string'
      ? (location.state as { from: string }).from
      : paths.dashboard

  if (loading) return <SessionRestoring />
  if (profile) return <Navigate to={from.startsWith('/') ? from : paths.dashboard} replace />
  return <Outlet />
}
