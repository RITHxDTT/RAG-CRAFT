export interface Source {
  chunk_id: string | null;
  document_id: string | null;
  document_name: string;
  sheet_name?: string | null;
  row_number?: number | null;
  page_number: number | null;
  chunk_index: number | null;
  excerpt: string;
  score: number;
  url?: string;
}
export interface Answer {
  answer: string;
  sources: Source[];
  conversation_id: string;
  user_message_id: string;
  message_id: string;
}
export interface ChatMessage {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  sources: Source[];
}
export interface Conversation {
  ownerId: string;
  id: string;
  chatbot_id: string;
  channel: "PLAYGROUND" | "TELEGRAM" | "WEB_WIDGET" | "PUBLIC_LINK";
  title: string;
  created_at: string;
  updated_at: string;
}
export interface ConversationDetail extends Conversation {
  messages: ChatMessage[];
}
