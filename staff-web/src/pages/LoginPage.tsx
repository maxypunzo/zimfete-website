import { useState, type FormEvent } from 'react'

import { api, ApiError, ensureCsrfCookie } from '../api/client'
import type { Me } from '../api/types'

export function LoginPage({ onLoggedIn }: { onLoggedIn: (me: Me) => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await ensureCsrfCookie()
      const me = await api<Me>('/api/auth/login', { method: 'POST', body: { username, password } })
      onLoggedIn(me)
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError('Wrong username or password, or your account has no asset finance role.')
      } else {
        setError(err instanceof Error ? err.message : 'Could not sign in.')
      }
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <form className="login-card" onSubmit={submit}>
        <img src="/logo.png" alt="ZimFete SACCO" width="72" height="72" />
        <h1>Asset Finance</h1>
        <p className="muted">Sign in with your Mifos username and password.</p>
        {error && (
          <div className="alert alert-danger" role="alert">
            {error}
          </div>
        )}
        <label className="field">
          <span className="field-label">Username</span>
          <input autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required
            autoFocus />
        </label>
        <label className="field">
          <span className="field-label">Password</span>
          <input type="password" autoComplete="current-password" value={password}
            onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}
