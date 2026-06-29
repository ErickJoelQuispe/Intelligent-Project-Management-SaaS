import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class InvitationService {
  private readonly http = inject(HttpClient);

  inviteMember(
    teamId: string,
    email: string,
  ): Observable<{ invitationId: string; email: string; expiresAt: string }> {
    return this.http.post<{ invitationId: string; email: string; expiresAt: string }>(
      `${environment.apiBaseUrl}/teams/${teamId}/invite`,
      { email },
    );
  }

  acceptInvitation(
    token: string,
    firstName: string,
    lastName: string,
    password: string,
  ): Observable<void> {
    return this.http.post<void>(
      `${environment.apiBaseUrl}/auth/accept-invitation`,
      { token, firstName, lastName, password },
    );
  }
}
