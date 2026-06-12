import { inject } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap } from 'rxjs';
import { tapResponse } from '@ngrx/operators';
import { Notification } from '../models/notification.model';
import { NotificationService } from '../services/notification.service';

interface UnreadCountResponse { count: number; }

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  loading: boolean;
  error: string | null;
  wsConnected: boolean;
}

const initialState: NotificationState = {
  notifications: [],
  unreadCount: 0,
  loading: false,
  error: null,
  wsConnected: false,
};

export const NotificationStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store, service = inject(NotificationService)) => ({
    loadNotifications(): void {
      patchState(store, { loading: true, error: null });
      service.getNotifications().pipe(
        tapResponse({
          next: (notifications) => {
            const unreadCount = notifications.filter((n) => !n.read).length;
            patchState(store, { notifications, unreadCount, loading: false });
          },
          error: (err: unknown) =>
            patchState(store, {
              loading: false,
              error: err instanceof Error ? err.message : 'Failed to load notifications',
            }),
        }),
      ).subscribe();
    },

    markAsRead: rxMethod<string>(
      pipe(
        switchMap((id) => {
          // Optimistic update — apply before backend confirms
          const notifications = store.notifications().map((n) =>
            n.id === id ? { ...n, read: true } : n,
          );
          const unreadCount = notifications.filter((n) => !n.read).length;
          patchState(store, { notifications, unreadCount });

          return service.markAsRead(id).pipe(
            tapResponse({
              next: () => {
                // Backend returns 204 No Content — optimistic update already applied, nothing to do
              },
              error: () => {
                // Rollback on failure
                const rolled = store.notifications().map((n) =>
                  n.id === id ? { ...n, read: false } : n,
                );
                const restoredCount = rolled.filter((n) => !n.read).length;
                patchState(store, { notifications: rolled, unreadCount: restoredCount });
              },
            }),
          );
        }),
      ),
    ),

    markAllAsRead: rxMethod<void>(
      pipe(
        switchMap(() => {
          // Optimistic update
          const notifications = store.notifications().map((n) => ({ ...n, read: true }));
          patchState(store, { notifications, unreadCount: 0 });

          return service.markAllAsRead().pipe(
            tapResponse({
              next: () => { /* already updated optimistically */ },
              error: () => {
                // Reload to restore correct state on failure
                service.getNotifications().subscribe((notifs) => {
                  const count = notifs.filter((n) => !n.read).length;
                  patchState(store, { notifications: notifs, unreadCount: count });
                });
              },
            }),
          );
        }),
      ),
    ),

    connectWebSocket(userId: string, token: string): void {
      // Disconnect any existing connection before reconnecting
      if (store.wsConnected()) {
        service.disconnect();
      }

      service.connect(userId, token);
      patchState(store, { wsConnected: true });

      // Sync unread count from server immediately — avoids stale 0 on app load
      service.getUnreadCount().subscribe({
        next: ({ count }) => patchState(store, { unreadCount: count }),
      });

      service.getNotificationStream(userId).subscribe({
        next: (message) => {
          const notification: Notification = JSON.parse(message.body);
          const notifications = [notification, ...store.notifications()];
          const unreadCount = notifications.filter((n) => !n.read).length;
          patchState(store, { notifications, unreadCount });
        },
      });
    },
  })),
);
