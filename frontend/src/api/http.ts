const TOKEN_KEY = 'xy_token'
const NAME_KEY = 'xy_name'
const NICK_KEY = 'xy_nickname'

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string | null
}

export class ApiError extends Error {
  readonly code: number
  readonly traceId: string | null

  constructor(message: string, code: number, traceId: string | null) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
  }
}

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function getNickname(): string {
  return localStorage.getItem(NICK_KEY) ?? ''
}

export function getUsername(): string {
  return localStorage.getItem(NAME_KEY) ?? ''
}

export function saveSession(token: string, username: string, nickname: string): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(NAME_KEY, username)
  localStorage.setItem(NICK_KEY, nickname)
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(NAME_KEY)
  localStorage.removeItem(NICK_KEY)
}

export const API_BASE = import.meta.env.VITE_API_BASE ?? '/api'

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  // FormData 由浏览器自己带 boundary，不能手写 Content-Type
  const isForm = typeof FormData !== 'undefined' && options.body instanceof FormData
  if (options.body && !isForm && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  } catch {
    throw new ApiError('无法连接后端，请确认服务已启动（8080）', 0, null)
  }

  let body: ApiEnvelope<T> | null = null
  try {
    body = (await response.json()) as ApiEnvelope<T>
  } catch {
    body = null
  }

  if (!response.ok || !body || body.code !== 0) {
    if (response.status === 401) clearSession()
    const message = body?.message || `请求失败（HTTP ${response.status}）`
    throw new ApiError(message, body?.code ?? response.status, body?.traceId ?? null)
  }

  return body.data
}
