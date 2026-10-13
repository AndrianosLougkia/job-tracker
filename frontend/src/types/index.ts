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

export type AnalysisStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

/** Result fields are null unless status is COMPLETED; errorMessage is set only when FAILED. */
export interface Analysis {
  id: number
  applicationId: number
  resumeId: number
  status: AnalysisStatus
  provider: string
  matchScore: number | null
  matchingSkills: string[] | null
  missingSkills: string[] | null
  strengths: string[] | null
  recommendations: string[] | null
  summary: string | null
  errorMessage: string | null
  createdAt: string
  completedAt: string | null
}
