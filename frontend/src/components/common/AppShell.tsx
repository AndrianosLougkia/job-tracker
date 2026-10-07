import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../store/authStore'

const NAV_LINKS = [
  { to: '/dashboard',    label: 'Dashboard' },
  { to: '/applications', label: 'Applications' },
  { to: '/resumes',      label: 'Resumes' },
  { to: '/profile',      label: 'Profile' },
]

export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      {/* Sidebar */}
      <nav style={{
        width: 220, background: 'var(--color-surface)',
        borderRight: '1px solid var(--color-border)',
        display: 'flex', flexDirection: 'column', padding: '1.5rem 0',
        position: 'sticky', top: 0, height: '100vh',
      }}>
        <div style={{ padding: '0 1.25rem', marginBottom: '2rem' }}>
          <span style={{ fontWeight: 700, fontSize: '1.1rem' }}>Job Tracker</span>
        </div>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '0.15rem' }}>
          {NAV_LINKS.map(({ to, label }) => (
            <NavLink key={to} to={to} style={({ isActive }) => ({
              padding: '0.55rem 1.25rem', fontSize: '0.9rem', textDecoration: 'none',
              color: isActive ? 'var(--color-primary)' : 'var(--color-text)',
              background: isActive ? '#eff6ff' : 'transparent',
              fontWeight: isActive ? 600 : 400,
              borderRight: isActive ? '2px solid var(--color-primary)' : '2px solid transparent',
            })}>
              {label}
            </NavLink>
          ))}
        </div>
        <div style={{ padding: '0 1.25rem', borderTop: '1px solid var(--color-border)', paddingTop: '1rem' }}>
          <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginBottom: '0.5rem',
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {user?.email}
          </div>
          <button onClick={handleLogout} style={{
            background: 'none', border: 'none', cursor: 'pointer',
            color: 'var(--color-text-muted)', fontSize: '0.85rem', padding: 0,
          }}>
            Sign out
          </button>
        </div>
      </nav>

      {/* Main content */}
      <main style={{ flex: 1, padding: '2rem', maxWidth: 900, margin: '0 auto', width: '100%' }}>
        {children}
      </main>
    </div>
  )
}
