import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from './AuthContext'
import type { Role } from '../api/types'
import { Spinner } from '../components/Spinner'

/** Route guard: signs the user in first, then checks their role. */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return <Spinner label="Checking your session" />
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  if (user.role !== role) {
    return (
      <div className="card empty">
        <h2>Not your door</h2>
        <p>
          This page is for {role.toLowerCase()} accounts. You are signed in as {user.role.toLowerCase()}.
        </p>
      </div>
    )
  }
  return <>{children}</>
}
