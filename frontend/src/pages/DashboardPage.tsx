import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listApplications } from '../api/applications'
import { AppShell } from '../components/common/AppShell'
import { Card, PageHeader, Spinner, Alert } from '../components/common'
import { StatusBadge } from '../components/common/StatusBadge'
import type { Application, ApplicationStatus } from '../types'

const STAT_STATUSES: ApplicationStatus[] = ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER']

export default function DashboardPage() {
  const [apps, setApps]     = useState<Application[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError]   = useState<string | null>(null)

  useEffect(() => {
    listApplications()
      .then(setApps)
      .catch(() => setError('Failed to load applications.'))
      .finally(() => setLoading(false))
  }, [])

  const countByStatus = (s: ApplicationStatus) => apps.filter(a => a.status === s).length
  const recent = [...apps].slice(0, 5)

  return (
    <AppShell>
      <PageHeader title="Dashboard" />

      {loading && <div style={{ textAlign: 'center', padding: '3rem' }}><Spinner /></div>}
      {error   && <Alert message={error} />}

      {!loading && !error && (
        <>
          {/* Stats row */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
            gap: '1rem', marginBottom: '2rem' }}>
            <StatCard label="Total" value={apps.length} />
            {STAT_STATUSES.map(s => (
              <StatCard key={s} label={s.charAt(0) + s.slice(1).toLowerCase()}
                value={countByStatus(s)} />
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
