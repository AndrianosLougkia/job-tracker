import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { listApplications, createApplication, deleteApplication } from '../api/applications'
import { AppShell } from '../components/common/AppShell'
import { StatusBadge, ALL_STATUSES } from '../components/common/StatusBadge'
import { Card, PageHeader, Button, Input, Textarea, Select, Alert, Spinner, EmptyState } from '../components/common'
import type { Application } from '../types'

export default function ApplicationsPage() {
  const [apps, setApps]         = useState<Application[]>([])
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)

  function load() {
    setLoading(true)
    listApplications()
      .then(setApps)
      .catch(() => setError('Failed to load applications.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  async function handleDelete(id: number) {
    if (!confirm('Delete this application?')) return
    try {
      await deleteApplication(id)
      setApps(prev => prev.filter(a => a.id !== id))
    } catch {
      setError('Failed to delete application.')
    }
  }

  return (
    <AppShell>
      <PageHeader
        title="Applications"
        action={<Button onClick={() => setShowForm(true)}>+ New application</Button>}
      />

      {error && <div style={{ marginBottom: '1rem' }}><Alert message={error} /></div>}

      {showForm && (
        <CreateApplicationForm
          onCreated={app => { setApps(prev => [app, ...prev]); setShowForm(false) }}
          onCancel={() => setShowForm(false)}
        />
      )}

      {loading && <div style={{ textAlign: 'center', padding: '3rem' }}><Spinner /></div>}

      {!loading && apps.length === 0 && !showForm && (
        <EmptyState title="No applications yet"
          description="Click 'New application' to start tracking." />
      )}

      {!loading && apps.length > 0 && (
        <Card style={{ padding: 0 }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--color-border)',
                background: 'var(--color-bg)' }}>
                {['Company', 'Role', 'Status', 'Applied', ''].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '0.75rem 1rem',
                    color: 'var(--color-text-muted)', fontWeight: 500 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {apps.map(app => (
                <tr key={app.id} style={{ borderBottom: '1px solid var(--color-border)' }}>
                  <td style={{ padding: '0.75rem 1rem' }}>
                    <Link to={`/applications/${app.id}`} style={{ fontWeight: 500 }}>
                      {app.company}
                    </Link>
                  </td>
                  <td style={{ padding: '0.75rem 1rem', color: 'var(--color-text-muted)' }}>
                    {app.role}
                  </td>
                  <td style={{ padding: '0.75rem 1rem' }}>
                    <StatusBadge status={app.status} />
                  </td>
                  <td style={{ padding: '0.75rem 1rem', color: 'var(--color-text-muted)' }}>
                    {app.appliedAt
                      ? new Date(app.appliedAt).toLocaleDateString()
                      : '--'}
                  </td>
                  <td style={{ padding: '0.75rem 1rem', textAlign: 'right' }}>
                    <Button variant="danger" onClick={() => handleDelete(app.id)}
                      style={{ padding: '0.3rem 0.7rem', fontSize: '0.8rem' }}>
                      Delete
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </AppShell>
  )
}

function CreateApplicationForm({
  onCreated, onCancel,
}: { onCreated: (app: Application) => void; onCancel: () => void }) {
  const [company, setCompany]   = useState('')
  const [role, setRole]         = useState('')
  const [jd, setJd]             = useState('')
  const [notes, setNotes]       = useState('')
  const [appliedAt, setAppliedAt] = useState('')
  const [status, setStatus]     = useState('WISHLIST')
  const [errors, setErrors]     = useState<Record<string, string>>({})
  const [loading, setLoading]   = useState(false)
  const [apiError, setApiError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const errs: Record<string, string> = {}
    if (!company.trim()) errs.company = 'Required'
    if (!role.trim())    errs.role    = 'Required'
    if (Object.keys(errs).length) { setErrors(errs); return }

    setLoading(true)
    try {
      const app = await createApplication({
        company, role,
        jobDescription: jd || undefined,
        notes: notes || undefined,
        appliedAt: appliedAt || undefined,
      })
      onCreated(app)
    } catch (err: any) {
      setApiError(err.response?.data?.message ?? 'Failed to create application.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card style={{ marginBottom: '1.5rem' }}>
      <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1.25rem' }}>
        New application
      </h2>
      {apiError && <div style={{ marginBottom: '1rem' }}><Alert message={apiError} /></div>}
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <Input label="Company" value={company} onChange={e => setCompany(e.target.value)}
            error={errors.company} autoFocus />
          <Input label="Role" value={role} onChange={e => setRole(e.target.value)}
            error={errors.role} />
        </div>
        <Input label="Applied date (optional)" type="date" value={appliedAt}
          onChange={e => setAppliedAt(e.target.value)} />
        <Textarea label="Job description (optional)" value={jd}
          onChange={e => setJd(e.target.value)} />
        <Textarea label="Notes (optional)" value={notes}
          onChange={e => setNotes(e.target.value)} />
        <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
          <Button variant="secondary" type="button" onClick={onCancel}>Cancel</Button>
          <Button type="submit" loading={loading}>Save</Button>
        </div>
      </form>
    </Card>
  )
}
