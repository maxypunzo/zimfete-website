import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'
import { useOfficeName } from '../api/hooks'

const NAV = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/applications', label: 'Applications' },
  { to: '/queue', label: 'Queue & cash' },
  { to: '/purchase-orders', label: 'Purchase orders' },
  { to: '/assets', label: 'Asset register' },
  { to: '/catalogue', label: 'Catalogue' },
]

export function Layout() {
  const { me, logout } = useAuth()
  const officeName = useOfficeName()
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()
  const [lastPath, setLastPath] = useState(location.pathname)
  if (location.pathname !== lastPath) {
    // Close the mobile menu after navigating.
    setLastPath(location.pathname)
    setMenuOpen(false)
  }
  const role = me.roles.includes('ADMIN') ? 'Admin' : me.roles.includes('MANAGER') ? 'Manager' : 'Officer'

  return (
    <div className="shell">
      {me.demo && (
        <div className="demo-banner">
          Demo mode: practice data only. Nothing here touches real member accounts.
        </div>
      )}
      <header className="topbar">
        <div className="topbar-inner">
          <a className="brand" href="/">
            <img src="/logo.png" alt="" width="36" height="36" />
            <span>
              <strong>ZimFete</strong> <span className="brand-sub">Asset Finance</span>
            </span>
          </a>
          <button
            className="menu-btn"
            aria-expanded={menuOpen}
            aria-controls="main-nav"
            onClick={() => setMenuOpen((o) => !o)}
          >
            Menu
          </button>
          <nav id="main-nav" className={`nav${menuOpen ? ' open' : ''}`} aria-label="Main">
            {NAV.map((n) => (
              <NavLink key={n.to} to={n.to} end={n.end}>
                {n.label}
              </NavLink>
            ))}
            <div className="user">
              <span className="user-name">{me.username}</span>
              <span className="user-meta">
                {role} · {me.allOffices ? 'All locations' : officeName(me.officeId)}
              </span>
              <button className="btn btn-ghost btn-sm" onClick={logout}>
                Sign out
              </button>
            </div>
          </nav>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
