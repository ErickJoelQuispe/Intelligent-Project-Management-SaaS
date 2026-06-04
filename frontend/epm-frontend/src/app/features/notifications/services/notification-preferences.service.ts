import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { NotificationPreference, NotificationChannel, NotificationType } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationPreferencesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/notifications/preferences`;

  getPreferences(): Observable<NotificationPreference[]> {
    return this.http.get<NotificationPreference[]>(this.baseUrl);
  }

  updatePreference(
    eventType: NotificationType,
    channel: NotificationChannel,
    enabled: boolean,
  ): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/${eventType}/${channel}`, { enabled });
  }
}
