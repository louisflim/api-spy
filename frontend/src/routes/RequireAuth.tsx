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

/** Blocks authenticated routes until session bootstrap finishes. */
export function RequireAuth() {
  const { loading, profile } = useAuth()
  const location = useLocation()

  if (loading) return <SessionRestoring />
  if (!profile) {
    return <Navigate to={paths.login} replace state={{ from: location.pathname + location.search }} />
  }
  return <Outlet />
}
