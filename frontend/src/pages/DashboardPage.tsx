import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getDashboardStats } from '../api/dashboard'
import { AppShell } from '../components/common/AppShell'
import { Card, PageHeader, Spinner, Alert } from '../components/common'
import { StatusBadge } from '../components/common/StatusBadge'
import type { ApplicationStatus, DashboardStats } from '../types'

const STAT_STATUSES: ApplicationStatus[] = ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER']

export default function DashboardPage() {
  const [stats, setStats]     = useState<DashboardStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState<string | null>(null)

  useEffect(() => {
    getDashboardStats()
      .then(setStats)
      .catch(() => setError('Failed to load dashboard statistics.'))
      .finally(() => setLoading(false))
  }, [])

  const recent = stats?.recentApplications ?? []

  return (
    <AppShell>
      <PageHeader title="Dashboard" />

      {loading && <div style={{ textAlign: 'center', padding: '3rem' }}><Spinner /></div>}
      {error   && <Alert message={error} />}

      {!loading && !error && stats && (
        <>
          {/* Stats row */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
            gap: '1rem', marginBottom: '2rem' }}>
            <StatCard label="Total" value={stats.totalApplications} />
            {STAT_STATUSES.map(s => (
              <StatCard key={s} label={s.charAt(0) + s.slice(1).toLowerCase()}
                value={stats.byStatus[s] ?? 0} />
            ))}
          </div>

          {/* Recent applications */}
          <Card>
            <div style={{ display: 'flex', justifyContent: 'space-between',
              alignItems: 'center', marginBottom: '1rem' }}>
              <h2 style={{ fontSize: '1rem', fontWeight: 600 }}>Recent Applications</h2>
              <Link to="/applications" style={{ fontSize: '0.85rem' }}>View all</Link>
            </div>

            {recent.length === 0 ? (
              <p style={{ color: 'var(--color-text-muted)', fontSize: '0.875rem' }}>
                No applications yet. <Link to="/applications">Add one</Link>.
              </p>
            ) : (
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
                    {['Company', 'Role', 'Status', 'Date'].map(h => (
                      <th key={h} style={{ textAlign: 'left', padding: '0.4rem 0.5rem',
                        color: 'var(--color-text-muted)', fontWeight: 500 }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {recent.map(app => (
                    <tr key={app.id} style={{ borderBottom: '1px solid var(--color-border)' }}>
                      <td style={{ padding: '0.6rem 0.5rem' }}>
                        <Link to={`/applications/${app.id}`}>{app.company}</Link>
                      </td>
                      <td style={{ padding: '0.6rem 0.5rem' }}>{app.role}</td>
                      <td style={{ padding: '0.6rem 0.5rem' }}>
                        <StatusBadge status={app.status} />
                      </td>
                      <td style={{ padding: '0.6rem 0.5rem', color: 'var(--color-text-muted)' }}>
                        {app.appliedAt
                          ? new Date(app.appliedAt).toLocaleDateString()
                          : new Date(app.createdAt).toLocaleDateString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>

          <p style={{ marginTop: '0.75rem', fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>
            Statistics as of {new Date(stats.generatedAt).toLocaleTimeString()}
          </p>
        </>
      )}
    </AppShell>
  )
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <Card style={{ textAlign: 'center' }}>
      <div style={{ fontSize: '1.8rem', fontWeight: 700, color: 'var(--color-primary)' }}>{value}</div>
      <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', marginTop: '0.2rem' }}>{label}</div>
    </Card>
  )
}
