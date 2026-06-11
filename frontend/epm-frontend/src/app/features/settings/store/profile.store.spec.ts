import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ProfileStore } from './profile.store';
import { UserService } from '../services/user.service';
import { UserProfile, UpdateProfileRequest } from '../../../core/models/user-profile.model';

const mockProfile: UserProfile = {
  id: '11111111-1111-1111-1111-111111111111',
  tenantId: '22222222-2222-2222-2222-222222222222',
  email: 'jane.doe@example.com',
  firstName: 'Jane',
  lastName: 'Doe',
  bio: 'Software engineer',
  avatarUrl: null,
  version: 1,
};

describe('ProfileStore', () => {
  let store: InstanceType<typeof ProfileStore>;
  let serviceMock: {
    getMe: ReturnType<typeof vi.fn>;
    updateMe: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    serviceMock = {
      getMe: vi.fn(),
      updateMe: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ProfileStore,
        { provide: UserService, useValue: serviceMock },
      ],
    });

    store = TestBed.inject(ProfileStore);
  });

  it('should initialize with null profile, loading false, saving false', () => {
    expect(store.profile()).toBeNull();
    expect(store.loading()).toBe(false);
    expect(store.saving()).toBe(false);
    expect(store.error()).toBeNull();
    expect(store.saveSuccess()).toBe(false);
  });

  describe('loadProfile()', () => {
    it('should set loading=true during fetch and populate profile on success', () => {
      serviceMock.getMe.mockReturnValue(of(mockProfile));

      TestBed.runInInjectionContext(() => {
        store.loadProfile();
      });

      expect(serviceMock.getMe).toHaveBeenCalledOnce();
      expect(store.profile()).toEqual(mockProfile);
      expect(store.loading()).toBe(false);
      expect(store.error()).toBeNull();
    });

    it('should set error on failure and loading to false', () => {
      serviceMock.getMe.mockReturnValue(
        throwError(() => new Error('Network error')),
      );

      TestBed.runInInjectionContext(() => {
        store.loadProfile();
      });

      expect(store.error()).toBe('Network error');
      expect(store.loading()).toBe(false);
      expect(store.profile()).toBeNull();
    });
  });

  describe('saveProfile()', () => {
    it('should update profile and set saveSuccess=true on success', () => {
      const updatedProfile: UserProfile = { ...mockProfile, firstName: 'Janet', version: 2 };
      serviceMock.getMe.mockReturnValue(of(mockProfile));
      serviceMock.updateMe.mockReturnValue(of(updatedProfile));

      TestBed.runInInjectionContext(() => {
        store.loadProfile();

        const req: UpdateProfileRequest = { firstName: 'Janet', version: 1 };
        store.saveProfile(req);
      });

      expect(store.profile()).toEqual(updatedProfile);
      expect(store.saveSuccess()).toBe(true);
      expect(store.saving()).toBe(false);
      expect(store.error()).toBeNull();
    });

    it('should set error message on generic HTTP error', () => {
      serviceMock.getMe.mockReturnValue(of(mockProfile));
      serviceMock.updateMe.mockReturnValue(
        throwError(() => new Error('Save failed')),
      );

      TestBed.runInInjectionContext(() => {
        store.loadProfile();
        store.saveProfile({ version: 1 });
      });

      expect(store.error()).toBe('Save failed');
      expect(store.saveSuccess()).toBe(false);
      expect(store.saving()).toBe(false);
    });

    it('should set conflict message on 409 HttpErrorResponse', () => {
      serviceMock.getMe.mockReturnValue(of(mockProfile));
      serviceMock.updateMe.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({ status: 409, statusText: 'Conflict', url: '/users/me' }),
        ),
      );

      TestBed.runInInjectionContext(() => {
        store.loadProfile();
        store.saveProfile({ version: 1 });
      });

      expect(store.error()).toBe('Profile was updated elsewhere — please reload');
      expect(store.saveSuccess()).toBe(false);
      expect(store.saving()).toBe(false);
    });
  });
});
