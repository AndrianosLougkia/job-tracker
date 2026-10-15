import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { createAnalysis, getAnalysis, listAnalyses } from '../../api/analyses'
import { listResumes, type ResumeResponse } from '../../api/resumes'
import { useNotifications } from '../../realtime/NotificationProvider'
import { Card, Button, Select, Alert, Spinner } from '../common'
import type { Analysis, AnalysisStatus } from '../../types'
import { apiErrorMessage } from '../../api/errors'

const POLL_INTERVAL_MS = 5_000

const isUnfinished = (status: AnalysisStatus) => status === 'PENDING' || status === 'PROCESSING'

/**
 * AI resume-versus-job-description analysis for one application:
 * pick a resume, run an analysis, view the result and earlier runs.
 *
 * Analyses finish in the background. A WebSocket notification triggers a refetch of the
 * finished analysis; while the live connection is down, unfinished analyses are polled.
 */
export function AnalysisPanel({ applicationId, hasJobDescription }: {
  applicationId: number
  hasJobDescription: boolean
}) {
  const [resumes, setResumes]       = useState<ResumeResponse[]>([])
  const [analyses, setAnalyses]     = useState<Analysis[]>([])
  const [resumeId, setResumeId]     = useState<number | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [loading, setLoading]       = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError]           = useState<string | null>(null)
  const { connection, onAnalysisNotification } = useNotifications()

  // Read by the polling timer without restarting it on every update.
  const analysesRef = useRef(analyses)
  analysesRef.current = analyses
  const hasUnfinished = analyses.some(a => isUnfinished(a.status))

  const refresh = useCallback(async (id: number) => {
    try {
      const fresh = await getAnalysis(id)
      setAnalyses(prev => prev.some(a => a.id === id)
        ? prev.map(a => (a.id === id ? fresh : a))
        : [fresh, ...prev])
    } catch {
      // Keep what is shown; the next notification or poll tries again.
    }
  }, [])

  useEffect(() => {
    Promise.all([listResumes(), listAnalyses(applicationId)])
      .then(([r, a]) => {
        setResumes(r)
        setAnalyses(a)
        setResumeId(r.find(x => x.textExtracted)?.id ?? null)
        setSelectedId(a[0]?.id ?? null)
      })
      .catch(err => setError(apiErrorMessage(err, 'Failed to load analyses.')))
      .finally(() => setLoading(false))
  }, [applicationId])

  // Live updates: fetch the finished analysis as soon as its notification arrives.
  useEffect(() => onAnalysisNotification(n => {
    if (n.applicationId === applicationId) refresh(n.analysisId)
  }), [applicationId, onAnalysisNotification, refresh])

  // Catch-up and fallback: refresh unfinished analyses whenever the connection state
  // changes (a notification may have been missed while it was down), and poll them
  // for as long as there is no live connection.
  useEffect(() => {
    if (!hasUnfinished) return
    const refreshUnfinished = () =>
      analysesRef.current.filter(a => isUnfinished(a.status)).forEach(a => refresh(a.id))
    refreshUnfinished()
    if (connection === 'connected') return
    const timer = setInterval(refreshUnfinished, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [hasUnfinished, connection, refresh])

  const usableResumes = resumes.filter(r => r.textExtracted)
  const selected = analyses.find(a => a.id === selectedId) ?? null
  const resumeName = (id: number) =>
    resumes.find(r => r.id === id)?.originalFilename ?? `Resume #${id}`

  async function handleRun() {
    if (resumeId == null) return
    setSubmitting(true)
    setError(null)
    try {
      const created = await createAnalysis(applicationId, resumeId)
      setAnalyses(prev => [created, ...prev])
      setSelectedId(created.id)
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not start the analysis. Please try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card>
      <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1rem' }}>AI Analysis</h3>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '1.5rem' }}><Spinner /></div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <RunControls
            hasJobDescription={hasJobDescription}
            usableResumes={usableResumes}
            hasAnyResume={resumes.length > 0}
            resumeId={resumeId}
            onResumeChange={setResumeId}
            submitting={submitting}
            onRun={handleRun}
          />

          {error && <Alert message={error} />}

          {hasUnfinished && connection !== 'connected' && (
            <p style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
              Live updates are unavailable right now; checking for results every few seconds.
            </p>
          )}

          {selected && <AnalysisResultView analysis={selected} resumeName={resumeName(selected.resumeId)} />}

          {analyses.length > 1 && (
            <AnalysisHistory analyses={analyses} selectedId={selectedId}
              onSelect={setSelectedId} resumeName={resumeName} />
          )}

          {analyses.length === 0 && !error && (
            <p style={{ fontSize: '0.875rem', color: 'var(--color-text-muted)' }}>
              No analyses yet. Compare a resume against this job description to see how well it matches.
            </p>
          )}
        </div>
      )}
    </Card>
  )
}

