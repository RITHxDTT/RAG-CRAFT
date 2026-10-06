export interface KnowledgeDocument {
  id: string;
  ownerId: string;
  chatbot_id: string;
  chunkCount: number;
  processingStartedAt?: string;
  url?: string;
  name: string;
  file_type: string;
  mime_type: string;
  size_bytes: number;
  status: "QUEUED" | "UPLOADING" | "PROCESSING" | "CRAWLING" | "EXTRACTING" | "CHUNKING" | "INDEXING" | "READY" | "FAILED" | "REVIEW";
  error_message: string | null;
  created_at: string;
  updated_at: string;
}
export interface DocumentDetail extends KnowledgeDocument {
  jobs: {
    id: string;
    status: string;
    error_message: string | null;
    created_at: string;
    updated_at: string;
  }[];
}
export interface UploadPolicy {
  max_upload_size_mb: number;
  extensions: string[];
  processing_enabled: boolean;
  worker_running: boolean;
}
export interface DocumentActivity extends KnowledgeDocument {
  chatbot_id: string;
  chatbot_name: string;
}
