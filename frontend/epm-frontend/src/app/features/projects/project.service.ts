import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Project } from '../../core/models/project.model';
import { CreateProjectRequest } from '../../core/models/create-project.request';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/projects`;

  list(includeArchived = false): Observable<Project[]> {
    const params = includeArchived ? { params: { includeArchived: 'true' } } : {};
    return this.http.get<Project[]>(this.baseUrl, params);
  }

  create(req: CreateProjectRequest): Observable<Project> {
    return this.http.post<Project>(this.baseUrl, req);
  }

  getById(id: string): Observable<Project> {
    return this.http.get<Project>(`${this.baseUrl}/${id}`);
  }

  update(id: string, req: { name: string; description?: string; visibility: string }): Observable<Project> {
    return this.http.patch<Project>(`${this.baseUrl}/${id}`, req);
  }

  archive(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  restore(id: string): Observable<Project> {
    return this.http.patch<Project>(`${this.baseUrl}/${id}/restore`, {});
  }

  assignTeam(projectId: string, teamId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${projectId}/teams`, { teamId });
  }
}
