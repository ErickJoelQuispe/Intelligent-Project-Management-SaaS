export interface ChatMessage {
  role: 'user' | 'assistant' | 'error';
  content: string;
  streaming?: boolean;
}

export interface ChatRequest {
  message: string;
  projectId?: string;
}

export interface ChatResponse {
  reply: string;
}
