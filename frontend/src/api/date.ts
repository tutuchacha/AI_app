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

export interface AdviceAction {
  title: string
  detail: string
  timing: string
}

export interface AdviceScript {
  scene: string
  text: string
}

export interface DateSpot {
  name: string
  address: string
  category: string
  location: string
  rating: string
  distance: string
}

export interface DateAdvice {
  id: number
  targetId: number
  stage: DateStage
  stageLabel: string
  city: string
  goal: string
  actions: AdviceAction[]
  scripts: AdviceScript[]
  avoid: string[]
  success: string
  spots: DateSpot[]
  model: string
  promptTokens: number
  completionTokens: number
  latencyMs: number
  createdAt: string
}

/** 生成建议：模型 + 高德地点，一次调用可能 5~15s */
export function createDateAdvice(targetId: number) {
  return request<DateAdvice>('/date/advice', {
    method: 'POST',
    body: JSON.stringify({ targetId }),
  })
}

/** 同一对象最近 5 条建议，倒序 */
export function listDateAdvice(targetId: number) {
  return request<DateAdvice[]>(`/date/advice?targetId=${targetId}`)
}

export interface DateAskInput {
  question: string
  area?: string
  targetId?: number
}

export interface DateAskResult {
  reply: string
  area: string
  city: string
  keywords: string[]
  spots: DateSpot[]
  model: string
  promptTokens: number
  completionTokens: number
  latencyMs: number
}

/** 约会问答：模型建议 + 按城市/区域搜高德，可能 5~15s */
export function askDateAssistant(input: DateAskInput) {
  return request<DateAskResult>('/date/ask', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}
