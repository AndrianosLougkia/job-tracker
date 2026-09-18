import { useState, useEffect } from 'react'

interface BackendHealth {
  status: string
  application: string
  timestamp: string
}

/**
 * Stage 1 landing page.
 * Displays a welcome message and pings the backend health endpoint
 * to confirm full-stack connectivity.
 */
export default function LandingPage() {
  const [health, setHealth] = useState<BackendHealth | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetch('/api/health')
      .then(res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<BackendHealth>
      })
      .then(data => {
        setHealth(data)
        setLoading(false)
      })
      .catch(err => {
        setError(String(err))
        setLoading(false)
      })
  }, [])

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <h1 style={styles.title}>Job Tracker</h1>
        <p style={styles.subtitle}>
          AI-powered job application tracking. Track applications, upload resumes,
          and get AI-driven match analysis.
        </p>

        <div style={styles.statusBox}>
          <h2 style={styles.statusTitle}>Backend Status</h2>
          {loading && <p style={styles.muted}>Checking backend...</p>}
          {error && (
            <p style={styles.errorText}>
              Could not reach backend: {error}
            </p>
          )}
          {health && (
            <div style={styles.healthGrid}>
              <span style={styles.label}>Status</span>
              <span style={{ ...styles.value, color: 'var(--color-success)', fontWeight: 600 }}>
                {health.status}
              </span>
              <span style={styles.label}>Application</span>
              <span style={styles.value}>{health.application}</span>
              <span style={styles.label}>Time</span>
              <span style={styles.value}>
                {new Date(health.timestamp).toLocaleString()}
              </span>
            </div>
          )}
        </div>

        <p style={styles.muted}>
          Stage 1 complete. Login and registration coming in Stage 3.
        </p>
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '2rem',
  },
  card: {
    background: 'var(--color-surface)',
    border: '1px solid var(--color-border)',
    borderRadius: 'var(--radius)',
    padding: '2.5rem',
    maxWidth: '480px',
    width: '100%',
    boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
  },
  title: {
    fontSize: '1.75rem',
    fontWeight: 700,
    marginBottom: '0.5rem',
  },
  subtitle: {
    color: 'var(--color-text-muted)',
    marginBottom: '1.5rem',
    fontSize: '0.95rem',
  },
  statusBox: {
    background: 'var(--color-bg)',
    border: '1px solid var(--color-border)',
    borderRadius: 'var(--radius)',
    padding: '1rem 1.25rem',
    marginBottom: '1.5rem',
  },
  statusTitle: {
    fontSize: '0.85rem',
    fontWeight: 600,
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
    color: 'var(--color-text-muted)',
    marginBottom: '0.75rem',
  },
  healthGrid: {
    display: 'grid',
    gridTemplateColumns: 'auto 1fr',
    gap: '0.25rem 1rem',
    alignItems: 'center',
  },
  label: {
    fontSize: '0.85rem',
    color: 'var(--color-text-muted)',
  },
  value: {
    fontSize: '0.85rem',
  },
  errorText: {
    color: 'var(--color-error)',
    fontSize: '0.875rem',
  },
  muted: {
    color: 'var(--color-text-muted)',
    fontSize: '0.85rem',
    textAlign: 'center' as const,
  },
}
