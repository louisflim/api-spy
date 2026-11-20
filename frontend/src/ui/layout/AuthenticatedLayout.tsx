import type { ReactNode } from 'react'
import { useAuth } from '../../auth/AuthContext'
import type { MenuDestination } from '../../screens/menuTypes'
import { SpyStage } from '../spy/SpyStage'
import { StarterAgentPicker } from '../loadout/StarterAgentPicker'
import { AuthenticatedNavbar, type AppNavId } from './AuthenticatedNavbar'

interface AuthenticatedLayoutProps {
  active: AppNavId | null
  uplink: 'checking' | 'up' | 'down'
  children: ReactNode
  onNavigate: (destination: MenuDestination) => void
  onLogout: () => void
}

/**
 * Shared chrome for authenticated management pages.
 * Phaser gameplay screens intentionally stay outside this layout.
 */
export function AuthenticatedLayout({
  active,
  uplink,
  children,
  onNavigate,
  onLogout,
}: AuthenticatedLayoutProps) {
  const { profile } = useAuth()
  const agentName = profile?.username ?? 'Recruit'

  return (
    <SpyStage variant="command" scene="command" wide className="spy-stage--dashboard">
      <div className="auth-shell">
        <AuthenticatedNavbar
          active={active}
          agentName={agentName}
          uplink={uplink}
          onNavigate={onNavigate}
          onLogout={onLogout}
          showResearch={Boolean(profile?.isAdministrator)}
        />
        <div className="auth-shell__body">{children}</div>
      </div>
      <StarterAgentPicker />
    </SpyStage>
  )
}
