import client from './client'

export interface ResumeResponse {
  id: number
  userId: number
  originalFilename: string
  fileSizeBytes: number
  textExtracted: boolean
  createdAt: string
}

export async function listResumes(): Promise<ResumeResponse[]> {
  const res = await client.get('/resumes')
  return res.data
}

export async function uploadResume(file: File): Promise<ResumeResponse> {
  const form = new FormData()
  form.append('file', file)
  const res = await client.post('/resumes', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data
}

export async function deleteResume(id: number): Promise<void> {
  await client.delete(`/resumes/${id}`)
}
