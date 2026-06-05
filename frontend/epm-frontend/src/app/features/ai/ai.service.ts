import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TaskDraft {
  title: string;
  description: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
}

export interface GenerateTasksRequest {
  projectId: string;
  description: string;
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
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/ai`;

  generateTasks(projectId: string, description: string): Observable<GenerateTasksResponse> {
    return this.http.post<GenerateTasksResponse>(`${this.baseUrl}/tasks/generate`, {
      projectId,
      description,
    });
  }

  getProjectSummary(projectId: string): Observable<ProjectSummaryResponse> {
    return this.http.post<ProjectSummaryResponse>(
      `${this.baseUrl}/projects/${projectId}/summary`,
      {},
    );
  }
}
