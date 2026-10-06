import type { Usage } from '@/services/admin.service';
export interface ChartPoint { name: string; value: number }
export interface AnalyticsOverview {
  usage: Usage;
  daily: { date: string; label: string; messages: number; playground: number; published: number }[];
  channels: ChartPoint[];
  channelStatus: ChartPoint[];
  chatbots: ChartPoint[];
  botStatus: ChartPoint[];
  sourceTypes: ChartPoint[];
  sourceStatus: ChartPoint[];
}
