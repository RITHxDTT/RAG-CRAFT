export interface BotSettings {
  model_id?: string | null;
  prompt_template_id?: string | null;
  custom_instruction?: string;
  tone?: "PROFESSIONAL" | "FRIENDLY" | "CONCISE" | "EDUCATIONAL" | "DETAILED";
  welcome_message?: string;
  fallback_message?: string;
  show_citations?: boolean;
  system_instruction: string;
  model_name: string;
  temperature: number;
  answer_length: "SHORT" | "MEDIUM" | "LONG";
  top_k: number;
}
export interface Chatbot {
  id: string;
  organization_id: string;
  ownerId: string;
  avatar?: string;
  name: string;
  description: string;
  starter_questions?: string[];
  status: "DRAFT" | "ACTIVE" | "INACTIVE" | "ERROR";
  created_at: string;
  updated_at: string;
  settings: BotSettings;
  document_count: number;
  ready_count: number;
  failed_count: number;
}
export interface ChatbotInput {
  avatar?: string;
  name: string;
  description: string;
  starter_questions?: string[];
  status?: "DRAFT" | "ACTIVE" | "INACTIVE" | "ERROR";
  settings?: BotSettings;
}
export interface Dashboard {
  total_chatbots: number;
  total_documents: number;
  ready_documents: number;
  failed_documents: number;
  recent_chatbots: Chatbot[];
}
