export type ApplicationStatus =
  | 'WISHLIST'
  | 'APPLIED'
  | 'SCREENING'
  | 'INTERVIEW'
  | 'OFFER'
  | 'REJECTED'
  | 'WITHDRAWN'

export interface Application {
  id: number
  userId: number
  company: string
  role: string
  status: ApplicationStatus
  jobDescription: string | null
  notes: string | null
  appliedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AuthUser {
  userId: number
  email: string
  token: string
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  errors?: string[]
}
