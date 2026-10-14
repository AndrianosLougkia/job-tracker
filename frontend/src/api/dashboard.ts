import client from './client'
import type { DashboardStats } from '../types'

/** Per-user statistics; cached on the server for up to a few minutes, refreshed after any change. */
export async function getDashboardStats(): Promise<DashboardStats> {
  const res = await client.get('/dashboard/stats')
  return res.data
}
