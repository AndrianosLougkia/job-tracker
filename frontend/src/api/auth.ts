import client from './client'
import type { AuthUser } from '../types'

export interface RegisterPayload { email: string; password: string }
export interface LoginPayload    { email: string; password: string }

export async function register(payload: RegisterPayload): Promise<{ id: number; email: string }> {
  const res = await client.post('/auth/register', payload)
  return res.data
}

export async function login(payload: LoginPayload): Promise<AuthUser> {
  const res = await client.post('/auth/login', payload)
  const { token, userId, email } = res.data
  return { token, userId, email }
}
