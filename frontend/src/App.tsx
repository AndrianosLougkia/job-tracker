import { useState } from 'react'
import AppRouter from './router/AppRouter'
import { AuthContext, loadAuth, saveAuth, clearAuth } from './store/authStore'
import type { AuthUser } from './types'

export default function App() {
  const [user, setUser] = useState<AuthUser | null>(loadAuth)

  function login(u: AuthUser) { saveAuth(u); setUser(u) }
  function logout()           { clearAuth(); setUser(null) }

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      <AppRouter />
    </AuthContext.Provider>
  )
}
