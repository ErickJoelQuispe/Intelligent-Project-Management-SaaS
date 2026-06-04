import { inject } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap, interval, startWith, exhaustMap } from 'rxjs';
import { tapResponse } from '@ngrx/operators';
import { Notification } from '../models/notification.model';
import { NotificationService } from '../services/notification.service';

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  loading: boolean;
  error: string | null;
}

const initialState: NotificationState = {
  notifications: [],
  unreadCount: 0,
  loading: false,
  error: null,
};

const POLL_INTERVAL_MS = 30_000;

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
          // Optimistic update
          const notifications = store.notifications().map((n) =>
            n.id === id ? { ...n, read: true } : n,
          );
          const unreadCount = notifications.filter((n) => !n.read).length;
          patchState(store, { notifications, unreadCount });

          return service.markAsRead(id).pipe(
            tapResponse({
              next: (updated) => {
                const refreshed = store.notifications().map((n) =>
                  n.id === updated.id ? updated : n,
                );
                patchState(store, { notifications: refreshed });
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

    pollNotifications: rxMethod<void>(
      pipe(
        exhaustMap(() =>
          interval(POLL_INTERVAL_MS).pipe(
            startWith(0),
            tap(() => {
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
                      error: err instanceof Error ? err.message : 'Poll failed',
                    }),
                }),
              ).subscribe();
            }),
          ),
        ),
      ),
    ),
  })),
);
