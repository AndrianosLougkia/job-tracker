import React from 'react'

// ------------------------------------------------------------------ //
//  Button                                                             //
// ------------------------------------------------------------------ //
interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger'
  loading?: boolean
}

export function Button({ variant = 'primary', loading, children, disabled, ...rest }: ButtonProps) {
  const base: React.CSSProperties = {
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    gap: '0.4rem', padding: '0.5rem 1.1rem', borderRadius: 'var(--radius)',
    fontWeight: 500, fontSize: '0.9rem', cursor: disabled || loading ? 'not-allowed' : 'pointer',
    border: 'none', transition: 'background 0.15s',
    opacity: disabled || loading ? 0.65 : 1,
  }
  const variants: Record<string, React.CSSProperties> = {
    primary:   { background: 'var(--color-primary)',   color: '#fff' },
    secondary: { background: 'var(--color-bg)',        color: 'var(--color-text)',
                 border: '1px solid var(--color-border)' },
    danger:    { background: 'var(--color-error)',     color: '#fff' },
  }
  return (
    <button style={{ ...base, ...variants[variant] }} disabled={disabled || loading} {...rest}>
      {loading ? <Spinner size={14} /> : null}
      {children}
    </button>
  )
}

// ------------------------------------------------------------------ //
//  Input                                                              //
// ------------------------------------------------------------------ //
interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
}

export function Input({ label, error, id, ...rest }: InputProps) {
  const inputId = id ?? label.toLowerCase().replace(/\s+/g, '-')
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
      <label htmlFor={inputId} style={{ fontSize: '0.85rem', fontWeight: 500 }}>{label}</label>
      <input
        id={inputId}
        style={{
          padding: '0.5rem 0.75rem', borderRadius: 'var(--radius)', fontSize: '0.9rem',
          border: `1px solid ${error ? 'var(--color-error)' : 'var(--color-border)'}`,
          background: 'var(--color-surface)', color: 'var(--color-text)', width: '100%',
          boxSizing: 'border-box',
        }}
        {...rest}
      />
      {error && <span style={{ fontSize: '0.78rem', color: 'var(--color-error)' }}>{error}</span>}
    </div>
  )
}

// ------------------------------------------------------------------ //
//  Textarea                                                           //
// ------------------------------------------------------------------ //
interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string
  error?: string
}

export function Textarea({ label, error, id, ...rest }: TextareaProps) {
  const inputId = id ?? label.toLowerCase().replace(/\s+/g, '-')
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
      <label htmlFor={inputId} style={{ fontSize: '0.85rem', fontWeight: 500 }}>{label}</label>
      <textarea
        id={inputId}
        rows={4}
        style={{
          padding: '0.5rem 0.75rem', borderRadius: 'var(--radius)', fontSize: '0.9rem',
          border: `1px solid ${error ? 'var(--color-error)' : 'var(--color-border)'}`,
          background: 'var(--color-surface)', color: 'var(--color-text)',
          width: '100%', boxSizing: 'border-box', resize: 'vertical', fontFamily: 'inherit',
        }}
        {...rest}
      />
      {error && <span style={{ fontSize: '0.78rem', color: 'var(--color-error)' }}>{error}</span>}
    </div>
  )
}

// ------------------------------------------------------------------ //
//  Select                                                             //
// ------------------------------------------------------------------ //
interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  error?: string
  options: { value: string; label: string }[]
}

export function Select({ label, error, id, options, ...rest }: SelectProps) {
  const inputId = id ?? label.toLowerCase().replace(/\s+/g, '-')
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
      <label htmlFor={inputId} style={{ fontSize: '0.85rem', fontWeight: 500 }}>{label}</label>
      <select
        id={inputId}
        style={{
          padding: '0.5rem 0.75rem', borderRadius: 'var(--radius)', fontSize: '0.9rem',
          border: `1px solid ${error ? 'var(--color-error)' : 'var(--color-border)'}`,
          background: 'var(--color-surface)', color: 'var(--color-text)', width: '100%',
        }}
        {...rest}
      >
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      {error && <span style={{ fontSize: '0.78rem', color: 'var(--color-error)' }}>{error}</span>}
    </div>
  )
}

// ------------------------------------------------------------------ //
//  Alert                                                              //
// ------------------------------------------------------------------ //
interface AlertProps { type?: 'error' | 'success' | 'info'; message: string }

export function Alert({ type = 'error', message }: AlertProps) {
  const colors = {
    error:   { bg: '#fef2f2', border: '#fecaca', text: 'var(--color-error)' },
    success: { bg: '#f0fdf4', border: '#bbf7d0', text: 'var(--color-success)' },
    info:    { bg: '#eff6ff', border: '#bfdbfe', text: 'var(--color-primary)' },
  }
  const c = colors[type]
  return (
    <div style={{
      padding: '0.75rem 1rem', borderRadius: 'var(--radius)', fontSize: '0.875rem',
      background: c.bg, border: `1px solid ${c.border}`, color: c.text,
    }}>
      {message}
    </div>
  )
}

// ------------------------------------------------------------------ //
//  Spinner                                                            //
// ------------------------------------------------------------------ //
export function Spinner({ size = 20 }: { size?: number }) {
  return (
    <span style={{
      display: 'inline-block', width: size, height: size,
      border: '2px solid var(--color-border)',
      borderTopColor: 'var(--color-primary)',
      borderRadius: '50%', animation: 'spin 0.6s linear infinite',
    }} />
  )
}

// ------------------------------------------------------------------ //
//  EmptyState                                                         //
// ------------------------------------------------------------------ //
export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div style={{ textAlign: 'center', padding: '3rem 1rem', color: 'var(--color-text-muted)' }}>
      <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>📋</div>
      <div style={{ fontWeight: 600, marginBottom: '0.25rem', color: 'var(--color-text)' }}>{title}</div>
      {description && <div style={{ fontSize: '0.875rem' }}>{description}</div>}
    </div>
  )
}

// ------------------------------------------------------------------ //
//  Card / Page shell                                                  //
// ------------------------------------------------------------------ //
export function Card({ children, style }: { children: React.ReactNode; style?: React.CSSProperties }) {
  return (
    <div style={{
      background: 'var(--color-surface)', border: '1px solid var(--color-border)',
      borderRadius: 'var(--radius)', padding: '1.5rem',
      boxShadow: '0 1px 4px rgba(0,0,0,0.05)', ...style,
    }}>
      {children}
    </div>
  )
}

export function PageHeader({ title, action }: { title: string; action?: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      marginBottom: '1.5rem' }}>
      <h1 style={{ fontSize: '1.4rem', fontWeight: 700 }}>{title}</h1>
      {action}
    </div>
  )
}
