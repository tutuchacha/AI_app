import { request } from './http'

export interface LoginResult {
  token: string
  username: string
  nickname: string
}

export interface UserProfile {
  id: number
  username: string
  nickname: string
  role: string
}

export function loginApi(username: string, password: string): Promise<LoginResult> {
  return request<LoginResult>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function registerApi(username: string, password: string, nickname?: string): Promise<UserProfile> {
  return request<UserProfile>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password, nickname }),
  })
}

export function meApi(): Promise<UserProfile> {
  return request<UserProfile>('/auth/me')
}
