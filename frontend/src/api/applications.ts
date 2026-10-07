import client from './client'
import type { Application, ApplicationStatus } from '../types'

export interface CreateApplicationPayload {
  company: string
  role: string
  jobDescription?: string
  notes?: string
  appliedAt?: string
}

export interface UpdateApplicationPayload {
  company?: string
  role?: string
  status?: ApplicationStatus
  jobDescription?: string
  notes?: string
  appliedAt?: string
}

export async function listApplications(): Promise<Application[]> {
  const res = await client.get('/applications')
  return res.data
}

export async function getApplication(id: number): Promise<Application> {
  const res = await client.get(`/applications/${id}`)
  return res.data
}

export async function createApplication(payload: CreateApplicationPayload): Promise<Application> {
  const res = await client.post('/applications', payload)
  return res.data
}

export async function updateApplication(id: number, payload: UpdateApplicationPayload): Promise<Application> {
  const res = await client.patch(`/applications/${id}`, payload)
  return res.data
}

export async function deleteApplication(id: number): Promise<void> {
  await client.delete(`/applications/${id}`)
}
