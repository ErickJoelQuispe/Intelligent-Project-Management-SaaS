import { inject } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap } from 'rxjs';
import { tapResponse } from '@ngrx/operators';
import { OAuthService } from 'angular-oauth2-oidc';
import { environment } from '../../../../environments/environment';
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

/** SSE heartbeat timeout — if no event arrives within this window, fall back to poll. */
const SSE_HEARTBEAT_MS = 60_000;
/** Fallback polling interval when SSE is disconnected. */
const FALLBACK_POLL_MS = 60_000;

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
  })),
  withMethods((store, service = inject(NotificationService), oauthService = inject(OAuthService)) => {
    let abortController: AbortController | null = null;
    let heartbeatTimer: ReturnType<typeof setTimeout> | null = null;
    let fallbackTimer: ReturnType<typeof setInterval> | null = null;

    /** Reloads notifications — shared by SSE event handler and fallback poll. */
    function reload() {
      service.getNotifications().subscribe({
        next: (notifications) => {
          const unreadCount = notifications.filter((n) => !n.read).length;
          patchState(store, { notifications, unreadCount, loading: false });
        },
        error: (err: unknown) =>
          patchState(store, {
            error: err instanceof Error ? err.message : 'Reload failed',
          }),
      });
    }

    /** Resets the heartbeat timer that detects a dead SSE connection. */
    function resetHeartbeat() {
      if (heartbeatTimer) clearTimeout(heartbeatTimer);
      heartbeatTimer = setTimeout(() => {
        console.warn('[NotificationStore] SSE heartbeat expired — reconnecting');
        ctx.disconnectSse();
        ctx.startFallbackPoll();
      }, SSE_HEARTBEAT_MS);
    }

    /** Simple SSE frame parser. The wire format is:
     *    event: <type>\n
     *    data: <json>\n
     *    \n
     */
    function onSseChunk(chunk: string) {
      // Look for event type in the frame
      if (chunk.includes('event: notification') || chunk.includes('"notification"')) {
        reload();
      }
      // Any data resets the heartbeat
      resetHeartbeat();
    }

    const ctx = {
      /** Opens an SSE connection using fetch (needed for the Authorization header). */
      connectSse(): void {
        // Clean any previous connection
        ctx.disconnectSse();

        const token = oauthService.getAccessToken();
        if (!token) {
          ctx.startFallbackPoll();
          return;
        }

        // Initial load so the UI is not empty while SSE connects
        patchState(store, { loading: true, error: null });
        reload();

        abortController = new AbortController();
        resetHeartbeat();

        fetch(`${environment.apiBaseUrl}/notifications/stream`, {
          headers: { Authorization: `Bearer ${token}` },
          signal: abortController.signal,
        })
          .then((response) => {
            if (!response.body) {
              ctx.startFallbackPoll();
              return;
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            function read() {
              reader
                .read()
                .then(({ done, value }) => {
                  if (done) {
                    // Stream ended — fall back to polling
                    ctx.startFallbackPoll();
                    return;
                  }

                  buffer += decoder.decode(value, { stream: true });

                  // SSE frames are separated by \n\n
                  const frames = buffer.split('\n\n');
                  // Keep the last (potentially incomplete) fragment in the buffer
                  buffer = frames.pop() || '';

                  for (const frame of frames) {
                    onSseChunk(frame);
                  }

                  read();
                })
                .catch(() => {
                  // Aborted or network error — silent
                });
            }

            read();
          })
          .catch(() => {
            ctx.startFallbackPoll();
          });
      },

      /** Closes the SSE connection and cancels all timers. */
      disconnectSse(): void {
        if (abortController) {
          abortController.abort();
          abortController = null;
        }
        if (heartbeatTimer) {
          clearTimeout(heartbeatTimer);
          heartbeatTimer = null;
        }
        if (fallbackTimer) {
          clearInterval(fallbackTimer);
          fallbackTimer = null;
        }
      },

      /** Starts a fallback polling interval (used when SSE is unavailable). */
      startFallbackPoll(): void {
        if (fallbackTimer) return;
        fallbackTimer = setInterval(() => reload(), FALLBACK_POLL_MS);
      },
    };

    return ctx;
  }),
);
