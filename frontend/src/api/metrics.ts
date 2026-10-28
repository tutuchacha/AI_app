import { request } from './http'

export interface UsageTotals {
  calls: number
  tokensIn: number
  tokensOut: number
  p95Ms: number
  errorRate: number
}

export interface UsageDaily {
  date: string
  label: string
  calls: number
}

export interface UsageModule {
  module: string
  label: string
  calls: number
  share: number
}

export interface UsageView {
  source: string
  prometheusUp: boolean
  activeStreams: number
  generatedAt: string
  today: UsageTotals
  mine: UsageTotals
  daily: UsageDaily[]
  modules: UsageModule[]
}

export function getUsage(days = 7): Promise<UsageView> {
  return request<UsageView>(`/metrics/usage?days=${days}`)
}
