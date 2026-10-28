import { request } from './http'

export type DateStage = 'MET' | 'CHATTING' | 'INVITED' | 'DATING' | 'TOGETHER' | 'PAUSED'

export interface DateProfile {
  id: number
  nickname: string
  gender: string
  age: number | null
  city: string
  occupation: string
  hobbies: string
  personality: string
  goal: string
  notes: string
  updatedAt: string
}

export type DateProfileInput = Omit<DateProfile, 'id' | 'updatedAt'>

export interface DateTarget {
  id: number
  name: string
  channel: string
  stage: DateStage
  stageLabel: string
  age: number | null
  city: string
  interests: string
  taboos: string
  notes: string
  lastContactAt: string
  createdAt: string
  updatedAt: string
}

export interface DateTargetInput {
  name: string
  channel?: string
  stage?: DateStage
  age?: number | null
  city?: string
  interests?: string
  taboos?: string
  notes?: string
  lastContactAt?: string
}

export const stageOptions: { value: DateStage; label: string }[] = [
  { value: 'MET', label: '刚认识' },
  { value: 'CHATTING', label: '聊天中' },
  { value: 'INVITED', label: '已邀约' },
  { value: 'DATING', label: '约会中' },
  { value: 'TOGETHER', label: '已确定关系' },
  { value: 'PAUSED', label: '暂停' },
]

/** 未建档时后端返回 null */
export function getDateProfile() {
  return request<DateProfile | null>('/date/profile')
}

export function saveDateProfile(input: DateProfileInput) {
  return request<DateProfile>('/date/profile', { method: 'PUT', body: JSON.stringify(input) })
}

export function listDateTargets() {
  return request<DateTarget[]>('/date/targets')
}

export function createDateTarget(input: DateTargetInput) {
  return request<DateTarget>('/date/targets', { method: 'POST', body: JSON.stringify(input) })
}

export function updateDateTarget(id: number, input: DateTargetInput) {
  return request<DateTarget>(`/date/targets/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function deleteDateTarget(id: number) {
  return request<void>(`/date/targets/${id}`, { method: 'DELETE' })
}
