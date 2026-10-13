import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { friendlyMessage } from '../lib/errors'
import type { Role } from '../api/types'

const HOME_BY_ROLE: Record<Role, string> = {
  ATTENDEE: '/',
  STAFF: '/staff/scan',
  ADMIN: '/admin',
}

export function LoginPage() {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from

  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const user = mode === 'login' ? await login(email, password) : await register(name, email, password)
      navigate(from ?? HOME_BY_ROLE[user.role], { replace: true })
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="narrow">
      <div className="card">
        <h1>{mode === 'login' ? 'Sign in' : 'Create an account'}</h1>
        <p className="muted">
          {mode === 'login'
            ? 'Attendees, gate staff and organisers all sign in here.'
            : 'New accounts are attendees. Staff and organiser accounts are created by an organiser.'}
        </p>

        <form onSubmit={submit} className="form">
          {mode === 'register' ? (
            <label>
              Name
              <input value={name} onChange={(event) => setName(event.target.value)} required maxLength={120} />
            </label>
          ) : null}
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              autoComplete="email"
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              minLength={8}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            />
          </label>

          {error ? (
            <div className="banner banner-error" role="alert">
              {friendlyMessage(error)}
            </div>
          ) : null}

          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <p className="muted small">
          {mode === 'login' ? (
            <>
              No account yet?{' '}
              <button type="button" className="link" onClick={() => setMode('register')}>
                Register
              </button>
            </>
          ) : (
            <>
              Already registered?{' '}
              <button type="button" className="link" onClick={() => setMode('login')}>
                Sign in
              </button>
            </>
          )}
        </p>
        <p className="muted small">
          Browsing is open to everyone — <Link to="/">see the events</Link>.
        </p>
      </div>
    </div>
  )
}
