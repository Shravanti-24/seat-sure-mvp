import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-mark" aria-hidden="true" />
          SeatSure
        </Link>
        <nav>
          <NavLink to="/">Events</NavLink>
          {user?.role === 'ATTENDEE' ? <NavLink to="/me/tickets">My tickets</NavLink> : null}
          {user?.role === 'STAFF' ? <NavLink to="/staff/scan">Gate</NavLink> : null}
          {user?.role === 'ADMIN' ? <NavLink to="/admin">Admin</NavLink> : null}
        </nav>
        <div className="topbar-right">
          {user ? (
            <>
              <span className="muted hide-sm">{user.name}</span>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => {
                  logout()
                  navigate('/')
                }}
              >
                Sign out
              </button>
            </>
          ) : (
            <Link to="/login" className="btn btn-primary btn-sm">
              Sign in
            </Link>
          )}
        </div>
      </header>
      <main>
        <Outlet />
      </main>
      <footer className="footer muted">
        Fair ticketing for high-demand drops · seats held in Redis, sold by Postgres
      </footer>
    </div>
  )
}
