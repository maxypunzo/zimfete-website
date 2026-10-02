import { createContext, useContext, useEffect, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import { api, ApiError, onUnauthorized } from '../api/client'
import type { Me, Role } from '../api/types'
import { LoginPage } from '../pages/LoginPage'
import { FullPageMessage } from '../components/ui'

type Auth = {
  me: Me
  can: (role: Role) => boolean
  logout: () => Promise<void>
}

const AuthContext = createContext<Auth | null>(null)

const ME_KEY = ['me']

async function fetchMe(): Promise<Me | null> {
  try {
    return await api<Me>('/api/me')
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null
    throw e
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient()
  const { data: me, isPending, error, refetch } = useQuery({
    queryKey: ME_KEY,
    queryFn: fetchMe,
    staleTime: Infinity,
    retry: false,
  })

  useEffect(() => {
    onUnauthorized(() => client.setQueryData(ME_KEY, null))
  }, [client])

  if (isPending) return <FullPageMessage>Loading…</FullPageMessage>
  if (error) {
    return (
      <FullPageMessage>
        <p>{error.message}</p>
        <button className="btn" onClick={() => refetch()}>
          Try again
        </button>
      </FullPageMessage>
    )
  }
  if (!me) {
    return (
      <LoginPage
        onLoggedIn={(user) => {
          client.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' })
          client.setQueryData(ME_KEY, user)
        }}
      />
    )
  }

  const auth: Auth = {
    me,
    can: (role) => me.roles.includes(role),
    logout: async () => {
      try {
        await api<void>('/api/auth/logout', { method: 'POST' })
      } finally {
        // Keep the 'me' entry (the login gate watches it); drop every other cached screen.
        client.setQueryData(ME_KEY, null)
        client.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' })
      }
    },
  }
  return <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>
}

export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth outside AuthProvider')
  return auth
}
