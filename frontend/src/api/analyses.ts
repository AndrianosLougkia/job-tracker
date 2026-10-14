import client from './client'
import type { Analysis } from '../types'

export async function listAnalyses(applicationId?: number): Promise<Analysis[]> {
  const res = await client.get('/analyses', { params: { applicationId } })
  return res.data
}

export async function getAnalysis(id: number): Promise<Analysis> {
  const res = await client.get(`/analyses/${id}`)
  return res.data
}

/** Starts an analysis; it comes back PENDING and finishes in the background. */
export async function createAnalysis(applicationId: number, resumeId: number): Promise<Analysis> {
  const res = await client.post('/analyses', { applicationId, resumeId })
  return res.data
}