function RunControls({ hasJobDescription, usableResumes, hasAnyResume, resumeId, onResumeChange,
                       submitting, onRun }: {
  hasJobDescription: boolean
  usableResumes: ResumeResponse[]
  hasAnyResume: boolean
  resumeId: number | null
  onResumeChange: (id: number) => void
  submitting: boolean
  onRun: () => void
}) {
  if (!hasJobDescription) {
    return <Alert type="info"
      message="Add a job description to this application (click Edit) to run an AI analysis." />
  }
  if (usableResumes.length === 0) {
    return (
      <div style={{ fontSize: '0.875rem', color: 'var(--color-text-muted)' }}>
        {hasAnyResume
          ? 'None of your resumes have readable text (scanned PDFs are not supported). '
          : 'You have not uploaded a resume yet. '}
        <Link to="/resumes">Upload a resume</Link> to run an analysis.
      </div>
    )
  }
  return (
    <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'flex-end', flexWrap: 'wrap' }}>
      <div style={{ flex: '1 1 240px' }}>
        <Select label="Resume" value={resumeId ?? ''}
          onChange={e => onResumeChange(Number(e.target.value))}
          options={usableResumes.map(r => ({ value: String(r.id), label: r.originalFilename }))} />
      </div>
      <Button onClick={onRun} loading={submitting} disabled={resumeId == null}>
        {submitting ? 'Starting…' : 'Run analysis'}
      </Button>
    </div>
  )
}

function AnalysisResultView({ analysis, resumeName }: { analysis: Analysis; resumeName: string }) {
  const meta = (
    <div style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)' }}>
      {resumeName} · {new Date(analysis.createdAt).toLocaleString()} · provider: {analysis.provider}
    </div>
  )

  if (analysis.status === 'FAILED') {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        <Alert message={`Analysis failed: ${analysis.errorMessage ?? 'unknown error'}`} />
        {meta}
      </div>
    )
  }
  if (analysis.status !== 'COMPLETED' || analysis.matchScore == null) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem', fontWeight: 500 }}>
          <Spinner size={16} /> {analysis.status === 'PENDING' ? 'Waiting to start…' : 'Analysing…'}
        </div>
        <p style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
          This can take a minute or more with local models. You will get a notification when it is done,
          so feel free to leave this page.
        </p>
        {meta}
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      <div style={{ display: 'flex', gap: '1.25rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <ScoreRing score={analysis.matchScore} />
        <div style={{ flex: '1 1 240px', display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
          <p style={{ fontSize: '0.95rem', fontWeight: 500 }}>{analysis.summary}</p>
          {meta}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
        gap: '1rem' }}>
        <Section title="Matching skills">
          <Chips items={analysis.matchingSkills} tone="good" empty="No matching skills found" />
        </Section>
        <Section title="Missing skills">
          <Chips items={analysis.missingSkills} tone="bad" empty="No missing skills 🎉" />
        </Section>
        <Section title="Strengths">
          <BulletList items={analysis.strengths} empty="None identified" />
        </Section>
        <Section title="Recommendations">
          <BulletList items={analysis.recommendations} empty="None" />
        </Section>
      </div>
    </div>
  )
}

function scoreColor(score: number) {
  if (score >= 75) return 'var(--color-success)'
  if (score >= 50) return '#d97706'
  return 'var(--color-error)'
}

