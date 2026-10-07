import { createContext, useContext } from 'react'
import type { AuthUser } from '../types'

const TOKEN_KEY = 'jt_token'
const USER_KEY  = 'jt_user'

// Persist to localStorage
export function saveAuth(user: AuthUser) {
  localStorage.setItem(TOKEN_KEY, user.token)
  localStorage.setItem(USER_KEY, JSON.stringify({ userId: user.userId, email: user.email }))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function loadAuth(): AuthUser | null {
  const token = localStorage.getItem(TOKEN_KEY)
  const raw   = localStorage.getItem(USER_KEY)
  if (!token || !raw) return null
  try {
    const { userId, email } = JSON.parse(raw)
    return { token, userId, email }
  } catch {
    return null
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

// React context
export interface AuthContextValue {
  user: AuthUser | null
  login:  (user: AuthUser) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue>({
  user: null,
  login:  () => {},
  logout: () => {},
})

export function useAuth() {
  return useContext(AuthContext)
}
