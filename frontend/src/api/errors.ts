import axios from 'axios'

/**
 * User-facing message for a failed API call. Uses the backend's structured error body
 * ({ message, errors[] }) when there is one, and says so plainly when the server
 * could not be reached at all.
 */
export function apiErrorMessage(err: unknown, fallback: string): string {
  if (!axios.isAxiosError(err)) return fallback
  if (!err.response) return 'Cannot reach the server. Check your connection and try again.'
  const data = err.response.data as { message?: string; errors?: string[] } | undefined
  if (data?.errors?.length) return data.errors.join(', ')
  if (data?.message) return data.message
  if (err.response.status >= 500) return 'The server ran into a problem. Please try again shortly.'
  return fallback
}
