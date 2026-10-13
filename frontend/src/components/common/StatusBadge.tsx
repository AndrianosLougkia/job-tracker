import type { ApplicationStatus } from '../../types'

const STATUS_CONFIG: Record<ApplicationStatus, { label: string; bg: string; color: string }> = {
  WISHLIST:   { label: 'Wishlist',   bg: '#f1f5f9', color: '#475569' },
  APPLIED:    { label: 'Applied',    bg: '#dbeafe', color: '#1d4ed8' },
  SCREENING:  { label: 'Screening',  bg: '#fef9c3', color: '#854d0e' },
  INTERVIEW:  { label: 'Interview',  bg: '#fce7f3', color: '#9d174d' },
  OFFER:      { label: 'Offer',      bg: '#dcfce7', color: '#15803d' },
  REJECTED:   { label: 'Rejected',   bg: '#fee2e2', color: '#991b1b' },
  WITHDRAWN:  { label: 'Withdrawn',  bg: '#f3f4f6', color: '#6b7280' },
}

export const ALL_STATUSES = Object.keys(STATUS_CONFIG) as ApplicationStatus[]

export function statusLabel(s: ApplicationStatus) { return STATUS_CONFIG[s]?.label ?? s }

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  const cfg = STATUS_CONFIG[status] ?? { label: status, bg: '#f1f5f9', color: '#475569' }
  return (
    <span style={{
      display: 'inline-block', padding: '0.2rem 0.55rem', borderRadius: '999px',
      fontSize: '0.75rem', fontWeight: 600,
      background: cfg.bg, color: cfg.color,
    }}>
      {cfg.label}
    </span>
  )
}

/**
 * Status dropdown styled like a StatusBadge, for changing status in place
 * (e.g. directly in the applications table).
 */
export function StatusSelect({ value, onChange, disabled, ariaLabel = 'Status' }: {
  value: ApplicationStatus
  onChange: (status: ApplicationStatus) => void
  disabled?: boolean
  ariaLabel?: string
}) {
  const cfg = STATUS_CONFIG[value] ?? { bg: '#f1f5f9', color: '#475569' }
  return (
    <select
      aria-label={ariaLabel}
      value={value}
      disabled={disabled}
      onChange={e => onChange(e.target.value as ApplicationStatus)}
      style={{
        padding: '0.2rem 0.4rem', borderRadius: '999px', fontSize: '0.75rem', fontWeight: 600,
        background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.color}33`,
        cursor: disabled ? 'wait' : 'pointer', opacity: disabled ? 0.6 : 1,
      }}
    >
      {ALL_STATUSES.map(s => <option key={s} value={s}>{statusLabel(s)}</option>)}
    </select>
  )
}
