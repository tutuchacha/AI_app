import { request } from './http'

/** 客服策略由后端下发，阈值调参不用改前端 */
export interface SupportPolicy {
  minScore: number
  topK: number
  handoffEnabled: boolean
}

export function getSupportPolicy() {
  return request<SupportPolicy>('/support/policy')
}
