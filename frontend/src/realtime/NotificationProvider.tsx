import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Client } from '@stomp/stompjs'
import { useAuth } from '../store/authStore'
import type { AnalysisNotification } from '../types'

export type ConnectionState = 'connecting' | 'connected' | 'disconnected'
type Listener = (n: AnalysisNotification) => void

interface NotificationContextValue {
  connection: ConnectionState
  /** Register a listener for analysis notifications; returns an unsubscribe function. */
  onAnalysisNotification: (listener: Listener) => () => void
}

const NotificationContext = createContext<NotificationContextValue>({
  connection: 'disconnected',
  onAnalysisNotification: () => () => {},
})

export function useNotifications() {
  return useContext(NotificationContext)
}

const QUEUE = '/user/queue/analyses'
const RECONNECT_DELAY_MS = 5_000
const TOAST_DURATION_MS = 8_000

function brokerUrl() {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/api/ws`
}

/**
 * Keeps one STOMP connection open while a user is signed in and shows a toast for
 * every analysis notification. The connection retries every few seconds when it drops;
 * pages that need fresh data check `connection` and fall back to polling meanwhile.
 */
export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth()
  const [connection, setConnection] = useState<ConnectionState>('disconnected')
  const [toasts, setToasts] = useState<AnalysisNotification[]>([])
  const listeners = useRef(new Set<Listener>())

  const onAnalysisNotification = useCallback((listener: Listener) => {
    listeners.current.add(listener)
    return () => { listeners.current.delete(listener) }
  }, [])

  const dismiss = useCallback((analysisId: number) => {
    setToasts(prev => prev.filter(t => t.analysisId !== analysisId))
  }, [])

  useEffect(() => {
    if (!user) return

    const client = new Client({
      brokerURL: brokerUrl(),
      connectHeaders: { Authorization: `Bearer ${user.token}` },
      reconnectDelay: RECONNECT_DELAY_MS,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        setConnection('connected')
        client.subscribe(QUEUE, frame => {
          const notification: AnalysisNotification = JSON.parse(frame.body)
          listeners.current.forEach(listener => listener(notification))
          setToasts(prev => [...prev.filter(t => t.analysisId !== notification.analysisId), notification])
          setTimeout(() => dismiss(notification.analysisId), TOAST_DURATION_MS)
        })
      },
      onStompError: frame => {
        // A rejected token will not become valid by retrying; REST calls will send the user to login.
        if (/token/i.test(frame.headers.message ?? '')) client.deactivate()
      },
      onWebSocketClose: () => setConnection('disconnected'),
    })

    setConnection('connecting')
    client.activate()
    return () => {
      client.deactivate()
      setConnection('disconnected')
    }
  }, [user, dismiss])

  return (
    <NotificationContext.Provider value={{ connection, onAnalysisNotification }}>
      {children}
      <ToastStack toasts={toasts} onDismiss={dismiss} />
    </NotificationContext.Provider>
  )
}

function ToastStack({ toasts, onDismiss }: {
  toasts: AnalysisNotification[]
  onDismiss: (analysisId: number) => void
}) {
  if (toasts.length === 0) return null
  return (
    <div role="status" aria-live="polite" style={{
      position: 'fixed', right: '1rem', bottom: '1rem', zIndex: 1000,
      display: 'flex', flexDirection: 'column', gap: '0.5rem', maxWidth: 'min(360px, calc(100vw - 2rem))',
    }}>
      {toasts.map(t => {
        const ok = t.status === 'COMPLETED'
        return (
          <div key={t.analysisId} style={{
            background: 'var(--color-surface)', border: '1px solid var(--color-border)',
            borderLeft: `4px solid ${ok ? 'var(--color-success)' : 'var(--color-error)'}`,
            borderRadius: 'var(--radius)', boxShadow: '0 4px 12px rgba(15, 23, 42, 0.12)',
            padding: '0.75rem 0.9rem', fontSize: '0.875rem',
            display: 'flex', gap: '0.75rem', alignItems: 'flex-start',
          }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{t.message}</div>
              <Link to={`/applications/${t.applicationId}`} onClick={() => onDismiss(t.analysisId)}
                style={{ fontSize: '0.8rem' }}>
                View application
              </Link>
            </div>
            <button onClick={() => onDismiss(t.analysisId)} aria-label="Dismiss notification" style={{
              background: 'none', border: 'none', color: 'var(--color-text-muted)',
              fontSize: '1.1rem', lineHeight: 1, padding: 0,
            }}>
              ×
            </button>
          </div>
        )
      })}
    </div>
  )
}
