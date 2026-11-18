import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import type { MenuDestination } from '../screens/menuTypes'
import { AuthenticatedLayout } from '../ui/layout/AuthenticatedLayout'
import type { AppNavId } from '../ui/layout/AuthenticatedNavbar'
import { paths } from './paths'
import { useUplink } from './useUplink'

function navIdForPath(pathname: string): AppNavId | null {
  if (
    pathname.startsWith('/operations') ||
    pathname.startsWith('/training-academy') ||
    pathname.startsWith('/missions/')
  ) {
    return 'select-mission'
  }
  if (pathname.startsWith('/loadout')) return 'loadout'
  if (pathname.startsWith('/research')) return 'research'
  if (pathname.startsWith('/profile') || pathname.startsWith('/settings')) return null
  return 'dashboard'
}

/**
 * Shared chrome for authenticated HQ pages. Field room routes sit outside this shell.
 */
export function AppShell() {
  const { signOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const uplink = useUplink()

  function handleNavigate(destination: MenuDestination) {
    if (destination === 'dashboard') {
      navigate(paths.dashboard)
      return
    }
    if (destination === 'select-mission') {
      navigate(paths.operations)
      return
    }
    if (destination === 'module-0') {
      navigate(paths.trainingAcademy)
      return
    }
    if (destination === 'profile') {
      navigate(paths.profile)
      return
    }
    if (destination === 'loadout') {
      navigate(paths.loadout)
      return
    }
    if (destination === 'settings') {
      navigate(paths.settings)
      return
    }
    if (destination === 'research') {
      navigate(paths.research)
      return
    }
    if (destination === 'continue') {
      navigate(paths.dashboard)
    }
  }

  async function handleLogout() {
    await signOut()
    navigate(paths.login, { replace: true })
  }

  return (
    <AuthenticatedLayout
      active={navIdForPath(location.pathname)}
      uplink={uplink}
      onNavigate={handleNavigate}
      onLogout={() => void handleLogout()}
    >
      <Outlet />
    </AuthenticatedLayout>
  )
}
