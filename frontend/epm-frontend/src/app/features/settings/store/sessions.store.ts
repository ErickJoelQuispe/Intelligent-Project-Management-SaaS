import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { OAuthService } from 'angular-oauth2-oidc';
import { MatDialog } from '@angular/material/dialog';
import { AuthApiService } from '../../../core/services/auth-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { UserSession } from '../../../core/models/user-session.model';
import { ConfirmDeleteAccountDialogComponent } from '../components/confirm-delete-account-dialog/confirm-delete-account-dialog.component';

/**
 * Store managing the list of active Keycloak sessions for the current user.
 *
 * Uses Angular signals for reactive state. Dialog confirmation is shown
 * before revoking the current session to warn the user they will be logged out.
 */
@Injectable({ providedIn: 'root' })
export class SessionsStore {
  private readonly authApi   = inject(AuthApiService);
  private readonly authSvc   = inject(AuthService);
  private readonly oauth     = inject(OAuthService);
  private readonly dialog    = inject(MatDialog);

  readonly sessions          = signal<UserSession[]>([]);
  readonly isLoading         = signal(false);
  readonly revokingSessionId = signal<string | null>(null);
  readonly error             = signal<string | null>(null);

  /**
   * Loads all active sessions from the backend and updates the sessions signal.
   */
  async loadSessions(): Promise<void> {
    this.isLoading.set(true);
    this.error.set(null);
    try {
      const data = await firstValueFrom(this.authApi.getSessions());
      this.sessions.set(data);
    } catch {
      this.error.set('Failed to load sessions');
    } finally {
      this.isLoading.set(false);
    }
  }

  /**
   * Revokes the given session.
   *
   * If the session is the current session (isCurrent=true), a confirmation dialog is
   * shown first because revoking it will log the user out. On success with isCurrent=true,
   * the auth service logout is called.
   *
   * The session is optimistically removed from the list immediately. On error,
   * the original list is restored and an error message is set.
   *
   * @param sessionId  the Keycloak session ID to revoke
   * @param isCurrent  whether this is the currently active session
   */
  async revokeSession(sessionId: string, isCurrent = false): Promise<void> {
    if (isCurrent) {
      const dialogRef = this.dialog.open(ConfirmDeleteAccountDialogComponent);
      const confirmed = await firstValueFrom(dialogRef.afterClosed());
      if (!confirmed) return;
    }

    // Optimistic update — remove from list immediately
    const snapshot = this.sessions();
    this.sessions.update(list => list.filter(s => s.sessionId !== sessionId));
    this.revokingSessionId.set(sessionId);
    this.error.set(null);

    try {
      await firstValueFrom(this.authApi.revokeSession(sessionId));
      if (isCurrent) {
        this.authSvc.logout();
      }
    } catch (err: unknown) {
      // Restore original list on failure
      this.sessions.set(snapshot);
      const message = err instanceof Error ? err.message : 'Failed to revoke session';
      // Reload to re-sync with backend (silently — don't overwrite error on failure)
      try {
        const data = await firstValueFrom(this.authApi.getSessions());
        this.sessions.set(data);
      } catch {
        // Silently ignore reload failure — keep the snapshot we already restored
      }
      // Set error AFTER reload so it is the final state
      this.error.set(message);
    } finally {
      this.revokingSessionId.set(null);
    }
  }

  /**
   * Returns the current session ID from the JWT identity claims ({@code sid} claim),
   * or {@code null} if the claim is not present.
   */
  currentSessionId(): string | null {
    const claims = this.oauth.getIdentityClaims() as Record<string, string> | null;
    return claims?.['sid'] ?? null;
  }
}
