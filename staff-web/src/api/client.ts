/** Thin fetch wrapper: same-origin session cookie, CSRF header on writes, problem-detail errors. */

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

let unauthorizedHandler: (() => void) | null = null

/** Called when the server says the session has ended (401) so the app can show the login page. */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith(name + '='))
    ?.slice(name.length + 1)
}

type Options = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  form?: FormData
}

export async function api<T>(path: string, { method = 'GET', body, form }: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
  }
  let payload: BodyInit | undefined
  if (form) {
    payload = form
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }

  let response: Response
  try {
    response = await fetch(path, { method, headers, body: payload, credentials: 'same-origin' })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your internet connection and try again.')
  }

  if (response.status === 401 && !path.startsWith('/api/auth/')) {
    unauthorizedHandler?.()
  }
  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response))
  }
  if (response.status === 204) return undefined as T
  const type = response.headers.get('content-type') ?? ''
  return (type.includes('json') ? await response.json() : undefined) as T
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const problem = await response.json()
    if (problem?.detail) return problem.detail
    if (problem?.title) return problem.title
  } catch {
    // not JSON
  }
  switch (response.status) {
    case 401:
      return 'Your session has ended. Please sign in again.'
    case 403:
      return 'You do not have permission to do this.'
    case 404:
      return 'Not found.'
    default:
      return `Something went wrong (error ${response.status}).`
  }
}

/** Makes sure the XSRF-TOKEN cookie exists before the first write (e.g. logging in). */
export function ensureCsrfCookie(): Promise<void> {
  return api<void>('/api/auth/csrf')
}
