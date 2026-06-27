import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Team,
  CreateTeamRequest,
  AddMemberRequest,
  UpdateTeamRequest,
  UpdateMemberRoleRequest,
} from '../../core/models/team.model';

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

  update(teamId: string, req: UpdateTeamRequest): Observable<Team> {
    return this.http.patch<Team>(`${this.baseUrl}/${teamId}`, req);
  }

  updateMemberRole(teamId: string, userId: string, req: UpdateMemberRoleRequest): Observable<Team> {
    return this.http.patch<Team>(`${this.baseUrl}/${teamId}/members/${userId}`, req);
  }
}
