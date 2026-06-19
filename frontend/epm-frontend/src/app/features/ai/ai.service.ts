import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OAuthService } from 'angular-oauth2-oidc';
import { environment } from '../../../environments/environment';
import { ChatRequest, ChatResponse } from './models/chat.models';

export interface TaskDraft {
  title: string;
  description: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
}

export interface GenerateTasksRequest {
  projectId: string;
  description: string;
  bypassCache: boolean;
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
  ): Observable<GenerateTasksResponse> {
    return this.http.post<GenerateTasksResponse>(`${this.baseUrl}/tasks/generate`, {
      projectId,
      description,
      bypassCache,
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
  streamChat(message: string, projectId?: string): Observable<string> {
    return new Observable<string>((subscriber) => {
      const controller = new AbortController();
      const token      = this.oauth.getAccessToken();

      const url = `${this.baseUrl}/chat/stream`;

      fetch(url, {
        method: 'POST',
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify({ message, projectId: projectId ?? '' }),
        signal: controller.signal,
      })
        .then(async (response) => {
          if (!response.ok || !response.body) {
            subscriber.error(new Error(`Stream request failed: ${response.status}`));
            return;
          }

          const reader  = response.body.getReader();
          const decoder = new TextDecoder();

          // eslint-disable-next-line no-constant-condition
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            const chunk = decoder.decode(value, { stream: true });
            const lines = chunk.split('\n');

            for (const line of lines) {
              const trimmed = line.trim();

              // Skip empty lines, SSE event/id/retry metadata, and [DONE] sentinel
              if (
                !trimmed ||
                trimmed === 'data: [DONE]' ||
                trimmed === '[DONE]' ||
                trimmed.startsWith('event:') ||
                trimmed.startsWith('id:') ||
                trimmed.startsWith('retry:')
              ) {
                continue;
              }

              // Strip `data: ` prefix (SSE format)
              if (trimmed.startsWith('data: ')) {
                const payload = trimmed.slice(6);
                if (payload && payload !== '[DONE]') {
                  subscriber.next(payload);
                }
              } else {
                // Raw text chunk — emit as-is
                subscriber.next(trimmed);
              }
            }
          }

          subscriber.complete();
        })
        .catch((err: unknown) => {
          // AbortError is expected on unsubscribe — don't propagate as error
          if (err instanceof Error && err.name === 'AbortError') {
            subscriber.complete();
          } else {
            subscriber.error(err);
          }
        });

      // Teardown: abort the fetch when the Observable is unsubscribed
      return () => {
        controller.abort();
      };
    });
  }
}
