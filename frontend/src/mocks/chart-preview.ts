import type { AnalyticsOverview } from '@/types/analytics';
// Presentation-only fixtures. Never persisted or mixed into local account metrics.
export function chartPreview(days: number, locale?: string): AnalyticsOverview {
  const daily = Array.from({ length: days }, (_, i) => {
    const date = new Date(); date.setDate(date.getDate() - days + i + 1);
    const playground = [24, 36, 30, 42, 28, 58, 44][i % 7];
    const published = [60, 84, 72, 108, 96, 120, 102][i % 7] + Math.floor(i / 7) * 12;
  return { date: date.toISOString(), label: date.toLocaleDateString(locale, { month: 'short', day: 'numeric' }), messages: playground + published, playground, published };
  });
    const total = daily.reduce((sum, day) => sum + day.messages, 0);
  const portions = (names: string[], weights: number[]) => { let assigned = 0; return names.map((name,index) => { const value = index === names.length - 1 ? total - assigned : Math.floor(total * weights[index]); assigned += value; return { name, value }; }); };
  return {
    usage: { total_chatbots: 6, active_chatbots: 4, total_documents: 24, total_knowledge: 28, total_conversations: 184, total_messages: daily.reduce((sum,day)=>sum+day.messages,0), messages_this_week: daily.slice(-7).reduce((sum,day)=>sum+day.messages,0), total_channels: 12, active_channels: 8, total_users: 12, active_users: 10, messages_by_channel: {} },
    daily,
    channelStatus: [{name:"Live",value:8},{name:"Off",value:4}],
    channels: portions(['PUBLIC LINK','PLAYGROUND','WEB WIDGET','TELEGRAM'],[.4,.2,.3,.1]),
    chatbots: portions(['Company Assistant','Customer Support','Product Expert','HR Assistant','Sales Companion','Onboarding Guide'],[.4,.25,.18,.1,.05,.02]),
    botStatus: [{name:'ACTIVE',value:4},{name:'DRAFT',value:1},{name:'INACTIVE',value:1}],
    sourceTypes: [{name:'PDF',value:12},{name:'DOCX',value:6},{name:'MARKDOWN',value:4},{name:'XLSX',value:2},{name:'WEBSITE',value:4}],
    sourceStatus: [{name:'READY',value:23},{name:'PROCESSING',value:3},{name:'FAILED',value:2}],
  };
}
