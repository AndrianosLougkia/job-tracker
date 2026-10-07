import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import { saveAuth, useAuth } from '../store/authStore'
import { Input, Button, Alert, Card } from '../components/common'

export default function LoginPage() {
  const { login: setUser } = useAuth()
  const navigate = useNavigate()

  const [email, setEmail]       = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState<string | null>(null)
  const [loading, setLoading]   = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const user = await login({ email, password })
      saveAuth(user)
      setUser(user)
      navigate('/dashboard')
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Login failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center',
      justifyContent: 'center', padding: '2rem' }}>
      <Card style={{ width: '100%', maxWidth: 400 }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '0.25rem' }}>Sign in</h1>
        <p style={{ color: 'var(--color-text-muted)', fontSize: '0.875rem', marginBottom: '1.5rem' }}>
          Don't have an account? <Link to="/register">Register</Link>
        </p>

        {error && <div style={{ marginBottom: '1rem' }}><Alert message={error} /></div>}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Input label="Email" type="email" value={email}
            onChange={e => setEmail(e.target.value)} required autoFocus />
          <Input label="Password" type="password" value={password}
            onChange={e => setPassword(e.target.value)} required />
          <Button type="submit" loading={loading}>Sign in</Button>
        </form>
      </Card>
    </div>
  )
}
