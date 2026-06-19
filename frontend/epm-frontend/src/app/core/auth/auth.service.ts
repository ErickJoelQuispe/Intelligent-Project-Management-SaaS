import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { OAuthService } from 'angular-oauth2-oidc';
import { catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Handles authentication operations that require a backend call.
 *
 * logout() calls POST /api/v1/auth/logout first so the backend can
 * invalidate the Keycloak session and record the security event
 * (IP address, user-agent). Then it clears the local OIDC state
 * regardless of the backend response — a server error must never
 * leave the user stuck in a logged-in state on the client.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http  = inject(HttpClient);
  private readonly oauth = inject(OAuthService);
  private readonly logoutUrl = `${environment.apiBaseUrl}/auth/logout`;

  logout(): void {
    this.http
      .post(this.logoutUrl, null, { responseType: 'text' })
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.oauth.logOut());
  }
}
