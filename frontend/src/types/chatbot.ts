/** DRAFT -> PENDING (first READY document) -> ACTIVE (publish) <-> PAUSED; DISABLED is set by an admin only. */
export type ChatbotStatus = "DRAFT" | "PENDING" | "ACTIVE" | "PAUSED" | "DISABLED";
export type Tone = "PROFESSIONAL" | "FRIENDLY" | "CASUAL" | "FORMAL" | "CUSTOM";
export type AnswerLength = "CONCISE" | "DETAILED";
export type BotLanguage = "AUTO" | "EN" | "KM" | "KO";
export type SearchMode = "SEMANTIC" | "KEYWORD" | "HYBRID";
export interface BotSettings {
  model_id?: string | null;
  prompt_template_id?: string | null;
  custom_instruction?: string;
  tone?: Tone;
  welcome_message?: string;
  fallback_message?: string;
  show_citations?: boolean;
  system_instruction: string;
  model_name: string;
  temperature: number;
  answer_length: AnswerLength;
  top_k: number;
  /** Used automatically when the primary provider fails. */
  fallback_model_name?: string | null;
  /** Chosen at creation; cannot be changed afterwards. */
  embedding_model?: string;
  language?: BotLanguage;
  formatting?: "RICH" | "PLAIN";
  max_tokens?: number;
  search_mode?: SearchMode;
  max_context_tokens?: number;
  chunk_size?: number;
  chunk_overlap?: number;
  /** Answer only from documents; otherwise reply with the not-found message. */
  answer_from_documents_only?: boolean;
}
export interface Chatbot {
  id: string;
  organization_id: string;
  ownerId: string;
  avatar?: string;
  name: string;
  description: string;
  starter_questions?: string[];
  status: ChatbotStatus;
  /** Reason given by the admin who disabled the bot; shown to the owner. */
  disabled_reason?: string | null;
  disabled_at?: string | null;
  created_at: string;
  updated_at: string;
  settings: BotSettings;
  document_count: number;
  ready_count: number;
  failed_count: number;
  /** Derived for the list view. */
  channel_count?: number;
  channels?: string[];
  message_count?: number;
  kb_status?: "EMPTY" | "PROCESSING" | "READY" | "FAILED";
}
export interface ChatbotInput {
  avatar?: string;
  name: string;
  description: string;
  starter_questions?: string[];
  /** Applied through the lifecycle rules, never written directly. */
  status?: ChatbotStatus;
  settings?: Partial<BotSettings>;
}
export interface Dashboard {
  total_chatbots: number;
  total_documents: number;
  ready_documents: number;
  failed_documents: number;
  recent_chatbots: Chatbot[];
}
export interface PublishChecklist {
  hasDocument: boolean;
  modelSelected: boolean;
  channelReady: boolean;
  ready: boolean;
}
