import { useEffect, useRef, useState } from 'react'
import { AppShell } from '../components/common/AppShell'
import { Card, PageHeader, Button, Alert, Spinner, EmptyState } from '../components/common'
import { listResumes, uploadResume, deleteResume } from '../api/resumes'
import type { ResumeResponse } from '../api/resumes'

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default function ResumesPage() {
  const [resumes, setResumes]   = useState<ResumeResponse[]>([])
  const [loading, setLoading]   = useState(true)
  const [uploading, setUploading] = useState(false)
  const [error, setError]       = useState<string | null>(null)
  const [success, setSuccess]   = useState<string | null>(null)
  const fileInputRef            = useRef<HTMLInputElement>(null)

  function load() {
    setLoading(true)
    listResumes()
      .then(setResumes)
      .catch(() => setError('Failed to load resumes.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return

    if (!file.name.toLowerCase().endsWith('.pdf')) {
      setError('Only PDF files are accepted.')
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      setError('File exceeds the 10 MB limit.')
      return
    }

    setError(null)
    setSuccess(null)
    setUploading(true)
    try {
      const resume = await uploadResume(file)
      setResumes(prev => [resume, ...prev])
      setSuccess(`"${resume.originalFilename}" uploaded successfully.${
        resume.textExtracted ? '' : ' (Text extraction failed — the file is still stored.)'
      }`)
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Upload failed.')
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  async function handleDelete(id: number, filename: string) {
    if (!confirm(`Delete "${filename}"?`)) return
    try {
      await deleteResume(id)
      setResumes(prev => prev.filter(r => r.id !== id))
    } catch {
      setError('Failed to delete resume.')
    }
  }

  return (
    <AppShell>
      <PageHeader
        title="Resumes"
        action={
          <>
            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf,application/pdf"
              style={{ display: 'none' }}
              onChange={handleFileChange}
            />
            <Button onClick={() => fileInputRef.current?.click()} loading={uploading}>
              + Upload PDF
            </Button>
          </>
        }
      />

      {error   && <div style={{ marginBottom: '1rem' }}><Alert type="error"   message={error} /></div>}
      {success && <div style={{ marginBottom: '1rem' }}><Alert type="success" message={success} /></div>}

      {loading && <div style={{ textAlign: 'center', padding: '3rem' }}><Spinner /></div>}

      {!loading && resumes.length === 0 && (
        <EmptyState title="No resumes yet"
          description="Upload a PDF resume to use it for AI analysis." />
      )}

      {!loading && resumes.length > 0 && (
        <Card style={{ padding: 0 }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--color-border)',
                background: 'var(--color-bg)' }}>
                {['Filename', 'Size', 'Text extracted', 'Uploaded', ''].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '0.75rem 1rem',
                    color: 'var(--color-text-muted)', fontWeight: 500 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {resumes.map(r => (
                <tr key={r.id} style={{ borderBottom: '1px solid var(--color-border)' }}>
                  <td style={{ padding: '0.75rem 1rem', fontWeight: 500 }}>
                    {r.originalFilename}
                  </td>
                  <td style={{ padding: '0.75rem 1rem', color: 'var(--color-text-muted)' }}>
                    {formatBytes(r.fileSizeBytes)}
                  </td>
                  <td style={{ padding: '0.75rem 1rem' }}>
                    <span style={{ color: r.textExtracted
                      ? 'var(--color-success)' : 'var(--color-text-muted)',
                      fontSize: '0.8rem' }}>
                      {r.textExtracted ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td style={{ padding: '0.75rem 1rem', color: 'var(--color-text-muted)' }}>
                    {new Date(r.createdAt).toLocaleDateString()}
                  </td>
                  <td style={{ padding: '0.75rem 1rem', textAlign: 'right' }}>
                    <Button variant="danger"
                      onClick={() => handleDelete(r.id, r.originalFilename)}
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
