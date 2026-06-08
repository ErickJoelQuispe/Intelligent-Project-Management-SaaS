import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { IMessage } from '@stomp/stompjs';
import { OAuthService } from 'angular-oauth2-oidc';
import { environment } from '../../../../environments/environment';
import { Notification } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly oauthService = inject(OAuthService);
  private readonly baseUrl = `${environment.apiBaseUrl}/notifications`;

  /** Exposed for testing only — do not access in production code. */
  _rxStomp: RxStomp | null = null;

  // ─── HTTP methods ────────────────────────────────────────────────────────────

  getNotifications(): Observable<Notification[]> {
    return this.http.get<Notification[]>(this.baseUrl);
  }

  getUnreadCount(): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/unread-count`);
  }

  markAsRead(id: string): Observable<Notification> {
    return this.http.patch<Notification>(`${this.baseUrl}/${id}/read`, {});
  }

  markAllAsRead(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/mark-all-read`, {});
  }

  // ─── WebSocket methods ───────────────────────────────────────────────────────

  connect(userId: string, token: string): void {
    this._rxStomp = new RxStomp();
    // Use webSocketFactory so RxStomp fetches a fresh token on every
    // reconnect attempt — prevents 401s after token expiry and refresh.
    const config: RxStompConfig = {
      webSocketFactory: () => new WebSocket(
        `${environment.wsBaseUrl}?token=${this.oauthService.getAccessToken() ?? token}`
      ),
      reconnectDelay: 5000,
    };
    this._rxStomp.configure(config);
    this._rxStomp.activate();
  }

  disconnect(): void {
    if (this._rxStomp) {
      this._rxStomp.deactivate();
    }
  }

  getNotificationStream(userId: string): Observable<IMessage> {
    return this._rxStomp!.watch(`/topic/notifications/${userId}`);
  }

  /** @internal — for unit testing access to the RxStomp instance. */
  getRxStomp(): RxStomp | null {
    return this._rxStomp;
  }
}
