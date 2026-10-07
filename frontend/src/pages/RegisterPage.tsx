import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { register } from '../api/auth'
import { Input, Button, Alert, Card } from '../components/common'

export default function RegisterPage() {
  const navigate = useNavigate()

  const [email, setEmail]       = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm]   = useState('')
  const [error, setError]       = useState<string | null>(null)
  const [loading, setLoading]   = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (password !== confirm) { setError('Passwords do not match.'); return }
    if (password.length < 8)  { setError('Password must be at least 8 characters.'); return }
    setLoading(true)
    try {
      await register({ email, password })
      navigate('/login')
    } catch (err: any) {
      const msg = err.response?.data?.message ?? 'Registration failed.'
      const fieldErrors: string[] = err.response?.data?.errors ?? []
      setError(fieldErrors.length ? fieldErrors.join(', ') : msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center',
      justifyContent: 'center', padding: '2rem' }}>
      <Card style={{ width: '100%', maxWidth: 400 }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '0.25rem' }}>Create account</h1>
        <p style={{ color: 'var(--color-text-muted)', fontSize: '0.875rem', marginBottom: '1.5rem' }}>
          Already have an account? <Link to="/login">Sign in</Link>
        </p>

        {error && <div style={{ marginBottom: '1rem' }}><Alert message={error} /></div>}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Input label="Email" type="email" value={email}
            onChange={e => setEmail(e.target.value)} required autoFocus />
          <Input label="Password" type="password" value={password}
            onChange={e => setPassword(e.target.value)} required />
          <Input label="Confirm password" type="password" value={confirm}
            onChange={e => setConfirm(e.target.value)} required />
          <Button type="submit" loading={loading}>Create account</Button>
        </form>
      </Card>
    </div>
  )
}
