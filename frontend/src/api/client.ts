import type { ApiErrorBody } from './types'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080'

/**
 * The token lives in memory. It is mirrored into localStorage only so a page reload can restore the
 * session; see DECISIONS.md for the XSS trade-off that implies.
 */
let accessToken: string | null = null

const TOKEN_STORAGE_KEY = 'seatsure.token'

export function setAccessToken(token: string | null): void {
  accessToken = token
  try {
    if (token) {
      window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
    } else {
      window.localStorage.removeItem(TOKEN_STORAGE_KEY)
    }
  } catch {
    // Private browsing can refuse storage; the in-memory token still works for this tab.
  }
}

export function restoreAccessToken(): string | null {
  try {
    accessToken = window.localStorage.getItem(TOKEN_STORAGE_KEY)
  } catch {
    accessToken = null
  }
  return accessToken
}

export function getAccessToken(): string | null {
  return accessToken
}

/** Carries the server's status and message so pages can show the right friendly text. */
export class ApiError extends Error {
  readonly status: number
  readonly details: number[]

  constructor(status: number, message: string, details: number[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }

  /** 409: someone else got the seat. */
  get isConflict(): boolean {
    return this.status === 409
  }

  /** 410: the hold expired while the user was paying. */
  get isGone(): boolean {
    return this.status === 410
  }

  /** 429: too many requests from this user. */
  get isRateLimited(): boolean {
    return this.status === 429
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  headers?: Record<string, string>
  auth?: boolean
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, headers = {}, auth = true } = options

  const finalHeaders: Record<string, string> = { ...headers }
  if (body !== undefined) {
    finalHeaders['Content-Type'] = 'application/json'
  }
  if (auth && accessToken) {
    finalHeaders.Authorization = `Bearer ${accessToken}`
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: finalHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const parsed: unknown = text.length > 0 ? JSON.parse(text) : null

  if (!response.ok) {
    const errorBody = parsed as ApiErrorBody | null
    throw new ApiError(
      response.status,
      errorBody?.message ?? `Request failed with status ${response.status}`,
      errorBody?.details ?? [],
    )
  }

  return parsed as T
}

export async function get<T>(path: string): Promise<T> {
  return request<T>(path)
}

export async function post<T>(path: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
  return request<T>(path, { method: 'POST', body: body ?? {}, headers })
}

export async function put<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, { method: 'PUT', body })
}

export async function del(path: string): Promise<void> {
  await request<void>(path, { method: 'DELETE' })
}

export async function getPublic<T>(path: string): Promise<T> {
  return request<T>(path, { auth: false })
}

/**
 * QR images need the Authorization header, which an <img src> cannot send, so the PNG is fetched as
 * a blob and turned into an object URL.
 */
export async function getBlobUrl(path: string): Promise<string> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  })
  if (!response.ok) {
    throw new ApiError(response.status, 'Could not load the QR code')
  }
  return URL.createObjectURL(await response.blob())
}
