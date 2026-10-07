import { useEffect, useState, type FormEvent } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { getApplication, updateApplication, deleteApplication } from '../api/applications'
import { AppShell } from '../components/common/AppShell'
import { StatusBadge, ALL_STATUSES, statusLabel } from '../components/common/StatusBadge'
import { Card, PageHeader, Button, Input, Textarea, Select, Alert, Spinner } from '../components/common'
import type { Application, ApplicationStatus } from '../types'

export default function ApplicationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [app, setApp]         = useState<Application | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState<string | null>(null)
  const [editing, setEditing] = useState(false)

  useEffect(() => {
    getApplication(Number(id))
      .then(setApp)
      .catch(() => setError('Application not found.'))
      .finally(() => setLoading(false))
  }, [id])

  async function handleDelete() {
    if (!confirm('Delete this application?')) return
    try {
      await deleteApplication(Number(id))
      navigate('/applications')
    } catch {
      setError('Failed to delete.')
    }
  }

  if (loading) return <AppShell><div style={{ textAlign: 'center', padding: '3rem' }}><Spinner /></div></AppShell>
  if (error || !app) return <AppShell><Alert message={error ?? 'Not found.'} /></AppShell>

  return (
    <AppShell>
      <PageHeader
        title={`${app.company} — ${app.role}`}
        action={
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <Button variant="secondary" onClick={() => setEditing(e => !e)}>
              {editing ? 'Cancel' : 'Edit'}
            </Button>
            <Button variant="danger" onClick={handleDelete}>Delete</Button>
          </div>
        }
      />

      {editing ? (
        <EditForm app={app} onSaved={updated => { setApp(updated); setEditing(false) }} />
      ) : (
        <ViewDetail app={app} />
      )}
    </AppShell>
  )
}

function ViewDetail({ app }: { app: Application }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <Card>
        <dl style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '0.5rem 1.5rem',
          fontSize: '0.875rem' }}>
          <Dt>Status</Dt>     <dd><StatusBadge status={app.status} /></dd>
          <Dt>Company</Dt>    <dd>{app.company}</dd>
          <Dt>Role</Dt>       <dd>{app.role}</dd>
          <Dt>Applied</Dt>    <dd>{app.appliedAt ? new Date(app.appliedAt).toLocaleDateString() : '--'}</dd>
          <Dt>Created</Dt>    <dd>{new Date(app.createdAt).toLocaleString()}</dd>
          <Dt>Updated</Dt>    <dd>{new Date(app.updatedAt).toLocaleString()}</dd>
        </dl>
      </Card>
      {app.jobDescription && (
        <Card>
          <h3 style={{ fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.5rem' }}>Job Description</h3>
          <pre style={{ whiteSpace: 'pre-wrap', fontSize: '0.875rem',
            color: 'var(--color-text-muted)', margin: 0, fontFamily: 'inherit' }}>
            {app.jobDescription}
          </pre>
        </Card>
      )}
      {app.notes && (
        <Card>
          <h3 style={{ fontSize: '0.875rem', fontWeight: 600, marginBottom: '0.5rem' }}>Notes</h3>
          <pre style={{ whiteSpace: 'pre-wrap', fontSize: '0.875rem',
            color: 'var(--color-text-muted)', margin: 0, fontFamily: 'inherit' }}>
            {app.notes}
          </pre>
        </Card>
      )}
    </div>
  )
}

function Dt({ children }: { children: React.ReactNode }) {
  return <dt style={{ color: 'var(--color-text-muted)', fontWeight: 500 }}>{children}</dt>
}

function EditForm({ app, onSaved }: { app: Application; onSaved: (a: Application) => void }) {
  const [company, setCompany]   = useState(app.company)
  const [role, setRole]         = useState(app.role)
  const [status, setStatus]     = useState<ApplicationStatus>(app.status)
  const [jd, setJd]             = useState(app.jobDescription ?? '')
  const [notes, setNotes]       = useState(app.notes ?? '')
  const [appliedAt, setAppliedAt] = useState(app.appliedAt ?? '')
  const [loading, setLoading]   = useState(false)
  const [error, setError]       = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setLoading(true)
    try {
      const updated = await updateApplication(app.id, {
        company, role, status,
        jobDescription: jd || undefined,
        notes: notes || undefined,
        appliedAt: appliedAt || undefined,
      })
      onSaved(updated)
    } catch {
      setError('Failed to save changes.')
    } finally {
      setLoading(false)
    }
  }

  const statusOptions = ALL_STATUSES.map(s => ({ value: s, label: statusLabel(s) }))

  return (
    <Card>
      {error && <div style={{ marginBottom: '1rem' }}><Alert message={error} /></div>}
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <Input label="Company" value={company} onChange={e => setCompany(e.target.value)} required />
          <Input label="Role"    value={role}    onChange={e => setRole(e.target.value)}    required />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <Select label="Status" value={status}
            onChange={e => setStatus(e.target.value as ApplicationStatus)}
            options={statusOptions} />
          <Input label="Applied date" type="date" value={appliedAt}
            onChange={e => setAppliedAt(e.target.value)} />
        </div>
        <Textarea label="Job description" value={jd} onChange={e => setJd(e.target.value)} />
        <Textarea label="Notes" value={notes} onChange={e => setNotes(e.target.value)} />
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button type="submit" loading={loading}>Save changes</Button>
        </div>
      </form>
    </Card>
  )
}
