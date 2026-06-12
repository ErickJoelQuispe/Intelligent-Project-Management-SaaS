import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Team, CreateTeamRequest, AddMemberRequest } from '../../core/models/team.model';

@Injectable({ providedIn: 'root' })
export class TeamService {
  private readonly http    = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/teams`;

  getAll(): Observable<Team[]> {
    return this.http.get<Team[]>(this.baseUrl);
  }

  getById(id: string): Observable<Team> {
    return this.http.get<Team>(`${this.baseUrl}/${id}`);
  }

  create(req: CreateTeamRequest): Observable<Team> {
    return this.http.post<Team>(this.baseUrl, req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  addMember(teamId: string, req: AddMemberRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${teamId}/members`, req);
  }

  removeMember(teamId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${teamId}/members/${userId}`);
  }
}