function ScoreRing({ score }: { score: number }) {
  const color = scoreColor(score)
  return (
    <div role="img" aria-label={`Match score ${score} out of 100`} style={{
      width: 88, height: 88, borderRadius: '50%', flexShrink: 0,
      background: `conic-gradient(${color} ${score * 3.6}deg, var(--color-border) 0deg)`,
      display: 'grid', placeItems: 'center',
    }}>
      <div style={{
        width: 70, height: 70, borderRadius: '50%', background: 'var(--color-surface)',
        display: 'grid', placeItems: 'center', lineHeight: 1.1, textAlign: 'center',
      }}>
        <div>
          <div style={{ fontSize: '1.4rem', fontWeight: 700, color }}>{score}</div>
          <div style={{ fontSize: '0.65rem', color: 'var(--color-text-muted)' }}>match</div>
        </div>
      </div>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h4 style={{ fontSize: '0.8rem', fontWeight: 600, textTransform: 'uppercase',
        letterSpacing: '0.04em', color: 'var(--color-text-muted)', marginBottom: '0.5rem' }}>
        {title}
      </h4>
      {children}
    </div>
  )
}

function Chips({ items, tone, empty }: { items: string[] | null; tone: 'good' | 'bad'; empty: string }) {
  if (!items?.length) return <Muted>{empty}</Muted>
  const style = tone === 'good'
    ? { background: '#dcfce7', color: '#15803d' }
    : { background: '#fee2e2', color: '#991b1b' }
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem' }}>
      {items.map(item => (
        <span key={item} style={{ ...style, padding: '0.2rem 0.6rem', borderRadius: '999px',
          fontSize: '0.78rem', fontWeight: 500 }}>
          {item}
        </span>
      ))}
    </div>
  )
}

function BulletList({ items, empty }: { items: string[] | null; empty: string }) {
  if (!items?.length) return <Muted>{empty}</Muted>
  return (
    <ul style={{ paddingLeft: '1.1rem', fontSize: '0.875rem', display: 'flex',
      flexDirection: 'column', gap: '0.3rem' }}>
      {items.map(item => <li key={item}>{item}</li>)}
    </ul>
  )
}

function Muted({ children }: { children: React.ReactNode }) {
  return <p style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>{children}</p>
}

function AnalysisHistory({ analyses, selectedId, onSelect, resumeName }: {
  analyses: Analysis[]
  selectedId: number | null
  onSelect: (id: number) => void
  resumeName: (id: number) => string
}) {
  return (
    <div>
      <h4 style={{ fontSize: '0.8rem', fontWeight: 600, textTransform: 'uppercase',
        letterSpacing: '0.04em', color: 'var(--color-text-muted)', marginBottom: '0.5rem' }}>
        History
      </h4>
      <div style={{ border: '1px solid var(--color-border)', borderRadius: 'var(--radius)' }}>
        {analyses.map((a, i) => {
          const active = a.id === selectedId
          return (
            <button key={a.id} onClick={() => onSelect(a.id)} style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem',
              width: '100%', padding: '0.6rem 0.9rem', fontSize: '0.85rem', textAlign: 'left',
              border: 'none', borderTop: i === 0 ? 'none' : '1px solid var(--color-border)',
              background: active ? 'var(--color-bg)' : 'transparent', color: 'var(--color-text)',
              fontWeight: active ? 600 : 400,
            }}>
              <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {new Date(a.createdAt).toLocaleString()} · {resumeName(a.resumeId)}
              </span>
              {a.status === 'COMPLETED' && a.matchScore != null
                ? <span style={{ color: scoreColor(a.matchScore), fontWeight: 700 }}>{a.matchScore}</span>
                : isUnfinished(a.status)
                  ? <span style={{ color: 'var(--color-text-muted)', fontSize: '0.75rem' }}>Running…</span>
                  : <span style={{ color: 'var(--color-error)', fontSize: '0.75rem' }}>{a.status}</span>}
            </button>
          )
        })}
      </div>
    </div>
  )
}
