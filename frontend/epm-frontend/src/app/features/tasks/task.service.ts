import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Task,
  TaskStatus,
  TaskSummary,
  KanbanResponse,
  CreateTaskRequest,
  CreateSubtaskRequest,
  UpdateTaskRequest,
  Page,
} from '../../core/models/task.models';

@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}`;

  getKanban(projectId: string): Observable<KanbanResponse> {
    return this.http.get<KanbanResponse>(
      `${this.baseUrl}/projects/${projectId}/tasks/kanban`,
    );
  }

  list(projectId: string, page: number, size: number): Observable<Page<Task>> {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));
    return this.http.get<Page<Task>>(
      `${this.baseUrl}/projects/${projectId}/tasks`,
      { params },
    );
  }

  getById(taskId: string): Observable<Task> {
    return this.http.get<Task>(`${this.baseUrl}/tasks/${taskId}`);
  }

  create(req: CreateTaskRequest): Observable<Task> {
    return this.http.post<Task>(`${this.baseUrl}/tasks`, req);
  }

  update(taskId: string, req: UpdateTaskRequest): Observable<Task> {
    return this.http.put<Task>(`${this.baseUrl}/tasks/${taskId}`, req);
  }

  changeStatus(taskId: string, status: TaskStatus): Observable<Task> {
    return this.http.patch<Task>(`${this.baseUrl}/tasks/${taskId}/status`, { status });
  }

  assign(taskId: string, assigneeId: string | null): Observable<Task> {
    return this.http.patch<Task>(`${this.baseUrl}/tasks/${taskId}/assignee`, { assigneeId });
  }

  delete(taskId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/tasks/${taskId}`);
  }

  getSubtasks(taskId: string): Observable<Task[]> {
    return this.http.get<Task[]>(`${this.baseUrl}/tasks/${taskId}/subtasks`);
  }

  createSubtask(req: CreateSubtaskRequest): Observable<Task> {
    return this.http.post<Task>(`${this.baseUrl}/tasks/subtasks`, req);
  }
}
