import { useEffect, useId, useRef, useState } from 'react'
import type { MenuDestination } from '../../screens/menuTypes'

export type AppNavId =
  | 'dashboard'
  | 'select-mission'
  | 'loadout'
  | 'research'

interface AuthenticatedNavbarProps {
  /** Null when on placeholder pages (profile/settings) so no primary tab looks active. */
  active: AppNavId | null
  agentName: string
  uplink: 'checking' | 'up' | 'down'
  onNavigate: (destination: MenuDestination) => void
  onLogout: () => void
  /** Research console tab (administrators only). */
  showResearch?: boolean
}

const NAV_ITEMS: Array<{ id: AppNavId; label: string; destination: MenuDestination }> = [
  { id: 'dashboard', label: 'Dashboard', destination: 'dashboard' },
  { id: 'select-mission', label: 'Operations Board', destination: 'select-mission' },
  { id: 'loadout', label: 'Agent Loadout', destination: 'loadout' },
]

export function AuthenticatedNavbar({
  active,
  agentName,
  uplink,
  onNavigate,
  onLogout,
  showResearch = false,
}: AuthenticatedNavbarProps) {
  const navItems = showResearch
    ? [...NAV_ITEMS, { id: 'research' as const, label: 'Research', destination: 'research' as const }]
    : NAV_ITEMS
  const menuId = useId()
  const mobileNavId = useId()
  const [accountOpen, setAccountOpen] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!accountOpen) return
    function onPointerDown(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setAccountOpen(false)
      }
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setAccountOpen(false)
    }
    window.addEventListener('mousedown', onPointerDown)
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('mousedown', onPointerDown)
      window.removeEventListener('keydown', onKey)
    }
  }, [accountOpen])

  useEffect(() => {
    setMobileOpen(false)
    setAccountOpen(false)
  }, [active])

  const uplinkLabel =
    uplink === 'checking' ? 'Checking uplink…' : uplink === 'up' ? 'HQ uplink online' : 'HQ uplink down'

  function navigate(destination: MenuDestination) {
    setMobileOpen(false)
    setAccountOpen(false)
    onNavigate(destination)
  }

  return (
    <header className="dash-nav" role="banner">
      <button
        type="button"
        className="dash-nav__brand"
        onClick={() => navigate('dashboard')}
        aria-label="API Spy dashboard"
      >
        <img src="/assets/brand/diamond-mark.svg" alt="" width={22} height={22} aria-hidden="true" />
        <span>API Spy</span>
      </button>

      <button
        type="button"
        className={`dash-nav__burger ${mobileOpen ? 'is-open' : ''}`.trim()}
        aria-expanded={mobileOpen}
        aria-controls={mobileNavId}
        aria-label={mobileOpen ? 'Close navigation menu' : 'Open navigation menu'}
        onClick={() => setMobileOpen((open) => !open)}
      >
        <span />
        <span />
        <span />
      </button>

      <nav
        id={mobileNavId}
        className={`dash-nav__tabs ${mobileOpen ? 'is-open' : ''}`.trim()}
        aria-label="Application"
      >
        {navItems.map((item) => (
          <button
            key={item.id}
            type="button"
            className={`dash-nav__tab ${active != null && active === item.id ? 'is-active' : ''}`.trim()}
            aria-current={active != null && active === item.id ? 'page' : undefined}
            onClick={() => navigate(item.destination)}
          >
            {item.label}
          </button>
        ))}
      </nav>

      <div className="dash-nav__account" ref={rootRef}>
        <p
          className={`dash-nav__uplink ${uplink === 'up' ? 'is-ok' : 'is-warn'}`}
          title={uplinkLabel}
        >
          {uplink === 'up' ? 'ONLINE' : uplink === 'checking' ? '…' : 'DOWN'}
        </p>
        <button
          type="button"
          className="dash-nav__account-btn"
          aria-expanded={accountOpen}
          aria-controls={menuId}
          onClick={() => setAccountOpen((open) => !open)}
        >
          <span className="dash-nav__avatar" aria-hidden="true">
            {agentName.slice(0, 1).toUpperCase()}
          </span>
          <span className="dash-nav__agent-name">Agent {agentName}</span>
        </button>
        {accountOpen ? (
          <div id={menuId} className="dash-nav__menu" role="menu">
            <p className="dash-nav__menu-label">Signed in as {agentName}</p>
            <button
              type="button"
              role="menuitem"
              className="dash-nav__menu-item"
              onClick={() => navigate('profile')}
            >
              Agent Profile
            </button>
            <button
              type="button"
              role="menuitem"
              className="dash-nav__menu-item"
              onClick={() => navigate('settings')}
            >
              Settings
            </button>
            <button
              type="button"
              role="menuitem"
              className="dash-nav__menu-item danger"
              onClick={() => {
                setAccountOpen(false)
                onLogout()
              }}
            >
              Logout
            </button>
          </div>
        ) : null}
      </div>
    </header>
  )
}
