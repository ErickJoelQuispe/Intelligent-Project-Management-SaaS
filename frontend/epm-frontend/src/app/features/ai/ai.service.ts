import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OAuthService } from 'angular-oauth2-oidc';
import { environment } from '../../../environments/environment';
import { ChatRequest, ChatResponse, ChatTurn, ChatTaskSummary } from './models/chat.models';

export interface TaskDraft {
  title: string;
  description: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
}

export interface GenerateTasksRequest {
  projectId: string;
  description: string;
  bypassCache: boolean;
  existingTaskTitles?: string[];
}

export interface GenerateTasksResponse {
  tasks: TaskDraft[];
  cached: boolean;
}

export interface ProjectSummaryResponse {
  summary: string;
  cached: boolean;
}

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http       = inject(HttpClient);
  private readonly oauth      = inject(OAuthService);
  private readonly baseUrl    = `${environment.apiBaseUrl}/ai`;

  generateTasks(
    projectId: string,
    description: string,
    bypassCache = false,
    existingTaskTitles: string[] = [],
  ): Observable<GenerateTasksResponse> {
    return this.http.post<GenerateTasksResponse>(`${this.baseUrl}/tasks/generate`, {
      projectId,
      description,
      bypassCache,
      existingTaskTitles,
    });
  }

  getProjectSummary(projectId: string): Observable<ProjectSummaryResponse> {
    return this.http.post<ProjectSummaryResponse>(
      `${this.baseUrl}/projects/${projectId}/summary`,
      {},
    );
  }

  /**
   * Blocking POST chat — returns a single ChatResponse.
   * Used as non-streaming fallback.
   * projectId is required by the backend (@NotBlank).
   */
  chat(message: string, projectId: string): Observable<ChatResponse> {
    const body: ChatRequest = { message, projectId };
    return this.http.post<ChatResponse>(`${this.baseUrl}/chat`, body);
  }

  /**
   * Streaming chat via fetch + ReadableStream.
   * Handles both SSE `data: <token>` format and raw text chunks.
   * AbortController teardown is wired to Observable unsubscription.
   */
  streamChat(
    message: string,
    projectId?: string,
    history?: ChatTurn[],
    existingTasks?: ChatTaskSummary[],
  ): Observable<string> {
    return new Observable<string>((subscriber) => {
      const controller = new AbortController();
      const token      = this.oauth.getAccessToken();

      fetch(`${this.baseUrl}/chat/stream`, {
        method: 'POST',
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify({
          message,
          projectId: projectId ?? '',
          history: history ?? [],
          existingTasks: existingTasks ?? [],
        }),
        signal: controller.signal,
      })
        .then(async (response) => {
          if (!response.ok || !response.body) {
            subscriber.error(new Error(`Stream request failed: ${response.status}`));
            return;
          }

          const reader  = response.body.getReader();
          const decoder = new TextDecoder();
          // Accumulate incomplete lines across TCP chunks
          let buffer = '';

          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });

            // SSE events are separated by double newlines; process only complete lines
            const lines = buffer.split('\n');
            // Keep the last (potentially incomplete) line in the buffer
            buffer = lines.pop() ?? '';

            for (const line of lines) {
              const trimmed = line.trim();
              if (!trimmed) continue;                          // blank separator line
              if (trimmed.startsWith('event:')) continue;     // SSE event name
              if (trimmed.startsWith('id:'))    continue;     // SSE event id
              if (trimmed.startsWith('retry:')) continue;     // SSE retry hint

              // Strip "data:" prefix — preserve the leading space that follows it,
              // because LLM tokenizers encode the inter-word space as the FIRST
              // character of each token (e.g. "data: Basado" → " Basado").
              // Only trim the newline/carriage-return that SSE adds at line end.
              const payload = trimmed.startsWith('data:')
                ? line.slice(line.indexOf('data:') + 5)   // use raw `line`, not `trimmed`
                : line.trim();

              if (!payload || payload === '[DONE]') continue;
              subscriber.next(payload);
            }
          }

          subscriber.complete();
        })
        .catch((err: unknown) => {
          if (err instanceof Error && err.name === 'AbortError') {
            subscriber.complete();
          } else {
            subscriber.error(err);
          }
        });

      return () => controller.abort();
    });
  }
}
