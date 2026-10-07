import { AppShell } from '../components/common/AppShell'
import { PageHeader, EmptyState } from '../components/common'

export default function ResumesPage() {
  return (
    <AppShell>
      <PageHeader title="Resumes" />
      <EmptyState
        title="Resume upload coming in Stage 5"
        description="PDF upload, storage, and text extraction will be available soon."
      />
    </AppShell>
  )
}
