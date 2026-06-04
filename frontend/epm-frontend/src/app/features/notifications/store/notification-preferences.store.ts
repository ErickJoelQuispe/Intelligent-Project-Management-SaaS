import { inject } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { tapResponse } from '@ngrx/operators';
import { NotificationPreference, NotificationChannel, NotificationType } from '../models/notification.model';
import { NotificationPreferencesService } from '../services/notification-preferences.service';

interface NotificationPreferencesState {
  preferences: NotificationPreference[];
  loading: boolean;
  error: string | null;
}

const initialState: NotificationPreferencesState = {
  preferences: [],
  loading: false,
  error: null,
};

export const NotificationPreferencesStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store, service = inject(NotificationPreferencesService)) => ({
    loadPreferences(): void {
      patchState(store, { loading: true, error: null });
      service.getPreferences().pipe(
        tapResponse({
          next: (preferences) => {
            patchState(store, { preferences, loading: false });
          },
          error: (err: unknown) => {
            patchState(store, {
              loading: false,
              error: err instanceof Error ? err.message : 'Failed to load preferences',
            });
          },
        }),
      ).subscribe();
    },

    updatePreference(
      eventType: NotificationType,
      channel: NotificationChannel,
      enabled: boolean,
    ): void {
      // Snapshot for rollback
      const previous = store.preferences();

      // Optimistic update
      const updated = previous.map((p) =>
        p.eventType === eventType && p.channel === channel ? { ...p, enabled } : p,
      );
      patchState(store, { preferences: updated });

      service.updatePreference(eventType, channel, enabled).pipe(
        tapResponse({
          next: () => { /* optimistic update already applied */ },
          error: () => {
            // Rollback to previous state
            patchState(store, { preferences: previous });
          },
        }),
      ).subscribe();
    },
  })),
);
