import { AppShell } from '../components/common/AppShell'
import { Card, PageHeader } from '../components/common'
import { useAuth } from '../store/authStore'

export default function ProfilePage() {
  const { user } = useAuth()

  return (
    <AppShell>
      <PageHeader title="Profile" />
      <Card style={{ maxWidth: 480 }}>
        <dl style={{ display: 'grid', gridTemplateColumns: 'auto 1fr',
          gap: '0.6rem 1.5rem', fontSize: '0.875rem' }}>
          <dt style={{ color: 'var(--color-text-muted)', fontWeight: 500 }}>User ID</dt>
          <dd>{user?.userId}</dd>
          <dt style={{ color: 'var(--color-text-muted)', fontWeight: 500 }}>Email</dt>
          <dd>{user?.email}</dd>
        </dl>
      </Card>
    </AppShell>
  )
}
