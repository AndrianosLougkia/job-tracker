import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from '../store/authStore'
import LoginPage          from '../pages/LoginPage'
import RegisterPage       from '../pages/RegisterPage'
import DashboardPage      from '../pages/DashboardPage'
import ApplicationsPage   from '../pages/ApplicationsPage'
import ApplicationDetailPage from '../pages/ApplicationDetailPage'
import ResumesPage        from '../pages/ResumesPage'
import ProfilePage        from '../pages/ProfilePage'

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user } = useAuth()
  return user ? <>{children}</> : <Navigate to="/login" replace />
}

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login"    element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
        <Route path="/applications" element={<ProtectedRoute><ApplicationsPage /></ProtectedRoute>} />
        <Route path="/applications/:id" element={<ProtectedRoute><ApplicationDetailPage /></ProtectedRoute>} />
        <Route path="/resumes" element={<ProtectedRoute><ResumesPage /></ProtectedRoute>} />
        <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
