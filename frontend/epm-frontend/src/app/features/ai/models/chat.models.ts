export interface ChatMessage {
  role: 'user' | 'assistant' | 'error';
  content: string;
  streaming?: boolean;
}

export interface ChatTurn {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatTaskSummary {
  title: string;
  status: string;
}

export interface ChatRequest {
  message: string;
  projectId: string;
  history?: ChatTurn[];
  existingTasks?: ChatTaskSummary[];
}

export interface ChatResponse {
  reply: string;
}
