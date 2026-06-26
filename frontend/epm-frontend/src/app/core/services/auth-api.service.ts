import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserSession } from '../models/user-session.model';

/**
 * Handles auth-service API calls that require a backend round-trip.
 *
 * Distinct from {@link AuthService} (which owns the local OIDC session lifecycle)
 * and focuses on authenticated REST operations against auth-service endpoints.
 */
@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  /**
   * Disables the authenticated user's Keycloak account.
   *
   * Step 1 of the frontend-orchestrated account deletion flow.
   * Returns 204 No Content on success.
   * Throws on HTTP error — caller must handle (show error and abort).
   */
  disableAccount(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/account`);
  }

  /**
   * Retrieves all active Keycloak sessions for the authenticated user.
   *
   * GET /api/v1/auth/sessions
   */
  getSessions(): Observable<UserSession[]> {
    return this.http.get<UserSession[]>(`${this.baseUrl}/sessions`);
  }

  /**
   * Revokes a specific Keycloak session.
   *
   * DELETE /api/v1/auth/sessions/{sessionId}
   * Returns 204 No Content on success.
   */
  revokeSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/sessions/${sessionId}`);
  }
}
