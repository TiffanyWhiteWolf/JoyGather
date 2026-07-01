const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'
const AUTH_KEYS = ['quju:token', 'quju:session']

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

function authHeaders(extra?: HeadersInit): HeadersInit {
  const token = localStorage.getItem('quju:token')
  return token ? { ...extra, Authorization: `Bearer ${token}` } : (extra ?? {})
}

export function clearAuthStorage() {
  AUTH_KEYS.forEach(key => localStorage.removeItem(key))
  window.dispatchEvent(new CustomEvent('quju:auth-changed'))
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${API_BASE}/auth/logout`, {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: '{}',
    })
  } finally {
    clearAuthStorage()
  }
}

async function unwrap<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let message = `请求失败：${response.status}`
    try {
      const body = await response.json() as { message?: string }
      if (body.message) message = body.message
    } catch { /* keep default */ }
    if (response.status === 401 || /请先登录|登录已过期|账号已被封禁|账号已注销/.test(message)) {
      clearAuthStorage()
      if (window.location.pathname !== '/auth') {
        window.location.href = '/auth?redirect=' + encodeURIComponent(window.location.pathname + window.location.search)
      }
    }
    throw new Error(message)
  }
  const body = await response.json() as ApiEnvelope<T>
  return body.data
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, { headers: authHeaders() })
  return unwrap<T>(response)
}

export async function apiPost<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  })
  return unwrap<T>(response)
}

export async function apiPut<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'PUT',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  })
  return unwrap<T>(response)
}

export async function apiDelete<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'DELETE',
    headers: authHeaders(body === undefined ? undefined : { 'Content-Type': 'application/json' }),
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  return unwrap<T>(response)
}

export async function apiUpload<T>(path: string, formData: FormData): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: authHeaders(),
    body: formData,
  })
  return unwrap<T>(response)
}
