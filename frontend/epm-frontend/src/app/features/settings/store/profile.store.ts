import { inject } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { tapResponse } from '@ngrx/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { UserProfile, UpdateProfileRequest } from '../../../core/models/user-profile.model';
import { UserService } from '../services/user.service';

interface ProfileState {
  profile: UserProfile | null;
  loading: boolean;
  saving: boolean;
  error: string | null;
  saveSuccess: boolean;
  loaded: boolean;
}

const initialState: ProfileState = {
  profile: null,
  loading: false,
  saving: false,
  error: null,
  saveSuccess: false,
  loaded: false,
};

export const ProfileStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store, service = inject(UserService)) => ({
    loadProfile(): void {
      if (store.loaded()) return;
      patchState(store, { loading: true, error: null });
      service.getMe().pipe(
        tapResponse({
          next: (profile) => {
            patchState(store, { profile, loading: false, loaded: true });
          },
          error: (err: unknown) => {
            patchState(store, {
              loading: false,
              error: err instanceof Error ? err.message : 'Failed to load profile',
            });
          },
        }),
      ).subscribe();
    },

    saveProfile(req: UpdateProfileRequest): void {
      patchState(store, { saving: true, error: null, saveSuccess: false });
      service.updateMe(req).pipe(
        tapResponse({
          next: (profile) => {
            patchState(store, { profile, saving: false, saveSuccess: true });
          },
          error: (err: unknown) => {
            const isConflict =
              err instanceof HttpErrorResponse && err.status === 409;
            patchState(store, {
              saving: false,
              saveSuccess: false,
              error: isConflict
                ? 'Profile was updated elsewhere — please reload'
                : err instanceof Error
                  ? err.message
                  : 'Failed to save profile',
            });
          },
        }),
      ).subscribe();
    },
  })),
);
